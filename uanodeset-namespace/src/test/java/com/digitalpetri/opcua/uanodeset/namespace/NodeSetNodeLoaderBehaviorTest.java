/*
 * Copyright (c) 2026 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.opcua.uanodeset.namespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.uanodeset.NodeSet;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.eclipse.milo.opcua.sdk.server.NodeManager;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransportFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class NodeSetNodeLoaderBehaviorTest {

  private static final String TEST_NAMESPACE_URI = "urn:uanodeset:test:node-behaviors";

  private final List<TestAddressSpace> startedAddressSpaces = new ArrayList<>();

  @AfterEach
  void shutdownAddressSpaces() {
    startedAddressSpaces.forEach(TestAddressSpace::shutdown);
  }

  @Nested
  class CatchAllCallbacks {

    // Consumers may build state incrementally from callbacks, so every selected node must be
    // visited exactly once in deterministic NodeSet document order.
    @Test
    void callbackRunsForEveryLoadedNodeInDocumentOrder() {
      List<LoadedNode> callbacks = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onNode((loadedNode, match) -> callbacks.add(loadedNode)));

      List<NodeId> expectedOrder =
          List.of(
              loaded.nodeId(1000),
              loaded.nodeId(1001),
              loaded.nodeId(2000),
              loaded.nodeId(2001),
              loaded.nodeId(2002),
              loaded.nodeId(1100),
              loaded.nodeId(1101),
              loaded.nodeId(2100),
              loaded.nodeId(3000));
      List<NodeId> actualOrder = callbacks.stream().map(n -> n.node().getNodeId()).toList();

      assertEquals(expectedOrder, actualOrder);
    }

    // Behavior attachment may inspect sibling nodes immediately; invoking it during an earlier
    // loader phase would expose a partially constructed address space.
    @Test
    void callbackRunsAfterEveryNodeHasBeenAdded() {
      List<LoadedNode> callbacks = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onNode((loadedNode, match) -> callbacks.add(loadedNode)));
      LoadedNode variable = callbackFor(callbacks, loaded.nodeId(2100));

      assertTrue(
          variable.nodeContext().getNodeManager().getNode(loaded.nodeId(3000)).isPresent(),
          "a later node in document order must already exist when the callback runs");
    }

    // Behavior attachment may read a Variable immediately, so the callback must not observe the
    // default Bad_NoValue state that exists before value decoding finishes.
    @Test
    void callbackRunsAfterVariableValuesAreDecoded() {
      List<LoadedNode> callbacks = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onNode((loadedNode, match) -> callbacks.add(loadedNode)));
      LoadedNode variable = callbackFor(callbacks, loaded.nodeId(2100));

      assertEquals(
          Boolean.TRUE,
          ((UaVariableNode) variable.node()).getValue().getValue().getValue(),
          "the Variable value must be decoded before the callback runs");
    }

    // Registry keys use server namespace indexes, so callback context must not leak the file-local
    // namespace index from the source NodeSet.
    @Test
    void loadedNodeReportsReindexedDirectTypeDefinitionsOnlyForInstances() {
      List<LoadedNode> callbacks = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onNode((loadedNode, match) -> callbacks.add(loadedNode)));

      assertEquals(
          loaded.nodeId(1001), callbackFor(callbacks, loaded.nodeId(2000)).typeDefinitionId());
      assertEquals(
          loaded.nodeId(1101), callbackFor(callbacks, loaded.nodeId(2100)).typeDefinitionId());
      assertEquals(
          NodeId.NULL_VALUE, callbackFor(callbacks, loaded.nodeId(3000)).typeDefinitionId());
    }

    // Consumers use the JAXB source node for extensions and documentation that Milo nodes do not
    // retain, so each callback must expose the source that corresponds to its loaded node.
    @Test
    void callbackProvidesTheMatchingSourceNodeFromTheMergedNodeSet() {
      List<LoadedNode> callbacks = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onNode((loadedNode, match) -> callbacks.add(loadedNode)));
      LoadedNode callback = callbackFor(callbacks, loaded.nodeId(2000));

      assertSame(
          callback.sourceNode(), callback.nodeSet().getNode(callback.sourceNode().getNodeId()));
    }
  }

  @Nested
  class ReferenceResolution {

    // NodeSet XML may spell namespace-zero reference types explicitly or declare a relationship
    // from its inverse side; both forms must resolve to the same direct type definition.
    @ParameterizedTest(name = "{2}")
    @CsvSource({
      "1001, 2000, explicit namespace-zero reference type",
      "1000, 2002, inverse-declared relationship"
    })
    void validHasTypeDefinitionReferenceFormsDispatchExactTypeCallbacks(
        int typeIdentifier, int instanceIdentifier, String referenceForm) {

      List<NodeId> matches = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onType(
                      new NodeId(namespaceIndex, typeIdentifier),
                      TypeMatch.EXACT,
                      (node, match) -> matches.add(node.node().getNodeId())));

      assertEquals(
          List.of(loaded.nodeId(instanceIdentifier)),
          matches,
          referenceForm + " must resolve to the instance's direct type");
    }
  }

  @Nested
  class TypeCallbacks {

    // Explicit namespace-zero HasSubtype references are semantically equivalent to i=45; base-type
    // behavior must still reach derived Object and Variable instances.
    @Test
    void includeSubtypesUsesCanonicalObjectAndVariableTypeHierarchies() {
      List<NodeId> objectMatches = new ArrayList<>();
      List<NodeId> variableMatches = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) -> {
                registry.onType(
                    new NodeId(namespaceIndex, 1000),
                    (node, match) -> objectMatches.add(node.node().getNodeId()));
                registry.onType(
                    new NodeId(namespaceIndex, 1100),
                    TypeMatch.INCLUDE_SUBTYPES,
                    (node, match) -> variableMatches.add(node.node().getNodeId()));
              });

      assertEquals(
          List.of(loaded.nodeId(2000), loaded.nodeId(2002)),
          objectMatches,
          "only the known derived and direct Object instances should match");
      assertEquals(List.of(loaded.nodeId(2100)), variableMatches);
    }

    // A companion type may be absent from the merged model; both modes must still match its direct
    // type exactly instead of dropping the callback with the unavailable hierarchy.
    @ParameterizedTest
    @EnumSource(TypeMatch.class)
    void unknownTypeHierarchyFallsBackToExactMatch(TypeMatch typeMatch) {
      List<NodeId> matches = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onType(
                      new NodeId(namespaceIndex, 1999),
                      typeMatch,
                      (node, match) -> matches.add(node.node().getNodeId())));

      assertEquals(List.of(loaded.nodeId(2001)), matches);
    }

    // Exact matching is for behavior that is valid only on the registered direct type; a derived
    // instance must not receive it accidentally.
    @Test
    void exactMatchIncludesDirectInstanceAndExcludesDerivedInstance() {
      List<NodeId> matches = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onType(
                      new NodeId(namespaceIndex, 1000),
                      TypeMatch.EXACT,
                      (node, match) -> matches.add(node.node().getNodeId())));

      assertEquals(
          List.of(loaded.nodeId(2002)),
          matches,
          "the derived Object instance must not match an exact base-type registration");
    }

    // Behavior can be layered from specific to generic; the derived callback must run first,
    // registrations at the same type must retain order, and catch-all processing must run last.
    @Test
    void matchingCallbacksRunMostDerivedFirstAndCatchAllLast() {
      Map<NodeId, List<String>> callbackOrder = new HashMap<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) -> {
                registry.onType(
                    new NodeId(namespaceIndex, 1000),
                    (node, match) -> recordCallback(callbackOrder, node, "object-base-first"));
                registry.onType(
                    new NodeId(namespaceIndex, 1001),
                    (node, match) -> recordCallback(callbackOrder, node, "object-derived"));
                registry.onType(
                    new NodeId(namespaceIndex, 1000),
                    (node, match) -> recordCallback(callbackOrder, node, "object-base-second"));
                registry.onType(
                    NodeIds.BaseObjectType,
                    (node, match) -> recordCallback(callbackOrder, node, "object-root"));
                registry.onNode(
                    new NodeId(namespaceIndex, 2000),
                    (node, match) -> recordCallback(callbackOrder, node, "specific-node"));
                registry.onNode((node, match) -> recordCallback(callbackOrder, node, "catch-all"));
              });

      assertEquals(
          List.of(
              "object-derived",
              "object-base-first",
              "object-base-second",
              "object-root",
              "specific-node",
              "catch-all"),
          callbackOrder.get(loaded.nodeId(2000)));
    }
  }

  @Nested
  class MatchContext {

    // Shared type callbacks need to distinguish a direct instance from one reached through subtype
    // matching without reconstructing the type hierarchy themselves.
    @Test
    void typeCallbackReportsExactOrSubtypeRelationship() {
      Map<NodeId, NodeMatch.Type> matches = new HashMap<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onType(
                      new NodeId(namespaceIndex, 1000),
                      TypeMatch.INCLUDE_SUBTYPES,
                      (node, match) ->
                          matches.put(node.node().getNodeId(), (NodeMatch.Type) match)));
      NodeId registeredTypeId = loaded.nodeId(1000);

      assertEquals(
          new NodeMatch.Type(
              registeredTypeId, TypeMatch.INCLUDE_SUBTYPES, TypeRelationship.SUBTYPE),
          matches.get(loaded.nodeId(2000)));
      assertEquals(
          new NodeMatch.Type(registeredTypeId, TypeMatch.INCLUDE_SUBTYPES, TypeRelationship.EXACT),
          matches.get(loaded.nodeId(2002)));
    }

    // A callback for a distinguished node must not run for peers and must identify the NodeId
    // registration that selected it.
    @Test
    void specificNodeCallbackRunsOnlyForItsRegisteredNode() {
      List<LoadedNode> callbacks = new ArrayList<>();
      List<NodeMatch> matches = new ArrayList<>();

      LoadedAddressSpace loaded =
          loadAddressSpace(
              (registry, namespaceIndex) ->
                  registry.onNode(
                      new NodeId(namespaceIndex, 2100),
                      (node, match) -> {
                        callbacks.add(node);
                        matches.add(match);
                      }));

      assertEquals(List.of(loaded.nodeId(2100)), callbackNodeIds(callbacks));
      assertEquals(List.of(new NodeMatch.SpecificNode(loaded.nodeId(2100))), matches);
    }

    // A shared callback can branch on the match kind instead of relying on which registration
    // method happened to receive its method reference.
    @Test
    void catchAllCallbackReportsAnyNodeMatch() {
      List<NodeMatch> matches = new ArrayList<>();

      loadAddressSpace(
          (registry, namespaceIndex) -> registry.onNode((loadedNode, match) -> matches.add(match)));

      assertFalse(matches.isEmpty(), "the catch-all registration must receive loaded nodes");
      assertTrue(
          matches.stream().allMatch(NodeMatch.AnyNode.class::isInstance),
          "every catch-all invocation must identify its any-node registration");
    }
  }

  @Nested
  class FailureHandling {

    // Starting with silently missing behavior is unsafe, so the original callback failure must
    // escape the loader instead of being logged and ignored.
    @Test
    void callbackFailureAbortsTheLoad() throws Exception {
      var registry = new NodeBehaviorRegistry();
      var failure = new BehaviorLoadException();
      LoaderFixture fixture = newNodeLoader(registry);
      registry.onType(
          new NodeId(fixture.namespaceIndex(), 1001),
          (loadedNode, match) -> {
            throw failure;
          });

      BehaviorLoadException thrown =
          assertThrows(BehaviorLoadException.class, fixture.loader()::loadNodes);

      assertSame(failure, thrown);
    }

    // Changing registrations during dispatch would make behavior depend on document order, so the
    // registry must reject mutation once loading has captured its callback set.
    @Test
    void registrationAfterLoadingBeginsIsRejected() throws Exception {
      var registry = new NodeBehaviorRegistry();
      registry.onNode(
          (loadedNode, match) ->
              registry.onType(loadedNode.typeDefinitionId(), (ignoredNode, ignoredMatch) -> {}));
      LoaderFixture fixture = newNodeLoader(registry);

      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, fixture.loader()::loadNodes);

      assertEquals("registrations cannot be added after loading has begun", thrown.getMessage());
    }
  }

  private LoadedAddressSpace loadAddressSpace(
      BiConsumer<NodeBehaviorRegistry, UShort> behaviorRegistration) {

    OpcUaServer server = newBareServer();
    server.getNamespaceTable().add("urn:filler:1");
    server.getNamespaceTable().add("urn:filler:2");

    TestAddressSpace addressSpace = new TestAddressSpace(server, behaviorRegistration);
    startedAddressSpaces.add(addressSpace);
    addressSpace.startup();

    UShort namespaceIndex = addressSpace.getNamespaceIndex();
    return new LoadedAddressSpace(addressSpace, namespaceIndex);
  }

  private static LoaderFixture newNodeLoader(NodeBehaviorRegistry registry) throws Exception {
    NodeSet nodeSet;
    try (InputStream inputStream = nodeSetInputStream()) {
      nodeSet = NodeSet.load(inputStream);
    }

    OpcUaServer server = newBareServer();
    server.getNamespaceTable().add("urn:filler:1");
    server.getNamespaceTable().add("urn:filler:2");
    UShort namespaceIndex = server.getNamespaceTable().add(TEST_NAMESPACE_URI);
    var nodeManager = new UaNodeManager();
    UaNodeContext nodeContext = newNodeContext(server, nodeManager);

    var loader =
        new NodeSetNodeLoader(
            nodeSet,
            nodeContext,
            server.getStaticEncodingContext(),
            TEST_NAMESPACE_URI::equals,
            registry);
    return new LoaderFixture(loader, namespaceIndex);
  }

  private static LoadedNode callbackFor(List<LoadedNode> callbacks, NodeId nodeId) {
    return callbacks.stream()
        .filter(callback -> callback.node().getNodeId().equals(nodeId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("callback not found for " + nodeId));
  }

  private static List<NodeId> callbackNodeIds(List<LoadedNode> callbacks) {
    return callbacks.stream().map(callback -> callback.node().getNodeId()).toList();
  }

  private static void recordCallback(
      Map<NodeId, List<String>> callbackOrder, LoadedNode loadedNode, String callbackName) {

    callbackOrder
        .computeIfAbsent(loadedNode.node().getNodeId(), ignored -> new ArrayList<>())
        .add(callbackName);
  }

  private static UaNodeContext newNodeContext(OpcUaServer server, NodeManager<UaNode> nodeManager) {
    return new UaNodeContext() {
      @Override
      public OpcUaServer getServer() {
        return server;
      }

      @Override
      public NodeManager<UaNode> getNodeManager() {
        return nodeManager;
      }
    };
  }

  private static OpcUaServer newBareServer() {
    OpcUaServerConfig config = OpcUaServerConfig.builder().build();
    OpcServerTransportFactory transportFactory = profile -> null;
    return new OpcUaServer(config, transportFactory);
  }

  private static InputStream nodeSetInputStream() {
    return Objects.requireNonNull(
        NodeSetNodeLoaderBehaviorTest.class.getResourceAsStream("/NodeBehaviors.NodeSet2.xml"));
  }

  private record LoadedAddressSpace(TestAddressSpace addressSpace, UShort namespaceIndex) {

    private NodeId nodeId(int identifier) {
      return new NodeId(namespaceIndex, identifier);
    }
  }

  private record LoaderFixture(NodeSetNodeLoader loader, UShort namespaceIndex) {}

  private static final class TestAddressSpace extends NodeSetNamespace {

    private final BiConsumer<NodeBehaviorRegistry, UShort> behaviorRegistration;
    private final List<InputStream> inputStreams;

    private TestAddressSpace(
        OpcUaServer server, BiConsumer<NodeBehaviorRegistry, UShort> behaviorRegistration) {
      super(server, TEST_NAMESPACE_URI);
      this.behaviorRegistration = behaviorRegistration;
      inputStreams = List.of(nodeSetInputStream());
    }

    @Override
    protected EncodingContext getEncodingContext() {
      return getServer().getStaticEncodingContext();
    }

    @Override
    protected List<InputStream> getNodeSetInputStreams() {
      return inputStreams;
    }

    @Override
    protected void registerNodeBehaviors(NodeBehaviorRegistry registry) {
      behaviorRegistration.accept(registry, getNamespaceIndex());
    }
  }

  private static final class BehaviorLoadException extends RuntimeException {}
}
