package com.digitalpetri.opcua.uanodeset.namespace;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.digitalpetri.opcua.uanodeset.DataTypeInfoTree;
import com.digitalpetri.opcua.uanodeset.NodeSet;
import java.io.InputStream;
import java.util.Objects;
import org.eclipse.milo.opcua.sdk.server.NodeManager;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.structured.DataTypeDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.transport.server.OpcServerTransportFactory;
import org.junit.jupiter.api.Test;
import org.opcfoundation.ua.UADataType;

class NodeSetNodeLoaderDataTypeDefinitionTest {

  private static final String TEST_NAMESPACE_URI = "urn:uanodeset:test:basedatatype-reindex";

  /**
   * Regression test: the baseDataType of a StructureDefinition must be reindexed against the server
   * namespace table, like every other NodeId in the definition.
   *
   * <p>The fixture defines ConcreteTestType (ns=1;i=3006) as a subtype of AbstractTestType
   * (ns=1;i=3003), with namespace index 1 local to the NodeSet file. The server namespace table is
   * arranged so that the test namespace gets an index different from 1 — like the DataTypeTest
   * namespace on the public Milo demo server, where this bug was originally observed (advertised
   * baseDataType ns=1;i=3003 did not exist in the server address space; see
   * node-opcua/node-opcua#1520).
   */
  @Test
  void baseDataTypeIsReindexedAgainstServerNamespaceTable() throws Exception {
    InputStream inputStream =
        Objects.requireNonNull(getClass().getResourceAsStream("/BaseDataTypeReindex.NodeSet2.xml"));

    NodeSet nodeSet = NodeSet.load(inputStream);

    OpcUaServer server = newBareServer();
    // add filler namespaces so the test namespace lands on an index different from its
    // file-local index 1, like the DataTypeTest namespace on the public Milo demo server
    server.getNamespaceTable().add("urn:filler:1");
    server.getNamespaceTable().add("urn:filler:2");
    UShort serverIndex = server.getNamespaceTable().add(TEST_NAMESPACE_URI);
    assertNotEquals(ushort(1), serverIndex);

    var nodeManager = new UaNodeManager();
    var context =
        new UaNodeContext() {
          @Override
          public OpcUaServer getServer() {
            return server;
          }

          @Override
          public NodeManager<UaNode> getNodeManager() {
            return nodeManager;
          }
        };

    var loader =
        new NodeSetNodeLoader(
            nodeSet, context, server.getStaticEncodingContext(), TEST_NAMESPACE_URI::equals);

    DataTypeInfoTree dataTypeTree = DataTypeInfoTree.create(nodeSet);

    UADataType concreteTestType = getDataType(nodeSet, "ns=1;i=3006");
    DataTypeDefinition definition = loader.newDataTypeDefinition(concreteTestType, dataTypeTree);

    StructureDefinition structureDefinition =
        assertInstanceOf(StructureDefinition.class, definition);

    // the parent AbstractTestType is ns=1;i=3003 in the NodeSet file and must be
    // reindexed to the index of the test namespace in the server namespace table
    assertEquals(new NodeId(serverIndex, 3003), structureDefinition.getBaseDataType());
  }

  /** A baseDataType from namespace 0 must remain untouched. */
  @Test
  void namespaceZeroBaseDataTypeIsUnchanged() throws Exception {
    InputStream inputStream =
        Objects.requireNonNull(getClass().getResourceAsStream("/BaseDataTypeReindex.NodeSet2.xml"));

    NodeSet nodeSet = NodeSet.load(inputStream);

    OpcUaServer server = newBareServer();
    server.getNamespaceTable().add(TEST_NAMESPACE_URI);

    var nodeManager = new UaNodeManager();
    var context =
        new UaNodeContext() {
          @Override
          public OpcUaServer getServer() {
            return server;
          }

          @Override
          public NodeManager<UaNode> getNodeManager() {
            return nodeManager;
          }
        };

    var loader =
        new NodeSetNodeLoader(
            nodeSet, context, server.getStaticEncodingContext(), TEST_NAMESPACE_URI::equals);

    DataTypeInfoTree dataTypeTree = DataTypeInfoTree.create(nodeSet);

    UADataType abstractTestType = getDataType(nodeSet, "ns=1;i=3003");
    DataTypeDefinition definition = loader.newDataTypeDefinition(abstractTestType, dataTypeTree);

    StructureDefinition structureDefinition =
        assertInstanceOf(StructureDefinition.class, definition);

    // the parent of AbstractTestType is i=22 (Structure), namespace 0: no reindexing
    assertEquals(new NodeId(0, 22), structureDefinition.getBaseDataType());
  }

  private static UADataType getDataType(NodeSet nodeSet, String nodeId) {
    UADataType dataType =
        nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod().stream()
            .filter(UADataType.class::isInstance)
            .map(UADataType.class::cast)
            .filter(node -> nodeId.equals(node.getNodeId()))
            .findFirst()
            .orElse(null);
    assertNotNull(dataType, "fixture DataType not found: " + nodeId);
    return dataType;
  }

  private static OpcUaServer newBareServer() {
    OpcUaServerConfig config = OpcUaServerConfig.builder().build();
    OpcServerTransportFactory transportFactory = profile -> null;
    return new OpcUaServer(config, transportFactory);
  }
}
