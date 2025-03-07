package com.digitalpetri.opcua.uanodeset;

import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;

class NodeSetTest {

  @ParameterizedTest(name = "load NodeSet for {0}")
  @CsvSource({
      "Opc.Ua.Adi.NodeSet2.xml",
      "Opc.Ua.AMLBaseTypes.NodeSet2.xml",
      "Opc.Ua.AMLLibraries.NodeSet2.xml",
      "Opc.Ua.AutoID.NodeSet2.xml",
      "Opc.Ua.Di.NodeSet2.xml",
      "Opc.Ua.IA.NodeSet2.xml",
      "Opc.Ua.Gds.NodeSet2.xml",
      "Opc.Ua.MachineTool.NodeSet2.xml",
      "Opc.Ua.NodeSet2.Services.xml",
      "Opc.Ua.NodeSet2.xml",
      "Opc.Ua.PackML.NodeSet2.xml",
      "Opc.Ua.PLCopen.NodeSet2_V1.02.xml",
      "Opc.Ua.Safety.NodeSet2.xml"
  })
  void load(String filename) throws JAXBException, IOException {
    try (InputStream inputStream =
             getClass().getClassLoader().getResourceAsStream(filename)) {

      NodeSet nodeSet = NodeSet.load(inputStream);

      System.out.println("Loaded " + filename + " and generated " +
          nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod().size() + " nodes.");
    }
  }

}