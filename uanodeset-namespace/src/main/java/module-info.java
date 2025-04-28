module com.digitalpetri.opcua.uanodeset.namespace {
  exports com.digitalpetri.opcua.uanodeset.namespace;

  requires transitive com.digitalpetri.opcua.uanodeset.core;
  requires transitive org.eclipse.milo.opcua.stack.core;
  requires transitive org.eclipse.milo.opcua.sdk.server;
  
  requires org.eclipse.milo.opcua.stack.encoding.xml;
  requires org.eclipse.milo.opcua.sdk.core;
  
  requires jakarta.xml.bind;
  requires org.jspecify;
  requires org.slf4j;
}
