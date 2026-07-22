package com.digitalpetri.opcua.uanodeset.namespace;

import com.digitalpetri.opcua.uanodeset.NodeSet;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
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

/**
 * Base address-space fragment that loads normalized NodeSet models during server startup.
 *
 * <p>Subclasses supply model streams, namespace filtering, and an encoding context. They may also
 * register post-load behavior through {@link #registerNodeBehaviors(NodeBehaviorRegistry)}. The
 * loader installs all selected nodes and references and finishes its value-decoding attempts before
 * those callbacks run. All supplied streams are closed after startup succeeds or fails.
 */
public abstract class NodeSetAddressSpace extends ManagedAddressSpaceFragmentWithLifecycle {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final AddressSpaceFilter addressSpaceFilter;
  private final SubscriptionModel subscriptionModel;

  /**
   * Create an address-space fragment whose NodeSet is loaded with the server lifecycle.
   *
   * @param server the server that owns the loaded nodes and subscriptions.
   */
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

  /**
   * Register callbacks that attach behavior after all selected nodes complete the load phases.
   *
   * <p>The default implementation does not register any callbacks. Overrides may register
   * type-specific, specific-node, and catch-all callbacks on {@code registry}. Callback failures
   * abort startup. Every invocation includes a {@link NodeMatch} describing which registration
   * matched the loaded node.
   *
   * @param registry the registry to populate before nodes are loaded.
   */
  protected void registerNodeBehaviors(NodeBehaviorRegistry registry) {}

  private void load() {
    List<InputStream> inputStreams = getNodeSetInputStreams();

    try {
      NodeSet nodeSet = NodeSet.load(inputStreams);

      nodeSet
          .getNodeSet()
          .getNamespaceUris()
          .getUri()
          .forEach(uri -> getServer().getNamespaceTable().add(uri));

      var behaviorRegistry = new NodeBehaviorRegistry();
      registerNodeBehaviors(behaviorRegistry);

      var loader =
          new NodeSetNodeLoader(
              nodeSet,
              getNodeContext(),
              getEncodingContext(),
              this::filterNamespace,
              behaviorRegistry);

      loader.loadNodes();
    } catch (JAXBException e) {
      throw new IllegalStateException("Error loading NodeSet", e);
    } finally {
      for (InputStream inputStream : inputStreams) {
        try {
          inputStream.close();
        } catch (IOException e) {
          logger.error("Error closing NodeSet input stream", e);
        }
      }
    }
  }
}
