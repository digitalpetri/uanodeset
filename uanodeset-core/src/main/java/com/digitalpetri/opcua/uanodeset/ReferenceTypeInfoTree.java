package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UAReferenceType;

/**
 * Public reference type hierarchy built from {@code References}.
 *
 * <p>The tree exposes the OPC UA reference type inheritance model used to classify and inspect
 * references. It follows normalized {@code HasSubtype} references from a {@link NodeSetContext} and
 * does not include any code-generation-specific naming policy.
 */
public class ReferenceTypeInfoTree extends TypeInfoTree<UAReferenceType, ReferenceTypeInfo> {

  private static final boolean DEBUG = false;

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
   * @throws IllegalStateException if the context does not contain {@code References}.
   */
  public static ReferenceTypeInfoTree create(NodeSetContext context) {
    UANode node = context.getNode(NodeIdUtil.get(NodeIds.References));

    if (node instanceof UAReferenceType referenceTypeNode) {
      var rootTypeInfo = new ReferenceTypeInfo(null, referenceTypeNode);

      addChildren(context, rootTypeInfo, 0);

      return new ReferenceTypeInfoTree(rootTypeInfo);
    } else {
      throw new IllegalStateException("UAReferenceType References not found");
    }
  }

  private static void addChildren(NodeSetContext context, ReferenceTypeInfo typeInfo, int level) {
    List<Reference> references = context.getReferences(typeInfo.getTypeNode().getNodeId());

    List<UAReferenceType> subTypes =
        references.stream()
            .filter(
                r -> r.isIsForward() && NodeIdUtil.equals(NodeIds.HasSubtype, r.getReferenceType()))
            .map(r -> context.getNode(r.getValue()))
            .filter(n -> n instanceof UAReferenceType)
            .map(n -> (UAReferenceType) n)
            .toList();

    for (UAReferenceType referenceType : subTypes) {
      if (DEBUG) {
        for (int i = 0; i < level; i++) {
          System.out.print("  ");
        }
        System.out.println(referenceType.getBrowseName());
      }

      var child = new ReferenceTypeInfo(typeInfo, referenceType);
      typeInfo.addChild(child);

      addChildren(context, child, level + 1);
    }
  }
}
