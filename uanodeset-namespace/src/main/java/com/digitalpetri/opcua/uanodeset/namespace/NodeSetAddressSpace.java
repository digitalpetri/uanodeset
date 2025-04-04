package com.digitalpetri.opcua.uanodeset.namespace;

import com.digitalpetri.opcua.uanodeset.NodeSet;
import jakarta.xml.bind.JAXBException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragmentWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NodeSetAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AddressSpaceFilter addressSpaceFilter;
  private final SubscriptionModel subscriptionModel;

  public NodeSetAddressSpace(OpcUaServer server) {
    super(server);

    addressSpaceFilter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);
    subscriptionModel = new SubscriptionModel(server, this);

    getLifecycleManager().addStartupTask(this::load);
    getLifecycleManager().addLifecycle(subscriptionModel);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return addressSpaceFilter;
  }

  @Override
  public void onDataItemsCreated(List<DataItem> items) {
    subscriptionModel.onDataItemsCreated(items);
  }

  @Override
  public void onDataItemsModified(List<DataItem> items) {
    subscriptionModel.onDataItemsModified(items);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> items) {
    subscriptionModel.onDataItemsDeleted(items);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> items) {
    subscriptionModel.onMonitoringModeChanged(items);
  }

  /**
   * Get the {@link SubscriptionModel} used by this {@link NodeSetAddressSpace}.
   *
   * @return the {@link SubscriptionModel} used by this {@link NodeSetAddressSpace}.
   */
  public SubscriptionModel getSubscriptionModel() {
    return subscriptionModel;
  }

  /**
   * Filter out nodes belonging to namespaces that should not be loaded.
   *
   * <p>UANodeSet files can contain nodes and references to nodes from other namespaces, but
   * generally when loading a nodeset we're only interested in instantiating nodes from a single
   * namespace.
   *
   * @param namespaceUri the namespace URI of the node to filter.
   * @return true if the node should be loaded, false otherwise.
   */
  protected abstract boolean filterNamespace(String namespaceUri);

  /**
   * Get the {@link EncodingContext} to use when decoding/encoding values.
   *
   * <p>If this NodeSet is accompanied by code-generated classes for each type, then this should be
   * the Server's "static" EncodingContext. Otherwise, it should be the "dynamic" EncodingContext.
   *
   * @see OpcUaServer#getDynamicEncodingContext()
   * @see OpcUaServer#getStaticEncodingContext()
   * @return the {@link EncodingContext} to use.
   */
  protected abstract EncodingContext getEncodingContext();

  /**
   * Get the {@link InputStream}s to load NodeSet XML files from.
   *
   * @return a list of {@link InputStream}s to load NodeSet XML files from.
   */
  protected abstract List<InputStream> getNodeSetInputStreams();

  private void load() {
    List<InputStream> inputStreams = getNodeSetInputStreams();

    try {
      NodeSet nodeSet = NodeSet.load(inputStreams);

      nodeSet
          .getNodeSet()
          .getNamespaceUris()
          .getUri()
          .forEach(uri -> getServer().getNamespaceTable().add(uri));

      var loader =
          new NodeSetNodeLoader(
              nodeSet, getNodeContext(), getEncodingContext(), this::filterNamespace);

      loader.loadNodes();
    } catch (JAXBException e) {
      logger.error("Error loading NodeSet", e);
    } finally {
      for (InputStream inputStream : getNodeSetInputStreams()) {
        try {
          inputStream.close();
        } catch (Exception e) {
          logger.error("Error closing NodeSet input stream", e);
        }
      }
    }
  }
}
