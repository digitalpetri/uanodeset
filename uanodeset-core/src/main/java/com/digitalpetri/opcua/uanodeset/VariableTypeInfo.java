package com.digitalpetri.opcua.uanodeset;

import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAVariableType;

/**
 * Type hierarchy node for a {@link UAVariableType}.
 *
 * <p>Variable type nodes expose the generic {@link TypeInfo} parent/children API with a
 * variable-type-specific parent return type so callers can traverse variable type inheritance
 * without casting at each step.
 */
public class VariableTypeInfo extends TypeInfo<UAVariableType> {

  /**
   * Create a variable type hierarchy node.
   *
   * @param parent the parent variable type, or {@code null} for {@code BaseVariableType}.
   * @param typeNode the JAXB variable type node.
   */
  public VariableTypeInfo(@Nullable VariableTypeInfo parent, UAVariableType typeNode) {
    super(parent, typeNode);
  }

  /**
   * Get the direct supertype as a variable-type-specific node.
   *
   * @return the parent variable type, or {@code null} for {@code BaseVariableType}.
   */
  @Override
  public @Nullable VariableTypeInfo getParent() {
    return (VariableTypeInfo) super.getParent();
  }
}
