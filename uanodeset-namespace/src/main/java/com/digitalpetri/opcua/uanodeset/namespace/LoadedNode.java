package com.digitalpetri.opcua.uanodeset.namespace;

import com.digitalpetri.opcua.uanodeset.NodeSet;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.opcfoundation.ua.UANode;

/**
 * A Milo node after all loader phases have completed, together with the parsed NodeSet state from
 * which it was created.
 *
 * <p>{@link #node()} and {@link #typeDefinitionId()} use the server's namespace indexes. The JAXB
 * {@link #sourceNode()} and its {@link #nodeSet()} retain indexes from the merged model; use the
 * Milo node or the reindexed type definition when comparing against registry keys. The separate
 * {@link NodeMatch} argument supplied to a {@link NodeLoadedCallback} explains why a particular
 * callback received this node. A completed load phase does not imply successful value decoding;
 * decode failures are logged and leave the Variable's default no-value status.
 *
 * @param node the Milo node, already present in the node manager.
 * @param typeDefinitionId the reindexed direct type definition for an Object or Variable instance,
 *     or {@link NodeId#NULL_VALUE} for other node classes.
 * @param sourceNode the normalized JAXB node from the parsed NodeSet.
 * @param nodeSet the merged and normalized NodeSet.
 * @param nodeContext the Milo node context used to load the node.
 */
public record LoadedNode(
    UaNode node,
    NodeId typeDefinitionId,
    UANode sourceNode,
    NodeSet nodeSet,
    UaNodeContext nodeContext) {}
