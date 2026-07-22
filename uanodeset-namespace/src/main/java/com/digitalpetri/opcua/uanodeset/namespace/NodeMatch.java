package com.digitalpetri.opcua.uanodeset.namespace;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Identifies the registry entry that caused one post-load callback invocation.
 *
 * <p>The match is per invocation rather than per loaded node: one {@link LoadedNode} can produce a
 * {@link Type}, {@link SpecificNode}, and {@link AnyNode} invocation when it satisfies all three
 * registrations. All NodeIds exposed by a match use server namespace indexes, the same index space
 * as {@link LoadedNode#node()}{@code .getNodeId()} and {@link LoadedNode#typeDefinitionId()}.
 */
public sealed interface NodeMatch
    permits NodeMatch.Type, NodeMatch.SpecificNode, NodeMatch.AnyNode {

  /**
   * A match against an Object or Variable instance's type definition.
   *
   * <p>{@code registrationMode} is the policy selected when registering the callback, while {@code
   * relationship} is the result for this instance. {@link TypeMatch#INCLUDE_SUBTYPES} may therefore
   * produce either {@link TypeRelationship#EXACT} or {@link TypeRelationship#SUBTYPE}; {@link
   * TypeMatch#EXACT} produces only an exact relationship.
   *
   * @param registeredTypeId the server-space type definition used to register the callback.
   * @param registrationMode the configured direct-type or subtype matching mode.
   * @param relationship the loaded instance's relationship to the registered type.
   */
  record Type(NodeId registeredTypeId, TypeMatch registrationMode, TypeRelationship relationship)
      implements NodeMatch {}

  /**
   * A match against a callback registered for one node.
   *
   * <p>{@code registeredNodeId} is equal to the loaded Milo node's NodeId for this invocation.
   *
   * @param registeredNodeId the server-space NodeId used to register the callback.
   */
  record SpecificNode(NodeId registeredNodeId) implements NodeMatch {}

  /** A match against a catch-all callback registered for every node selected and created. */
  record AnyNode() implements NodeMatch {}
}
