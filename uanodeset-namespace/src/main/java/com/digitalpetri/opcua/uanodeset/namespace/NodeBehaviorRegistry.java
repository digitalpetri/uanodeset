package com.digitalpetri.opcua.uanodeset.namespace;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Collects callbacks that attach runtime behavior to nodes loaded from a NodeSet.
 *
 * <p>A {@link NodeSetAddressSpace} creates and populates the registry before loading nodes, then
 * invokes its callbacks synchronously after all selected nodes and references are installed,
 * dynamic datatype setup is complete, and Variable value decoding has been attempted. Subclasses
 * normally register behavior by overriding {@link
 * NodeSetAddressSpace#registerNodeBehaviors(NodeBehaviorRegistry)}:
 *
 * <pre>{@code
 * @Override
 * protected void registerNodeBehaviors(NodeBehaviorRegistry registry) {
 *   NodeId pumpTypeId = new NodeId(getNamespaceIndex(), 1001);
 *   NodeId primaryPumpId = new NodeId(getNamespaceIndex(), 5001);
 *
 *   registry.onType(pumpTypeId, TypeMatch.INCLUDE_SUBTYPES, this::attachPumpBehavior);
 *   registry.onNode(primaryPumpId, this::attachPrimaryPumpBehavior);
 *   registry.onNode(this::observeLoadedNode);
 * }
 *
 * private void attachPumpBehavior(LoadedNode loadedNode, NodeMatch match) {
 *   if (loadedNode.node() instanceof UaObjectNode pump
 *       && match instanceof NodeMatch.Type typeMatch) {
 *     // Attach behavior common to direct and derived pump instances.
 *     if (typeMatch.relationship() == TypeRelationship.SUBTYPE) {
 *       // Adapt the behavior when the direct type is derived from pumpTypeId.
 *     }
 *   }
 * }
 *
 * private void attachPrimaryPumpBehavior(LoadedNode loadedNode, NodeMatch match) {
 *   if (match instanceof NodeMatch.SpecificNode specificNode) {
 *     // specificNode.registeredNodeId() is the primaryPumpId used during registration.
 *   }
 * }
 *
 * private void observeLoadedNode(LoadedNode loadedNode, NodeMatch match) {
 *   if (match instanceof NodeMatch.AnyNode) {
 *     // Observe or initialize every loaded node, regardless of its type.
 *   }
 * }
 * }</pre>
 *
 * <p>By default, type callbacks apply to Object and Variable instances whose direct type definition
 * is the registered type or one of its subtypes. Pass {@link TypeMatch#EXACT} to the three-argument
 * {@link #onType(NodeId, TypeMatch, NodeLoadedCallback)} overload when behavior applies only to
 * instances of the registered direct type. Type definition IDs must use the server's namespace
 * indexes; using {@code getNamespaceIndex()} as shown above avoids confusing merged-model namespace
 * indexes with server indexes. The loader resolves direct types and ancestry from the merged {@link
 * com.digitalpetri.opcua.uanodeset.NodeSet} model, then reindexes those results for registry
 * dispatch; Milo's runtime server type trees are not used for matching.
 *
 * <p>Each callback receives a {@link NodeMatch} that identifies why it ran. {@link NodeMatch.Type}
 * reports both the configured {@link TypeMatch} and the actual {@link TypeRelationship}; {@link
 * NodeMatch.SpecificNode} and {@link NodeMatch.AnyNode} identify the specific-node and catch-all
 * registration forms. For a single node, matching type callbacks run from the most-derived
 * registered type to the least-derived registered type, followed by callbacks for its specific
 * NodeId, then catch-all node callbacks. Registrations within each step run in registration order.
 * An exception from any callback aborts the load and propagates to the startup caller.
 *
 * <p>All registrations must be added before {@link NodeSetNodeLoader#loadNodes()} begins. Loading
 * freezes the registry so every node observes the same callback set; subsequent registration
 * attempts throw {@link IllegalStateException}.
 */
public final class NodeBehaviorRegistry {

  private final List<TypeRegistration> typeRegistrations = new ArrayList<>();
  private final List<NodeRegistration> nodeRegistrations = new ArrayList<>();
  private final List<NodeLoadedCallback> anyNodeCallbacks = new ArrayList<>();
  private boolean frozen;

  /**
   * Register behavior for Object and Variable instances of a type or any subtype.
   *
   * <p>This is equivalent to calling {@link #onType(NodeId, TypeMatch, NodeLoadedCallback)} with
   * {@link TypeMatch#INCLUDE_SUBTYPES}.
   *
   * <p>The callback runs once for each matching instance during the post-load phase. If the parsed
   * model does not contain the instance type's supertype chain, matching falls back to exact type
   * definition equality. The type definition ID must use the server's namespace indexes. Each
   * invocation receives a {@link NodeMatch.Type} describing the registered type, configured mode,
   * and actual relationship.
   *
   * @param typeDefinitionId the type definition to match.
   * @param callback the callback to invoke for each matching post-load instance.
   * @throws IllegalStateException if loading has begun.
   */
  public synchronized void onType(NodeId typeDefinitionId, NodeLoadedCallback callback) {
    onType(typeDefinitionId, TypeMatch.INCLUDE_SUBTYPES, callback);
  }

  /**
   * Register behavior for Object and Variable instances using an explicit type match mode.
   *
   * <p>{@link TypeMatch#EXACT} matches only the instance's direct type definition. {@link
   * TypeMatch#INCLUDE_SUBTYPES} also matches instances whose direct type descends from the
   * registered type. If the parsed model does not contain an instance type's supertype chain,
   * subtype matching falls back to exact type definition equality.
   *
   * @param typeDefinitionId the type definition to match.
   * @param typeMatch the rule used to compare the registered and instance type definitions.
   * @param callback the callback to invoke for each matching post-load instance.
   * @throws IllegalStateException if loading has begun.
   */
  public synchronized void onType(
      NodeId typeDefinitionId, TypeMatch typeMatch, NodeLoadedCallback callback) {
    ensureMutable();
    typeRegistrations.add(new TypeRegistration(typeDefinitionId, typeMatch, callback));
  }

  /**
   * Register behavior for one node identified by its reindexed NodeId.
   *
   * <p>The callback runs only if the selected NodeSet content creates that node. The NodeId must
   * use the server's namespace indexes. Its callback receives a {@link NodeMatch.SpecificNode}
   * containing the registered NodeId.
   *
   * @param nodeId the server-space NodeId of the node to match.
   * @param callback the callback to invoke for the matching post-load node.
   * @throws IllegalStateException if loading has begun.
   */
  public synchronized void onNode(NodeId nodeId, NodeLoadedCallback callback) {
    ensureMutable();
    nodeRegistrations.add(new NodeRegistration(nodeId, callback));
  }

  /**
   * Register behavior that applies to every node created by the loader.
   *
   * <p>The callback includes type nodes, instances, Methods, and Views in merged NodeSet order. For
   * node classes without a type definition, {@link LoadedNode#typeDefinitionId()} is {@link
   * NodeId#NULL_VALUE}. Catch-all callbacks run after all matching type and specific-node callbacks
   * for each node. Their callback receives a {@link NodeMatch.AnyNode}.
   *
   * @param callback the callback to invoke for each post-load node.
   * @throws IllegalStateException if loading has begun.
   */
  public synchronized void onNode(NodeLoadedCallback callback) {
    ensureMutable();
    anyNodeCallbacks.add(callback);
  }

  synchronized Dispatcher freeze() {
    frozen = true;
    return new Dispatcher(
        List.copyOf(typeRegistrations),
        List.copyOf(nodeRegistrations),
        List.copyOf(anyNodeCallbacks));
  }

  private void ensureMutable() {
    if (frozen) {
      throw new IllegalStateException("registrations cannot be added after loading has begun");
    }
  }

  static final class Dispatcher {

    private static final NodeMatch.AnyNode ANY_NODE_MATCH = new NodeMatch.AnyNode();

    private final List<TypeRegistration> typeRegistrations;
    private final List<NodeRegistration> nodeRegistrations;
    private final List<NodeLoadedCallback> anyNodeCallbacks;

    private Dispatcher(
        List<TypeRegistration> typeRegistrations,
        List<NodeRegistration> nodeRegistrations,
        List<NodeLoadedCallback> anyNodeCallbacks) {
      this.typeRegistrations = typeRegistrations;
      this.nodeRegistrations = nodeRegistrations;
      this.anyNodeCallbacks = anyNodeCallbacks;
    }

    boolean hasCallbacks() {
      return !typeRegistrations.isEmpty()
          || !nodeRegistrations.isEmpty()
          || !anyNodeCallbacks.isEmpty();
    }

    boolean hasTypeRegistrations() {
      return !typeRegistrations.isEmpty();
    }

    void fireCallbacks(LoadedNode loadedNode, List<NodeId> typeHierarchy) {
      for (int depth = 0; depth < typeHierarchy.size(); depth++) {
        NodeId typeDefinitionId = typeHierarchy.get(depth);
        TypeRelationship relationship =
            depth == 0 ? TypeRelationship.EXACT : TypeRelationship.SUBTYPE;

        for (TypeRegistration registration : typeRegistrations) {
          boolean matchesDirectType = relationship == TypeRelationship.EXACT;
          boolean includesSubtypes = registration.typeMatch() == TypeMatch.INCLUDE_SUBTYPES;

          if (registration.typeDefinitionId().equals(typeDefinitionId)
              && (matchesDirectType || includesSubtypes)) {
            registration
                .callback()
                .onNodeLoaded(
                    loadedNode,
                    new NodeMatch.Type(
                        registration.typeDefinitionId(), registration.typeMatch(), relationship));
          }
        }
      }

      for (NodeRegistration registration : nodeRegistrations) {
        if (registration.nodeId().equals(loadedNode.node().getNodeId())) {
          registration
              .callback()
              .onNodeLoaded(loadedNode, new NodeMatch.SpecificNode(registration.nodeId()));
        }
      }

      for (NodeLoadedCallback callback : anyNodeCallbacks) {
        callback.onNodeLoaded(loadedNode, ANY_NODE_MATCH);
      }
    }
  }

  private record TypeRegistration(
      NodeId typeDefinitionId, TypeMatch typeMatch, NodeLoadedCallback callback) {}

  private record NodeRegistration(NodeId nodeId, NodeLoadedCallback callback) {}
}
