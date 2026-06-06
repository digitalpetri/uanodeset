package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.parser.IndexUtil;
import com.digitalpetri.opcua.uanodeset.parser.UANodeSetMerger;
import com.digitalpetri.opcua.uanodeset.parser.UANodeSetParser;
import jakarta.xml.bind.JAXBException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.*;

/**
 * Normalized, indexed view of one or more OPC UA NodeSets.
 *
 * <p>A {@link NodeSet} wraps a JAXB {@link UANodeSet} after applying the post-processing that most
 * loaders, validators, and generators need before they can reason about the model:
 *
 * <ul>
 *   <li>The URI table contains the base OPC UA namespace URI, and the UANodeSet contains the base
 *       OPC UA model
 *   <li>Fields containing NodeIds are replaced with NodeIds that have had any potential alias
 *       resolved
 *   <li>RolePermissions and AccessRestrictions defined at the model level have been applied to
 *       individual UANodes
 *   <li>Implicit References are created for all References that are not explicitly defined in the
 *       UANodeSet
 * </ul>
 *
 * <p>Use {@link #load(InputStream)}, {@link #load(List)}, {@link #from(UANodeSet)}, or {@link
 * #from(Collection)} when starting from an extension model. Those factories merge the supplied
 * model with the bundled OPC UA base NodeSet before constructing the indexed context.
 */
public class NodeSet implements NodeSetContext {

  private final Map<String, String> aliases = new HashMap<>();
  private final Map<String, UANode> nodeMap = new HashMap<>();

  private final CombinedReferences combinedReferences = new CombinedReferences();
  private final Map<String, List<Reference>> explicitReferences = new HashMap<>();
  private final Map<String, List<Reference>> implicitReferences = new HashMap<>();

  private final UANodeSet nodeSet;

  /**
   * Create an indexed context around an already-merged NodeSet.
   *
   * <p>The supplied {@link UANodeSet} must already contain the base OPC UA model. Factories such as
   * {@link #from(UANodeSet)} are the preferred entry point when callers have only an extension
   * model.
   *
   * @param nodeSet the merged NodeSet to index and normalize.
   */
  public NodeSet(UANodeSet nodeSet) {
    this.nodeSet = nodeSet;

    assert nodeSet.getModels().getModel().stream()
        .anyMatch(e -> Objects.equals(Namespaces.OPC_UA, e.getModelUri()));

    AliasTable aliasTable = nodeSet.getAliases();
    if (aliasTable != null) {
      aliasTable.getAlias().forEach(nia -> aliases.put(nia.getAlias(), nia.getValue()));
    }

    UriTable namespaceUris = nodeSet.getNamespaceUris();
    if (namespaceUris == null) {
      namespaceUris = new UriTable();
      namespaceUris.getUri().add(Namespaces.OPC_UA);
      nodeSet.setNamespaceUris(namespaceUris);
    }

    var rolePermissionsByModelUri = new HashMap<String, Optional<ListOfRolePermissions>>();
    var accessRestrictionsByModelUri = new HashMap<String, Optional<Integer>>();

    nodeSet
        .getUAObjectOrUAVariableOrUAMethod()
        .forEach(
            node -> {
              node.setNodeId(resolveAlias(node.getNodeId()));

              if (node instanceof UADataType dataType) {
                DataTypeDefinition definition = dataType.getDefinition();

                if (definition != null) {
                  definition
                      .getField()
                      .forEach(field -> field.setDataType(resolveAlias(field.getDataType())));
                }
              }

              if (node instanceof UAVariable variable) {
                variable.setDataType(resolveAlias(variable.getDataType()));
              }

              if (node instanceof UAVariableType variableType) {
                variableType.setDataType(resolveAlias(variableType.getDataType()));
              }

              // TODO other nodes with aliases that need resolving?

              // Maybe set RolePermissions from the model
              if (!node.isHasNoPermissions()) {
                ListOfRolePermissions nodeRolePermissions = node.getRolePermissions();

                if (nodeRolePermissions == null
                    || nodeRolePermissions.getRolePermission().isEmpty()) {

                  Optional<ListOfRolePermissions> modelRolePermissions =
                      rolePermissionsByModelUri.computeIfAbsent(
                          getNamespaceUri(node.getNodeId()),
                          namespaceUri -> {
                            Optional<ModelTableEntry> modelTableEntry =
                                nodeSet.getModels().getModel().stream()
                                    .filter(e -> namespaceUri.equals(e.getModelUri()))
                                    .findFirst();

                            return modelTableEntry.flatMap(
                                e -> Optional.ofNullable(e.getRolePermissions()));
                          });

                  modelRolePermissions.ifPresent(node::setRolePermissions);
                }
              }

              // Maybe set AccessRestrictions from the model
              if (node.getAccessRestrictions() == null) {
                Optional<Integer> modelAccessRestrictions =
                    accessRestrictionsByModelUri.computeIfAbsent(
                        getNamespaceUri(node.getNodeId()),
                        namespaceUri -> {
                          Optional<ModelTableEntry> modelTableEntry =
                              nodeSet.getModels().getModel().stream()
                                  .filter(e -> namespaceUri.equals(e.getModelUri()))
                                  .findFirst();

                          return modelTableEntry.flatMap(
                              e -> Optional.of(e.getAccessRestrictions()));
                        });

                modelAccessRestrictions.ifPresent(node::setAccessRestrictions);
              }

              nodeMap.put(node.getNodeId(), node);

              ListOfReferences references = node.getReferences();

              // resolve Reference aliases and add explicit/implicit References
              if (references != null) {
                references
                    .getReference()
                    .forEach(
                        reference -> {
                          reference.setValue(resolveAlias(reference.getValue()));
                          reference.setReferenceType(resolveAlias(reference.getReferenceType()));
                          explicitReferences
                              .computeIfAbsent(node.getNodeId(), k -> new ArrayList<>())
                              .add(reference);
                          var inverse = new Reference();
                          inverse.setValue(node.getNodeId());
                          inverse.setIsForward(!reference.isIsForward());
                          inverse.setReferenceType(reference.getReferenceType());
                          implicitReferences
                              .computeIfAbsent(reference.getValue(), k -> new ArrayList<>())
                              .add(inverse);
                        });
              }
            });
  }

