package com.digitalpetri.opcua.uanodeset.namespace;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.AddressSpace;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransportFactory;
import org.junit.jupiter.api.Test;

class NodeSetNodeLoaderReferenceTest {

  private static final String TEST_NAMESPACE_URI = "urn:uanodeset:test:reference-normalization";

  @Test
  void reciprocalDeclarationsLoadExactlyOneReferenceInEachDirection() {
    LoadedAddressSpace loaded = loadAddressSpace();
    try {
      NodeId parentNodeId = loaded.nodeId(5000);
      NodeId childNodeId = loaded.nodeId(5040);

      assertEquals(
          1,
          matchingReferences(
                  loaded.addressSpace(),
                  parentNodeId,
                  NodeIds.HasComponent,
                  childNodeId,
                  Reference::isForward)
              .size());
      assertEquals(
          1,
          matchingReferences(
                  loaded.addressSpace(),
                  childNodeId,
                  NodeIds.HasComponent,
                  parentNodeId,
                  Reference::isInverse)
              .size());
    } finally {
      loaded.addressSpace().shutdown();
    }
  }

  @Test
  void oneSidedDeclarationSynthesizesExactlyOneReferenceInEachDirection() {
    LoadedAddressSpace loaded = loadAddressSpace();
    try {
      NodeId parentNodeId = loaded.nodeId(5000);
      NodeId childNodeId = loaded.nodeId(5041);

      assertEquals(
          1,
          matchingReferences(
                  loaded.addressSpace(),
                  parentNodeId,
                  NodeIds.HasProperty,
                  childNodeId,
                  Reference::isForward)
              .size());
      assertEquals(
          1,
          matchingReferences(
                  loaded.addressSpace(),
                  childNodeId,
                  NodeIds.HasProperty,
                  parentNodeId,
                  Reference::isInverse)
              .size());
    } finally {
      loaded.addressSpace().shutdown();
    }
  }

  @Test
  void addressSpaceBrowseDoesNotExposeDuplicateIdenticalReferences() {
    LoadedAddressSpace loaded = loadAddressSpace();
    try {
      NodeId parentNodeId = loaded.nodeId(5000);
      NodeId childNodeId = loaded.nodeId(5040);
      ViewDescription view = new ViewDescription(NodeId.NULL_VALUE, DateTime.NULL_VALUE, uint(0));

      List<AddressSpace.ReferenceResult> results =
          loaded
              .addressSpace()
              .browse(
                  new AddressSpace.BrowseContext(loaded.server(), null),
                  view,
                  List.of(parentNodeId));
      AddressSpace.ReferenceResult.ReferenceList result =
          assertInstanceOf(AddressSpace.ReferenceResult.ReferenceList.class, results.get(0));

      assertEquals(
          1,
          result.references().stream()
              .filter(Reference::isForward)
              .filter(reference -> NodeIds.HasComponent.equals(reference.getReferenceTypeId()))
              .filter(
                  reference ->
                      reference
                          .getTargetNodeId()
                          .toNodeId(loaded.server().getNamespaceTable())
                          .filter(childNodeId::equals)
                          .isPresent())
              .count());
    } finally {
      loaded.addressSpace().shutdown();
    }
  }

  private static List<Reference> matchingReferences(
      TestAddressSpace addressSpace,
      NodeId sourceNodeId,
      NodeId referenceTypeId,
      NodeId targetNodeId,
      Predicate<Reference> directionFilter) {

    return addressSpace.getNodeManager().getReferences(sourceNodeId).stream()
        .filter(directionFilter)
        .filter(reference -> referenceTypeId.equals(reference.getReferenceTypeId()))
        .filter(
            reference ->
                reference
                    .getTargetNodeId()
                    .toNodeId(addressSpace.server().getNamespaceTable())
                    .filter(targetNodeId::equals)
                    .isPresent())
        .toList();
  }

  private static LoadedAddressSpace loadAddressSpace() {
    OpcUaServer server = newBareServer();
    TestAddressSpace addressSpace = new TestAddressSpace(server);
    addressSpace.startup();

    UShort namespaceIndex = server.getNamespaceTable().getIndex(TEST_NAMESPACE_URI);
    return new LoadedAddressSpace(server, addressSpace, namespaceIndex);
  }

  private static OpcUaServer newBareServer() {
    OpcUaServerConfig config = OpcUaServerConfig.builder().build();
    OpcServerTransportFactory transportFactory = profile -> null;
    return new OpcUaServer(config, transportFactory);
  }

  private record LoadedAddressSpace(
      OpcUaServer server, TestAddressSpace addressSpace, UShort namespaceIndex) {

    private NodeId nodeId(int identifier) {
      return new NodeId(namespaceIndex, identifier);
    }
  }

  private static final class TestAddressSpace extends NodeSetNamespace {

    private final List<InputStream> inputStreams;

    private TestAddressSpace(OpcUaServer server) {
      super(server, TEST_NAMESPACE_URI);
      inputStreams =
          List.of(
              Objects.requireNonNull(
                  getClass().getResourceAsStream("/ReferenceNormalization.NodeSet2.xml")));
    }

    @Override
    protected EncodingContext getEncodingContext() {
      return server().getStaticEncodingContext();
    }

    @Override
    protected List<InputStream> getNodeSetInputStreams() {
      return inputStreams;
    }

    private OpcUaServer server() {
      return getServer();
    }
  }
}
