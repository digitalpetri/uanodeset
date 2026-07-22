package com.digitalpetri.opcua.uanodeset.namespace;

import java.util.Objects;
import org.eclipse.milo.opcua.sdk.server.Namespace;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public abstract class NodeSetNamespace extends NodeSetAddressSpace implements Namespace {

  private final String namespaceUri;
  private final UShort namespaceIndex;

  public NodeSetNamespace(OpcUaServer server, String namespaceUri) {
    super(server);

    this.namespaceUri = namespaceUri;
    namespaceIndex = server.getNamespaceTable().add(namespaceUri);
  }

  @Override
  public String getNamespaceUri() {
    return namespaceUri;
  }

  @Override
  public UShort getNamespaceIndex() {
    return namespaceIndex;
  }

  @Override
  protected boolean filterNamespace(String namespaceUri) {
    return Objects.equals(this.namespaceUri, namespaceUri);
  }
}
