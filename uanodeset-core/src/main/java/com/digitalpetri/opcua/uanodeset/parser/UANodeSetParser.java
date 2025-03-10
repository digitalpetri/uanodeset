package com.digitalpetri.opcua.uanodeset.parser;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.InputStream;
import org.opcfoundation.ua.ObjectFactory;
import org.opcfoundation.ua.UANodeSet;

public final class UANodeSetParser {

  private UANodeSetParser() {}

  /**
   * Read and parse an XML document conforming to the <a
   * href="http://opcfoundation.org/UA/2011/03/UANodeSet.xsd">UANodeSet</a> schema from an {@link
   * InputStream}.
   *
   * @param inputStream the {@link InputStream} to read from.
   * @return a {@link UANodeSet}.
   * @throws JAXBException if an error occurs while unmarshalling.
   */
  public static UANodeSet parse(InputStream inputStream) throws JAXBException {
    JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);

    return (UANodeSet) jaxbContext.createUnmarshaller().unmarshal(inputStream);
  }
}
