package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UAObjectType;

/**
 * Public object type hierarchy built from {@code BaseObjectType}.
 *
 * <p>The tree is useful when a tool needs to generate, validate, or inspect object type inheritance
 * independently of generated Java naming concerns. It follows normalized {@code HasSubtype}
 * references from a {@link NodeSetContext}.
 */
public class ObjectTypeInfoTree extends TypeInfoTree<UAObjectType, ObjectTypeInfo> {

  private static final boolean DEBUG = false;

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
   * @throws IllegalStateException if the context does not contain {@code BaseObjectType}.
   */
  public static ObjectTypeInfoTree create(NodeSetContext context) {
    UANode node = context.getNode(NodeIdUtil.get(NodeIds.BaseObjectType));

    if (node instanceof UAObjectType objectTypeNode) {
      var rootTypeInfo = new ObjectTypeInfo(null, objectTypeNode);

      addChildren(context, rootTypeInfo, 0);

      return new ObjectTypeInfoTree(rootTypeInfo);
    } else {
      throw new IllegalStateException("UAObjectType BaseObjectType not found");
    }
  }

  private static void addChildren(NodeSetContext context, ObjectTypeInfo typeInfo, int level) {
    List<Reference> references = context.getReferences(typeInfo.getTypeNode().getNodeId());

    List<UAObjectType> subTypes =
        references.stream()
            .filter(
                r -> r.isIsForward() && NodeIdUtil.equals(NodeIds.HasSubtype, r.getReferenceType()))
            .map(r -> context.getNode(r.getValue()))
            .filter(n -> n instanceof UAObjectType)
            .map(n -> (UAObjectType) n)
            .toList();

    for (UAObjectType objectType : subTypes) {
      if (DEBUG) {
        for (int i = 0; i < level; i++) {
          System.out.print("  ");
        }
        System.out.println(objectType.getBrowseName());
      }

      var child = new ObjectTypeInfo(typeInfo, objectType);
      typeInfo.addChild(child);

      addChildren(context, child, level + 1);
    }
  }
}