  /**
   * Get the normalized JAXB NodeSet backing this context.
   *
   * @return the merged and normalized NodeSet.
   */
  @Override
  public UANodeSet getNodeSet() {
    return nodeSet;
  }

  /**
   * Look up a node by its normalized NodeId string.
   *
   * @param nodeId the NodeId of the node to get.
   * @return the matching node, or {@code null} when the NodeId is not present.
   */
  @Override
  public @Nullable UANode getNode(String nodeId) {
    return nodeMap.get(nodeId);
  }

  /**
   * Get all references known for a node.
   *
   * <p>The returned references include explicit references from the XML and implicit inverse
   * references synthesized while the NodeSet was indexed.
   *
   * @param nodeId the NodeId of the node to inspect.
   * @return the deduplicated references for the node.
   */
  @Override
  public List<Reference> getReferences(String nodeId) {
    return combinedReferences.get(nodeId);
  }

  /**
   * Get the references explicitly declared on a node in the source XML.
   *
   * @param nodeId the NodeId of the node to inspect.
   * @return the explicit references for the node.
   */
  @Override
  public List<Reference> getExplicitReferences(String nodeId) {
    return explicitReferences.getOrDefault(nodeId, Collections.emptyList());
  }

  /**
   * Get the implicit references synthesized from other nodes' explicit references.
   *
   * @param nodeId the NodeId of the node to inspect.
   * @return the implicit references for the node.
   */
  @Override
  public List<Reference> getImplicitReferences(String nodeId) {
    return implicitReferences.getOrDefault(nodeId, Collections.emptyList());
  }

  private String getNamespaceUri(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);

    int namespaceIndex = 0;
    if (matcher.matches()) {
      namespaceIndex = Integer.parseInt(matcher.group(1));
    }

