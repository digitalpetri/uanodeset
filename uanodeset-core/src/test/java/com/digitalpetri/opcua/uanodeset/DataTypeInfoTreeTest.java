package com.digitalpetri.opcua.uanodeset;

import jakarta.xml.bind.JAXBException;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DataTypeInfoTreeTest {

  @Nested
  class OpcUaNamespace {

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
      var dataTypeTree = DataTypeInfoTree.create(nodeSet);

      assertNotNull(dataTypeTree);
    }

    @Test
    void isStructure() {
      var dataTypeTree = DataTypeInfoTree.create(nodeSet);

      assertTrue(dataTypeTree.isStructure(NodeIds.Argument));
      assertFalse(dataTypeTree.isStructure(NodeIds.String));
      assertFalse(dataTypeTree.isStructure(NodeIds.ApplicationType));
    }

    @Test
    void isEnumeration() {
      var dataTypeTree = DataTypeInfoTree.create(nodeSet);

      assertTrue(dataTypeTree.isEnumeration(NodeIds.ApplicationType));
      assertFalse(dataTypeTree.isEnumeration(NodeIds.String));
      assertFalse(dataTypeTree.isEnumeration(NodeIds.Argument));
    }

  }

}