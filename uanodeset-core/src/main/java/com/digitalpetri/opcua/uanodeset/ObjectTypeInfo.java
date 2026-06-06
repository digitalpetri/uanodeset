package com.digitalpetri.opcua.uanodeset;

import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAObjectType;

/**
 * Type hierarchy node for a {@link UAObjectType}.
 *
 * <p>Object type nodes expose the generic {@link TypeInfo} parent/children API with an
 * object-type-specific parent return type so callers can traverse object type inheritance without
 * casting at each step.
 */
public class ObjectTypeInfo extends TypeInfo<UAObjectType> {

  /**
   * Create an object type hierarchy node.
   *
   * @param parent the parent object type, or {@code null} for {@code BaseObjectType}.
   * @param typeNode the JAXB object type node.
   */
  public ObjectTypeInfo(@Nullable ObjectTypeInfo parent, UAObjectType typeNode) {
    super(parent, typeNode);
  }

  /**
   * Get the direct supertype as an object-type-specific node.
   *
   * @return the parent object type, or {@code null} for {@code BaseObjectType}.
   */
  @Override
  public @Nullable ObjectTypeInfo getParent() {
    return (ObjectTypeInfo) super.getParent();
  }
}
