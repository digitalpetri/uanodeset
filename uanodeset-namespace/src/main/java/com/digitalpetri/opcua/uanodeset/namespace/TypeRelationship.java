package com.digitalpetri.opcua.uanodeset.namespace;

/**
 * Reports how an instance's direct type definition matched a type registration.
 *
 * <p>This is the per-invocation result exposed by {@link NodeMatch.Type#relationship()}, not the
 * registration policy selected with {@link TypeMatch}.
 */
public enum TypeRelationship {
  /** The instance's direct type definition is the registered type. */
  EXACT,

  /** The instance's direct type definition descends from the registered type. */
  SUBTYPE
}
