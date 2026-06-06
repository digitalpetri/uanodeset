package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAType;

/**
 * Indexed view of an OPC UA type hierarchy.
 *
 * <p>A {@link TypeInfoTree} is built around a root type, such as {@code BaseDataType} or {@code
 * BaseObjectType}, and exposes both hierarchy traversal and direct lookup by NodeId string.
 * Concrete subclasses decide which root to use and how to collect descendants from a {@link
 * NodeSetContext}.
 *
 * @param <T> the JAXB type represented by each tree node.
 * @param <N> the public node wrapper used by this tree.
 */
public abstract class TypeInfoTree<T extends UAType, N extends TypeInfo<T>> {

  protected final Map<String, N> typeInfos = new HashMap<>();

  protected final N rootTypeInfo;

  /**
   * Create a tree index from an already-linked root node.
   *
   * @param rootTypeInfo the root of the hierarchy.
   */
  public TypeInfoTree(N rootTypeInfo) {
    this.rootTypeInfo = rootTypeInfo;

    //noinspection unchecked
    rootTypeInfo.traverse(
        typeInfo -> typeInfos.put(typeInfo.getTypeNode().getNodeId(), (N) typeInfo));
  }

  /**
   * Get the root type in this hierarchy.
   *
   * @return the root type information.
   */
  public N getRootTypeInfo() {
    return rootTypeInfo;
  }

  /**
   * Visit every type in this hierarchy in depth-first order.
   *
   * @param consumer the callback invoked once for each visited type.
   */
  public void traverse(Consumer<N> consumer) {
    rootTypeInfo.traverse(
        typeInfo -> {
          //noinspection unchecked
          consumer.accept((N) typeInfo);
        });
  }

  /**
   * Look up type information by NodeId.
   *
   * @param nodeId the NodeId of the type to get.
   * @return the matching type information, or {@code null} when the NodeId is not part of this
   *     hierarchy.
   */
  public @Nullable N getTypeInfo(String nodeId) {
    return typeInfos.get(nodeId);
  }

  /**
   * Check whether a type definition is abstract.
   *
   * @param nodeId the NodeId of the type to check.
   * @return {@code true} if the type exists in this tree and is abstract.
   */
  public boolean isAbstract(NodeId nodeId) {
    return isAbstract(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a type definition is abstract.
   *
   * @param nodeId the NodeId of the type to check.
   * @return {@code true} if the type exists in this tree and is abstract.
   */
  public boolean isAbstract(String nodeId) {
    TypeInfo<T> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null && typeInfo.isAbstract();
  }

  /**
   * Check whether a type descends from another type in this hierarchy.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the supertype to check against.
   * @return {@code true} if the type is a descendant of the supertype.
   */
  public boolean isSubtypeOf(String nodeId, NodeId superNodeId) {
    return isSubtypeOf(nodeId, NodeIdUtil.get(superNodeId));
  }

  /**
   * Check whether a type descends from another type in this hierarchy.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the supertype to check against.
   * @return {@code true} if the type is a descendant of the supertype.
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
