package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAType;

/**
 * Indexed view of an OPC UA type hierarchy.
 *
 * <p>A {@link TypeInfoTree} is built around a root type, such as {@code BaseDataType} or {@code
 * BaseObjectType}, and exposes both hierarchy traversal and semantic lookup by NodeId. Concrete
 * subclasses select the root and JAXB type class; their {@code create} methods derive parent-child
 * relationships from a {@link NodeSetContext}'s combined {@code HasSubtype} references.
 *
 * @param <T> the JAXB type represented by each tree node.
 * @param <N> the public node wrapper used by this tree.
 */
public abstract class TypeInfoTree<T extends UAType, N extends TypeInfo<T>> {

  protected final Map<NodeId, N> typeInfos = new HashMap<>();

  protected final N rootTypeInfo;

  /**
   * Create a tree index from an already-linked root node.
   *
   * @param rootTypeInfo the root of the hierarchy.
   * @throws IllegalArgumentException if the linked nodes contain a cycle, a duplicate NodeId, or a
   *     child whose declared parent does not match its position.
   * @throws NullPointerException if {@code rootTypeInfo} is {@code null}.
   */
  public TypeInfoTree(N rootTypeInfo) {
    this.rootTypeInfo = Objects.requireNonNull(rootTypeInfo, "rootTypeInfo cannot be null");

    Set<TypeInfo<T>> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<TypeInfo<T>> indexed = Collections.newSetFromMap(new IdentityHashMap<>());
    index(rootTypeInfo, null, visiting, indexed);
  }

  private void index(
      TypeInfo<T> typeInfo,
      @Nullable TypeInfo<T> expectedParent,
      Set<TypeInfo<T>> visiting,
      Set<TypeInfo<T>> indexed) {

    if (!visiting.add(typeInfo)) {
      throw new IllegalArgumentException(
          "cycle detected in linked type hierarchy at " + typeInfo.getTypeNode().getNodeId());
    }
    if (indexed.contains(typeInfo)) {
      throw new IllegalArgumentException(
          "type is linked from multiple parents: " + typeInfo.getTypeNode().getNodeId());
    }
    if (typeInfo.getParent() != expectedParent) {
      throw new IllegalArgumentException(
          "type " + typeInfo.getTypeNode().getNodeId() + " is not linked from its declared parent");
    }

    NodeId typeId = NodeIdUtil.parse(typeInfo.getTypeNode().getNodeId());
    if (typeInfos.containsKey(typeId)) {
      throw new IllegalArgumentException("duplicate type NodeId in hierarchy: " + typeId);
    }

    //noinspection unchecked
    typeInfos.put(typeId, (N) typeInfo);
    for (TypeInfo<T> child : typeInfo.getChildren()) {
      index(child, typeInfo, visiting, indexed);
    }

    visiting.remove(typeInfo);
    indexed.add(typeInfo);
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
    return getTypeInfo(NodeIdUtil.parse(nodeId));
  }

  /**
   * Look up type information by semantic NodeId.
   *
   * @param nodeId the NodeId of the type to get.
   * @return the matching type information, or {@code null} when the type is unknown or disconnected
   *     from this tree's root.
   */
  public @Nullable N getTypeInfo(NodeId nodeId) {
    return typeInfos.get(nodeId);
  }

  /**
   * Get a direct type followed by every known supertype.
   *
   * <p>An unknown or disconnected type produces a singleton list containing {@code nodeId}. This
   * preserves exact-type comparisons while making no claim about unavailable ancestry.
   *
   * @param nodeId the NodeId of the direct type.
   * @return the direct type followed by known supertypes, from most specific to most general.
   */
  public List<NodeId> getTypeHierarchy(String nodeId) {
    return getTypeHierarchy(NodeIdUtil.parse(nodeId));
  }

  /**
   * Get a direct type followed by every known supertype.
   *
   * <p>An unknown or disconnected type produces a singleton list containing {@code nodeId}. This
   * preserves exact-type comparisons while making no claim about unavailable ancestry.
   *
   * @param nodeId the NodeId of the direct type.
   * @return the direct type followed by known supertypes, from most specific to most general.
   */
  public List<NodeId> getTypeHierarchy(NodeId nodeId) {
    N typeInfo = getTypeInfo(nodeId);
    if (typeInfo == null) {
      return List.of(nodeId);
    }

    List<NodeId> hierarchy = new ArrayList<>();
    TypeInfo<T> current = typeInfo;
    while (current != null) {
      hierarchy.add(NodeIdUtil.parse(current.getTypeNode().getNodeId()));
      current = current.getParent();
    }
    return List.copyOf(hierarchy);
  }

  /**
   * Check whether a type definition is abstract.
   *
   * @param nodeId the NodeId of the type to check.
   * @return {@code true} if the type exists in this tree and is abstract.
   */
  public boolean isAbstract(NodeId nodeId) {
    TypeInfo<T> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null && typeInfo.isAbstract();
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
   * <p>This is a strict subtype check; equal type and supertype NodeIds return {@code false}.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the supertype to check against.
   * @return {@code true} if the type is a descendant of the supertype.
   */
  public boolean isSubtypeOf(String nodeId, NodeId superNodeId) {
    return isSubtypeOf(NodeIdUtil.parse(nodeId), superNodeId);
  }

  /**
   * Check whether a type descends from another type in this hierarchy.
   *
   * <p>This is a strict subtype check; semantically equal NodeId strings return {@code false}.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the supertype to check against.
   * @return {@code true} if the type is a descendant of the supertype.
   */
  public boolean isSubtypeOf(String nodeId, String superNodeId) {
    return isSubtypeOf(NodeIdUtil.parse(nodeId), NodeIdUtil.parse(superNodeId));
  }

  /**
   * Check whether one type is a strict descendant of another type in this hierarchy.
   *
   * <p>A type is never a strict subtype of itself. Unknown or disconnected ancestry returns {@code
   * false}.
   *
   * @param nodeId the NodeId of the possible subtype.
   * @param superNodeId the NodeId of the possible supertype.
   * @return {@code true} if {@code nodeId} is a strict descendant of {@code superNodeId}.
   */
  public boolean isSubtypeOf(NodeId nodeId, NodeId superNodeId) {
    List<NodeId> hierarchy = getTypeHierarchy(nodeId);
    return hierarchy.size() > 1 && hierarchy.subList(1, hierarchy.size()).contains(superNodeId);
  }

  /**
   * Check whether a type is equal to or descends from another type.
   *
   * <p>Unlike {@link #isSubtypeOf(NodeId, NodeId)}, equal NodeIds return {@code true}, including
   * when the type's ancestry is unavailable.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the type or supertype to check against.
   * @return {@code true} if the NodeIds are equal or known ancestry contains the supertype.
   */
  public boolean isTypeOrSubtypeOf(NodeId nodeId, NodeId superNodeId) {
    return nodeId.equals(superNodeId) || isSubtypeOf(nodeId, superNodeId);
  }

  /**
   * Check whether a type is equal to or descends from another type.
   *
   * @param nodeId the NodeId of the type to check.
   * @param superNodeId the NodeId of the type or supertype to check against.
   * @return {@code true} if the NodeIds are equal or known ancestry contains the supertype.
   */
  public boolean isTypeOrSubtypeOf(String nodeId, String superNodeId) {
    return isTypeOrSubtypeOf(NodeIdUtil.parse(nodeId), NodeIdUtil.parse(superNodeId));
  }
}
