package com.digitalpetri.opcua.uanodeset;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    @Test
    void genericDataTypeHelpers() {
      var dataTypeTree = DataTypeInfoTree.create(nodeSet);

      assertTrue(dataTypeTree.isAbstract(NodeIds.BaseDataType));
      assertFalse(dataTypeTree.isAbstract(NodeIds.String));
      assertTrue(dataTypeTree.isSimpleType(NodeIds.String));
      assertFalse(dataTypeTree.isSimpleType(NodeIds.Argument));
      assertFalse(dataTypeTree.isSimpleType("ns=1234;i=5678"));
      assertEquals(String.class, dataTypeTree.getBackingClass(NodeIds.String));
      assertEquals(Integer.class, dataTypeTree.getBackingClass(NodeIds.Enumeration));
      assertEquals(OpcUaDataType.String, dataTypeTree.getOpcUaDataType(NodeIds.String));
      assertEquals(OpcUaDataType.Int32, dataTypeTree.getOpcUaDataType(NodeIds.Enumeration));
      assertEquals(OpcUaDataType.Int32, dataTypeTree.getOpcUaDataType(NodeIds.ApplicationType));
    }
  }
}
