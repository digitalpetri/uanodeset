package com.digitalpetri.opcua.uanodeset;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObjectTypeInfoTreeTest {

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
    var objectTypeTree = ObjectTypeInfoTree.create(nodeSet);

    assertNotNull(objectTypeTree);
    assertEquals(
        NodeIds.BaseObjectType.toParseableString(),
        objectTypeTree.getRootTypeInfo().getTypeNode().getNodeId());
  }

  @Test
  void resolvesSubtypes() {
    var objectTypeTree = ObjectTypeInfoTree.create(nodeSet);

    ObjectTypeInfo folderType = objectTypeTree.getTypeInfo(NodeIds.FolderType.toParseableString());

    assertNotNull(folderType);
    assertTrue(
        objectTypeTree.isSubtypeOf(NodeIds.FolderType.toParseableString(), NodeIds.BaseObjectType));
    assertEquals(
        NodeIds.BaseObjectType.toParseableString(),
        folderType.getParent().getTypeNode().getNodeId());
  }
}
