package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAType;

public abstract class TypeInfoTree<T extends UAType, N extends TypeInfo<T>> {

  protected final Map<String, N> typeInfos = new HashMap<>();

  protected final N rootTypeInfo;

  public TypeInfoTree(N rootTypeInfo) {
    this.rootTypeInfo = rootTypeInfo;

    //noinspection unchecked
    rootTypeInfo.traverse(
        typeInfo -> typeInfos.put(typeInfo.getTypeNode().getNodeId(), (N) typeInfo));
  }

  /**
   * Get the root {@link TypeInfo} node.
   *
   * @return the root {@link TypeInfo} node.
   */
  public N getRootTypeInfo() {
    return rootTypeInfo;
  }

  /**
   * Get the {@link TypeInfo} for the Node identified by {@code nodeId}.
   *
   * @param nodeId the NodeId of the type to get.
   * @return the {@link TypeInfo} for the Node identified by {@code nodeId}, or {@code null} if no
   *     such type exists.
   */
  public @Nullable N getTypeInfo(String nodeId) {
    return typeInfos.get(nodeId);
  }

  /**
   * Check whether a type is a subtype of another type.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the super type to check against.
   * @return {@code true} if the type is a subtype of the super type, {@code false} otherwise.
   */
  public boolean isSubtypeOf(String nodeId, NodeId superNodeId) {
    return isSubtypeOf(nodeId, NodeIdUtil.get(superNodeId));
  }

  /**
   * Check whether a type is a subtype of another type.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the super type to check against.
   * @return {@code true} if the type is a subtype of the super type, {@code false} otherwise.
   */
  public boolean isSubtypeOf(String nodeId, String superNodeId) {
    TypeInfo<T> typeInfo = getTypeInfo(nodeId);
    if (typeInfo == null) {
      return false;
    }

    TypeInfo<T> parentTypeInfo = typeInfo.getParent();
    if (parentTypeInfo == null) {
      return false;
    }

    String parentNodeId = parentTypeInfo.getTypeNode().getNodeId();

    return parentNodeId.equals(superNodeId) || isSubtypeOf(parentNodeId, superNodeId);
  }
}
