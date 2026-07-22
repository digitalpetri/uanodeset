package com.digitalpetri.opcua.uanodeset;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.opcfoundation.ua.UAReferenceType;

/**
 * Public reference type hierarchy built from {@code References}.
 *
 * <p>The tree exposes the OPC UA reference type inheritance model used to classify and inspect
 * references. It follows normalized {@code HasSubtype} references from a {@link NodeSetContext} and
 * does not include any code-generation-specific naming policy.
 */
public class ReferenceTypeInfoTree extends TypeInfoTree<UAReferenceType, ReferenceTypeInfo> {

  /**
   * Create a reference type tree from a linked root node.
   *
   * <p>Most callers should use {@link #create(NodeSetContext)} so the hierarchy is built from a
   * normalized {@link NodeSetContext}.
   *
   * @param rootTypeInfo the {@code References} node.
   */
  public ReferenceTypeInfoTree(ReferenceTypeInfo rootTypeInfo) {
    super(rootTypeInfo);
  }

  /**
   * Build a reference type tree from a normalized node set context.
   *
   * @param context the context that supplies nodes and resolved references.
   * @return a reference type tree rooted at {@code References}.
   * @throws IllegalStateException if the context does not contain {@code References}, the known
   *     hierarchy contains a cycle, or a type declares multiple supertypes.
   */
  public static ReferenceTypeInfoTree create(NodeSetContext context) {
    ReferenceTypeInfo rootTypeInfo =
        TypeInfoTreeBuilder.build(
            context, NodeIds.References, UAReferenceType.class, ReferenceTypeInfo::new);
    return new ReferenceTypeInfoTree(rootTypeInfo);
  }
}
