package com.digitalpetri.opcua.uanodeset;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.opcfoundation.ua.UAObjectType;

/**
 * Public object type hierarchy built from {@code BaseObjectType}.
 *
 * <p>The tree is useful when a tool needs to generate, validate, or inspect object type inheritance
 * independently of generated Java naming concerns. It follows normalized {@code HasSubtype}
 * references from a {@link NodeSetContext}.
 */
public class ObjectTypeInfoTree extends TypeInfoTree<UAObjectType, ObjectTypeInfo> {

  /**
   * Create an object type tree from a linked root node.
   *
   * <p>Most callers should use {@link #create(NodeSetContext)} so the hierarchy is built from a
   * normalized {@link NodeSetContext}.
   *
   * @param rootTypeInfo the {@code BaseObjectType} node.
   */
  public ObjectTypeInfoTree(ObjectTypeInfo rootTypeInfo) {
    super(rootTypeInfo);
  }

  /**
   * Build an object type tree from a normalized node set context.
   *
   * @param context the context that supplies nodes and resolved references.
   * @return an object type tree rooted at {@code BaseObjectType}.
   * @throws IllegalStateException if the context does not contain {@code BaseObjectType}, the known
   *     hierarchy contains a cycle, or a type declares multiple supertypes.
   */
  public static ObjectTypeInfoTree create(NodeSetContext context) {
    ObjectTypeInfo rootTypeInfo =
        TypeInfoTreeBuilder.build(
            context, NodeIds.BaseObjectType, UAObjectType.class, ObjectTypeInfo::new);
    return new ObjectTypeInfoTree(rootTypeInfo);
  }
}
