package com.digitalpetri.opcua.uanodeset.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.xml.bind.JAXBException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.junit.jupiter.api.Test;
import org.opcfoundation.ua.ModelTableEntry;
import org.opcfoundation.ua.NodeIdAlias;
import org.opcfoundation.ua.UANodeSet;
import org.opcfoundation.ua.UriTable;

class UANodeSetMergerTest {

  @Test
  public void testMergeAll() throws JAXBException {
    List<String> inputStreams =
        List.of(
            "Opc.Ua.Adi.NodeSet2.xml",
            "Opc.Ua.AMLBaseTypes.NodeSet2.xml",
            "Opc.Ua.AMLLibraries.NodeSet2.xml",
            "Opc.Ua.AutoID.NodeSet2.xml",
            "Opc.Ua.Di.NodeSet2.xml",
            "Opc.Ua.Gds.NodeSet2.xml",
            "Opc.Ua.IA.NodeSet2.xml",
            "Opc.Ua.Machinery.NodeSet2.xml",
            "Opc.Ua.MachineTool.NodeSet2.xml",
            "Opc.Ua.NodeSet2.Services.xml",
            "Opc.Ua.NodeSet2.xml",
            "Opc.Ua.PackML.NodeSet2.xml",
            "Opc.Ua.PLCopen.NodeSet2_V1.02.xml",
            "Opc.Ua.Safety.NodeSet2.xml");

    UANodeSet base = UANodeSetParser.parse(getNodeSetInputStream(inputStreams.get(0)));

    printNodeSetContents(base);

    for (int i = 1; i < inputStreams.size(); i++) {
      System.out.println("Merging " + inputStreams.get(i));

      UANodeSet incoming = UANodeSetParser.parse(getNodeSetInputStream(inputStreams.get(i)));

      UANodeSetMerger.merge(base, incoming);

      printNodeSetContents(base);
    }
  }

  private static void printNodeSetContents(UANodeSet nodeSet) {
    System.out.println("UANodeSet Contents");
    System.out.println("  Nodes: " + nodeSet.getUAObjectOrUAVariableOrUAMethod().size());
    System.out.println(
        "  Namespaces: "
            + nodeSet.getNamespaceUris().getUri().size()
            + " "
            + nodeSet.getNamespaceUris().getUri());
    System.out.println(
        "  Models: "
            + nodeSet.getModels().getModel().size()
            + " "
            + nodeSet.getModels().getModel().stream().map(ModelTableEntry::getModelUri).toList());

    int servers = 0;
    UriTable serverUris = nodeSet.getServerUris();
    if (serverUris != null) {
      servers = serverUris.getUri().size();
    }
    System.out.println("  Servers: " + servers);
  }

  @Test
  public void testNamespaceUriMerge() throws JAXBException {
    UANodeSet ns1 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.NodeSet2.xml"));
    UANodeSet ns2 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.Di.NodeSet2.xml"));
    UANodeSet ns3 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.Safety.NodeSet2.xml"));
    UANodeSet merged = UANodeSetMerger.merge(UANodeSetMerger.merge(ns1, ns2), ns3);

    List<String> mergedUris = merged.getNamespaceUris().getUri();
    assertEquals(0, mergedUris.indexOf(Namespaces.OPC_UA));
    assertEquals(1, mergedUris.indexOf("http://opcfoundation.org/UA/DI/"));
    assertEquals(2, mergedUris.indexOf("http://opcfoundation.org/UA/Safety"));

    merged
        .getUAObjectOrUAVariableOrUAMethod()
        .forEach(
            node -> {
              String nodeId = node.getNodeId();
              if (nodeId.startsWith("ns=")) {
                assertTrue(nodeId.startsWith("ns=1") || nodeId.startsWith("ns=2"));
              }
            });
  }

  @Test
  public void testModelUrisMerge() throws JAXBException {
    UANodeSet ns1 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.NodeSet2.xml"));
    UANodeSet ns2 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.Di.NodeSet2.xml"));
    UANodeSet ns3 =
        UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.PLCopen.NodeSet2_V1.02.xml"));

    UANodeSet merged = UANodeSetMerger.merge(ns1, ns2);
    assertEquals(2, merged.getModels().getModel().size());
    assertEquals(
        "http://opcfoundation.org/UA/", merged.getModels().getModel().get(0).getModelUri());
    assertEquals(
        "http://opcfoundation.org/UA/DI/", merged.getModels().getModel().get(1).getModelUri());

    UANodeSetMerger.merge(merged, ns3);
    assertEquals(3, merged.getModels().getModel().size());
    assertEquals(
        "http://opcfoundation.org/UA/", merged.getModels().getModel().get(0).getModelUri());
    assertEquals(
        "http://opcfoundation.org/UA/DI/", merged.getModels().getModel().get(1).getModelUri());
    assertEquals(
        "http://PLCopen.org/OpcUa/IEC61131-3/", merged.getModels().getModel().get(2).getModelUri());
  }

  @Test
  public void testAliasMerge() throws JAXBException {
    UANodeSet ns1 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.NodeSet2.xml"));
    UANodeSet ns2 = UANodeSetParser.parse(getNodeSetInputStream("Opc.Ua.Safety.NodeSet2.xml"));
    UANodeSet merged = UANodeSetMerger.merge(ns1, ns2);

    Map<String, String> mergedAliasMap =
        merged.getAliases().getAlias().stream()
            .collect(Collectors.toMap(NodeIdAlias::getAlias, NodeIdAlias::getValue));

    // Contains no duplicates
    assertEquals(merged.getAliases().getAlias().size(), mergedAliasMap.keySet().size());

    // All aliases from ns1 accounted for
    ns1.getAliases()
        .getAlias()
        .forEach(
            nia -> {
              String value = mergedAliasMap.get(nia.getAlias());
              assertEquals(nia.getValue(), value);
            });

    // All aliases from ns2 accounted for
    ns2.getAliases()
        .getAlias()
        .forEach(
            nia -> {
              String value = mergedAliasMap.get(nia.getAlias());
              assertEquals(nia.getValue(), value);
            });
  }

  private InputStream getNodeSetInputStream(String nodeSetName) {
    return getClass().getClassLoader().getResourceAsStream(nodeSetName);
  }
}
