module com.digitalpetri.opcua.uanodeset.core {
  exports com.digitalpetri.opcua.uanodeset;
  exports org.opcfoundation.ua;

  // Allow the JAXB implementation (jakarta.xml.bind) to reflectively
  // access the JAXB-generated classes in this package at runtime.
  opens org.opcfoundation.ua to
      jakarta.xml.bind;

  exports com.digitalpetri.opcua.uanodeset.parser;

  requires jakarta.xml.bind;
  requires org.glassfish.jaxb.runtime;
  requires transitive org.eclipse.milo.opcua.stack.core;
  requires org.jspecify;
}
