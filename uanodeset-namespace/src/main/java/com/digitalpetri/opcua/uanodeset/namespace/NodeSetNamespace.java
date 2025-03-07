package com.digitalpetri.opcua.uanodeset.namespace;

import java.util.Objects;
import org.eclipse.milo.opcua.sdk.server.Namespace;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public abstract class NodeSetNamespace extends NodeSetAddressSpace implements Namespace {

  private final String namespaceUri;

  public NodeSetNamespace(OpcUaServer server, String namespaceUri) {
    super(server);

    this.namespaceUri = namespaceUri;

    server.getNamespaceTable().add(namespaceUri);
  }

  @Override
  public String getNamespaceUri() {
    return namespaceUri;
  }

  @Override
  public UShort getNamespaceIndex() {
    return getServer().getNamespaceTable().getIndex(namespaceUri);
  }

  @Override
  protected boolean filterNamespace(String namespaceUri) {
    return Objects.equals(this.namespaceUri, namespaceUri);
  }
}
