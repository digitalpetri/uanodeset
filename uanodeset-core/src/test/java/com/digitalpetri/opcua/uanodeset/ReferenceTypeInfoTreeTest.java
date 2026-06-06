package com.digitalpetri.opcua.uanodeset;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReferenceTypeInfoTreeTest {

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
    var referenceTypeTree = ReferenceTypeInfoTree.create(nodeSet);

    assertNotNull(referenceTypeTree);
    assertEquals(
        NodeIds.References.toParseableString(),
        referenceTypeTree.getRootTypeInfo().getTypeNode().getNodeId());
  }

  @Test
  void resolvesSubtypes() {
    var referenceTypeTree = ReferenceTypeInfoTree.create(nodeSet);

    ReferenceTypeInfo hierarchicalReferences =
        referenceTypeTree.getTypeInfo(NodeIds.HierarchicalReferences.toParseableString());

    assertNotNull(hierarchicalReferences);
    assertTrue(
        referenceTypeTree.isSubtypeOf(
            NodeIds.HierarchicalReferences.toParseableString(), NodeIds.References));
    assertTrue(
        referenceTypeTree.isSubtypeOf(NodeIds.Organizes.toParseableString(), NodeIds.References));
    assertEquals(
        NodeIds.References.toParseableString(),
        hierarchicalReferences.getParent().getTypeNode().getNodeId());
  }
}
