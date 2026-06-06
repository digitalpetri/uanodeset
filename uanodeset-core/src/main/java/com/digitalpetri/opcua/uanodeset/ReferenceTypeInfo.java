package com.digitalpetri.opcua.uanodeset;

import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAReferenceType;

/**
 * Type hierarchy node for a {@link UAReferenceType}.
 *
 * <p>Reference type nodes expose the generic {@link TypeInfo} parent/children API with a
 * reference-type-specific parent return type so callers can traverse reference type inheritance
 * without casting at each step.
 */
public class ReferenceTypeInfo extends TypeInfo<UAReferenceType> {

  /**
   * Create a reference type hierarchy node.
   *
   * @param parent the parent reference type, or {@code null} for {@code References}.
   * @param typeNode the JAXB reference type node.
   */
  public ReferenceTypeInfo(@Nullable ReferenceTypeInfo parent, UAReferenceType typeNode) {
    super(parent, typeNode);
  }

  /**
   * Get the direct supertype as a reference-type-specific node.
   *
   * @return the parent reference type, or {@code null} for {@code References}.
   */
  @Override
  public @Nullable ReferenceTypeInfo getParent() {
    return (ReferenceTypeInfo) super.getParent();
  }
}
