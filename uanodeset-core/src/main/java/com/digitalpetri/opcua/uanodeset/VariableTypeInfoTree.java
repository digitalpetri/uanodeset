package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.List;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UAVariableType;

/**
 * Public variable type hierarchy built from {@code BaseVariableType}.
 *
 * <p>The tree exposes variable type inheritance for namespace tools and code generators that need
 * the OPC UA type model without depending on any private Java naming policy. It follows normalized
 * {@code HasSubtype} references from a {@link NodeSetContext}.
 */
public class VariableTypeInfoTree extends TypeInfoTree<UAVariableType, VariableTypeInfo> {

  private static final boolean DEBUG = false;

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
   * @throws IllegalStateException if the context does not contain {@code BaseVariableType}.
   */
  public static VariableTypeInfoTree create(NodeSetContext context) {
    UANode node = context.getNode(NodeIdUtil.get(NodeIds.BaseVariableType));

    if (node instanceof UAVariableType variableTypeNode) {
      var rootTypeInfo = new VariableTypeInfo(null, variableTypeNode);

      addChildren(context, rootTypeInfo, 0);

      return new VariableTypeInfoTree(rootTypeInfo);
    } else {
      throw new IllegalStateException("UAVariableType BaseVariableType not found");
    }
  }

  private static void addChildren(NodeSetContext context, VariableTypeInfo typeInfo, int level) {
    List<Reference> references = context.getReferences(typeInfo.getTypeNode().getNodeId());

    List<UAVariableType> subTypes =
        references.stream()
            .filter(
                r -> r.isIsForward() && NodeIdUtil.equals(NodeIds.HasSubtype, r.getReferenceType()))
            .map(r -> context.getNode(r.getValue()))
            .filter(n -> n instanceof UAVariableType)
            .map(n -> (UAVariableType) n)
            .toList();

    for (UAVariableType variableType : subTypes) {
      if (DEBUG) {
        for (int i = 0; i < level; i++) {
          System.out.print("  ");
        }
        System.out.println(variableType.getBrowseName());
      }

      var child = new VariableTypeInfo(typeInfo, variableType);
      typeInfo.addChild(child);

      addChildren(context, child, level + 1);
    }
  }
}
