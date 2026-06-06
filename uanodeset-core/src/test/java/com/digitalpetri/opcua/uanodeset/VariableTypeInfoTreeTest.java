package com.digitalpetri.opcua.uanodeset;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VariableTypeInfoTreeTest {

  NodeSet nodeSet;

  @BeforeEach
  void loadNodeSet() throws IOException, JAXBException {
    try (InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream("Opc.Ua.NodeSet2.xml")) {

      nodeSet = NodeSet.load(inputStream);
    }
  }

  @Test
  void create() {
    var variableTypeTree = VariableTypeInfoTree.create(nodeSet);

    assertNotNull(variableTypeTree);
    assertEquals(
        NodeIds.BaseVariableType.toParseableString(),
        variableTypeTree.getRootTypeInfo().getTypeNode().getNodeId());
  }

  @Test
  void resolvesSubtypes() {
    var variableTypeTree = VariableTypeInfoTree.create(nodeSet);

    VariableTypeInfo baseDataVariableType =
        variableTypeTree.getTypeInfo(NodeIds.BaseDataVariableType.toParseableString());

    assertNotNull(baseDataVariableType);
    assertTrue(
        variableTypeTree.isSubtypeOf(
            NodeIds.BaseDataVariableType.toParseableString(), NodeIds.BaseVariableType));
    assertEquals(
        NodeIds.BaseVariableType.toParseableString(),
        baseDataVariableType.getParent().getTypeNode().getNodeId());
  }
}
