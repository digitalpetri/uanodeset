/**
 * Materializes normalized OPC UA NodeSet models as nodes in a Milo server address space.
 *
 * <h2>Entry points</h2>
 *
 * <p>{@link com.digitalpetri.opcua.uanodeset.namespace.NodeSetNamespace} is the usual base class
 * for a single namespace backed by NodeSet XML. It supplies namespace filtering while its parent,
 * {@link com.digitalpetri.opcua.uanodeset.namespace.NodeSetAddressSpace}, integrates loading and
 * subscriptions with the server lifecycle. Callers that already own a parsed {@link
 * com.digitalpetri.opcua.uanodeset.NodeSet} can use {@link
 * com.digitalpetri.opcua.uanodeset.namespace.NodeSetNodeLoader} directly.
 *
 * <h2>Load lifecycle</h2>
 *
 * <p>During address-space startup, the input NodeSets are parsed and merged with the OPC UA base
 * model, their namespace URIs are added to the server namespace table, and selected nodes are
 * loaded in phases. References are installed before type and instance nodes; datatype definitions
 * and dynamic codecs are then registered; Variable and VariableType values are decoded last. Type
 * definitions and ancestry are resolved by {@link com.digitalpetri.opcua.uanodeset.NodeSet} from
 * the merged model. The loader reindexes that model-space hierarchy only when dispatching
 * callbacks; Milo's runtime type trees do not decide behavior matches.
 *
 * <p>Post-load behavior is the final phase. {@link
 * com.digitalpetri.opcua.uanodeset.namespace.NodeBehaviorRegistry} registrations receive {@link
 * com.digitalpetri.opcua.uanodeset.namespace.LoadedNode} contexts only after all selected nodes are
 * installed and value-decoding attempts are complete. Callbacks run synchronously on the startup
 * path and in merged NodeSet order. Type registrations match an exact direct type or include known
 * subtypes; when a companion hierarchy is unavailable, only the direct type is reported.
 * Specific-node registrations select one server-space NodeId, while catch-all registrations also
 * see type nodes, Methods, and Views.
 *
 * <p>Each invocation includes a {@link com.digitalpetri.opcua.uanodeset.namespace.NodeMatch}:
 * {@link com.digitalpetri.opcua.uanodeset.namespace.NodeMatch.Type} reports the configured match
 * mode and actual exact-or-subtype relationship, {@link
 * com.digitalpetri.opcua.uanodeset.namespace.NodeMatch.SpecificNode} identifies an exact NodeId
 * registration, and {@link com.digitalpetri.opcua.uanodeset.namespace.NodeMatch.AnyNode} identifies
 * a catch-all registration. One loaded node can therefore produce multiple invocations when it
 * satisfies more than one registration.
 *
 * <h2>Extension and runtime boundaries</h2>
 *
 * <p>Override {@link
 * com.digitalpetri.opcua.uanodeset.namespace.NodeSetAddressSpace#registerNodeBehaviors(com.digitalpetri.opcua.uanodeset.namespace.NodeBehaviorRegistry)}
 * to attach method handlers, condition or alarm behavior, or application-specific initialization.
 * Registry keys are server-space NodeIds, not the merged-model namespace indexes retained by {@code
 * NodeSet}. The loader provides the reindexed direct type definition through {@link
 * com.digitalpetri.opcua.uanodeset.namespace.LoadedNode#typeDefinitionId()}.
 *
 * <h2>Failure and ownership</h2>
 *
 * <p>XML parsing failures and callback exceptions abort startup. When type callbacks are
 * registered, malformed known ObjectType or VariableType hierarchies also abort matching.
 * Individual Variable value decode failures are logged and leave the node's default no-value status
 * so that unrelated nodes can still load. {@code NodeSetAddressSpace} closes the input streams
 * returned by {@link
 * com.digitalpetri.opcua.uanodeset.namespace.NodeSetAddressSpace#getNodeSetInputStreams()} after
 * loading; direct users of {@code NodeSetNodeLoader} retain ownership of their parsed model and any
 * streams used to create it.
 */
@NullMarked
package com.digitalpetri.opcua.uanodeset.namespace;

import org.jspecify.annotations.NullMarked;