    return nodeSet.getNamespaceUris().getUri().get(namespaceIndex);
  }

  private String resolveAlias(String nodeIdOrAlias) {
    return aliases.getOrDefault(nodeIdOrAlias, nodeIdOrAlias);
  }

  private class CombinedReferences {

    private final Map<String, List<Reference>> references = new HashMap<>();

    private List<Reference> get(String nodeId) {
      return references.computeIfAbsent(
          nodeId,
          id -> {
            var combined = new LinkedHashSet<ReferenceWrapper>();
            getExplicitReferences(nodeId).stream()
                .map(ReferenceWrapper::new)
                .forEach(combined::add);
            getImplicitReferences(nodeId).stream()
                .map(ReferenceWrapper::new)
                .forEach(combined::add);
            return combined.stream().map(ReferenceWrapper::get).toList();
          });
    }

    private static class ReferenceWrapper {
      private final Reference reference;

      private ReferenceWrapper(Reference reference) {
        this.reference = reference;
      }

      public Reference get() {
        return reference;
      }

      private boolean equivalent(Reference a, Reference b) {
        if (a == b) {
          return true;
        }
        if (a == null || b == null) {
          return false;
        }
        return Objects.equals(a.getValue(), b.getValue())
            && Objects.equals(a.getReferenceType(), b.getReferenceType())
            && a.isIsForward() == b.isIsForward();
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (obj instanceof ReferenceWrapper other) {
          return equivalent(this.reference, other.reference);
        }
        return false;
      }

      @Override
      public int hashCode() {
        if (reference == null) return 0;
        return Objects.hash(
            reference.getReferenceType(), reference.getValue(), reference.isIsForward());
      }
    }
  }

  /**
   * Parse and load a single NodeSet stream.
   *
   * <p>The parsed model is merged with the bundled OPC UA base NodeSet before indexing.
   *
   * @param inputStream the XML stream to parse.
   * @return the normalized NodeSet context.
   * @throws JAXBException if the stream cannot be parsed or the NodeSets cannot be merged.
   */
  public static NodeSet load(InputStream inputStream) throws JAXBException {
    return load(Collections.singletonList(inputStream));
  }

  /**
   * Parse and load multiple NodeSet streams in order.
   *
   * <p>The parsed models are merged with the bundled OPC UA base NodeSet before indexing. Later
   * NodeSets may contribute additional namespaces, models, and nodes to the merged result.
   *
   * @param inputStreams the XML streams to parse.
   * @return the normalized NodeSet context.
   * @throws JAXBException if any stream cannot be parsed or the NodeSets cannot be merged.
   */
  public static NodeSet load(List<InputStream> inputStreams) throws JAXBException {
    var nodeSets = new ArrayList<UANodeSet>();
    for (InputStream inputStream : inputStreams) {
      nodeSets.add(UANodeSetParser.parse(inputStream));
    }

    return from(nodeSets);
  }

  /**
   * Create a normalized context from an already-parsed extension NodeSet.
   *
   * <p>The supplied model is merged with the bundled OPC UA base NodeSet before indexing, so
   * callers do not need to include the standard base model themselves.
   *
   * @param nodeSet the parsed extension NodeSet.
   * @return the normalized NodeSet context.
   * @throws JAXBException if the bundled base NodeSet cannot be parsed or the NodeSets cannot be
   *     merged.
   */
  public static NodeSet from(UANodeSet nodeSet) throws JAXBException {
    return from(Collections.singletonList(nodeSet));
  }

  /**
   * Create a normalized context from already-parsed extension NodeSets.
   *
   * <p>The supplied models are merged with the bundled OPC UA base NodeSet before indexing. This is
   * the shared merge path used by all public loading factories.
   *
   * @param nodeSets the parsed extension NodeSets to merge in order.
   * @return the normalized NodeSet context.
   * @throws JAXBException if the bundled base NodeSet cannot be parsed or the NodeSets cannot be
   *     merged.
   */
  public static NodeSet from(Collection<UANodeSet> nodeSets) throws JAXBException {
    // merge the base OPC UA NodeSet with the provided NodeSets
    UANodeSet mergedNodeSet =
        UANodeSetParser.parse(
            NodeSet.class.getClassLoader().getResourceAsStream("1.05/Opc.Ua.NodeSet2.xml"));

    for (UANodeSet nodeSet : nodeSets) {
      mergedNodeSet = UANodeSetMerger.merge(mergedNodeSet, nodeSet);
    }

    return new NodeSet(mergedNodeSet);
  }
}
