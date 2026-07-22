package com.digitalpetri.opcua.uanodeset.namespace;

/**
 * Selects the relationships eligible for a type registration.
 *
 * <p>This is registration-time policy. When a callback runs, {@link NodeMatch.Type#relationship()}
 * reports the actual {@link TypeRelationship}: {@link #EXACT} registrations produce only {@link
 * TypeRelationship#EXACT}, while {@link #INCLUDE_SUBTYPES} can produce either relationship.
 */
public enum TypeMatch {
  /** Match only instances whose direct type definition equals the registered type. */
  EXACT,

  /** Match instances whose direct type definition is the registered type or any subtype. */
  INCLUDE_SUBTYPES
}
