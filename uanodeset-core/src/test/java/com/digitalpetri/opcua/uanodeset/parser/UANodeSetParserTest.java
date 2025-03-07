package com.digitalpetri.opcua.uanodeset.parser;

import java.io.InputStream;

import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opcfoundation.ua.UANodeSet;

class UANodeSetParserTest {

    @ParameterizedTest(name = "parse {0}")
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
    void testParse(String nodeSetFilename) throws JAXBException {
        InputStream nodeSetXml = getClass().getClassLoader().getResourceAsStream(nodeSetFilename);

        UANodeSet nodeSet = UANodeSetParser.parse(nodeSetXml);

        System.out.println(
            "Parsed " + nodeSetFilename + " and generated " +
                nodeSet.getUAObjectOrUAVariableOrUAMethod().size() + " nodes."
        );
    }

}
