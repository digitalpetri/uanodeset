package com.digitalpetri.opcua.uanodeset;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.opcfoundation.ua.UAVariableType;

/**
 * Public variable type hierarchy built from {@code BaseVariableType}.
 *
 * <p>The tree exposes variable type inheritance for namespace tools and code generators that need
 * the OPC UA type model without depending on any private Java naming policy. It follows normalized
 * {@code HasSubtype} references from a {@link NodeSetContext}.
 */
public class VariableTypeInfoTree extends TypeInfoTree<UAVariableType, VariableTypeInfo> {

  /**
   * Create a variable type tree from a linked root node.
   *
   * <p>Most callers should use {@link #create(NodeSetContext)} so the hierarchy is built from a
   * normalized {@link NodeSetContext}.
   *
   * @param rootTypeInfo the {@code BaseVariableType} node.
   */
  public VariableTypeInfoTree(VariableTypeInfo rootTypeInfo) {
    super(rootTypeInfo);
  }

  /**
   * Build a variable type tree from a normalized node set context.
   *
   * @param context the context that supplies nodes and resolved references.
   * @return a variable type tree rooted at {@code BaseVariableType}.
   * @throws IllegalStateException if the context does not contain {@code BaseVariableType}, the
   *     known hierarchy contains a cycle, or a type declares multiple supertypes.
   */
  public static VariableTypeInfoTree create(NodeSetContext context) {
    VariableTypeInfo rootTypeInfo =
        TypeInfoTreeBuilder.build(
            context, NodeIds.BaseVariableType, UAVariableType.class, VariableTypeInfo::new);
    return new VariableTypeInfoTree(rootTypeInfo);
  }
}
