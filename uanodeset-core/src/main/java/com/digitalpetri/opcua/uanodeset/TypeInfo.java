package com.digitalpetri.opcua.uanodeset;

import org.jetbrains.annotations.Nullable;
import org.opcfoundation.ua.UAType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A tree node in the {@link TypeInfoTree} for some {@link UAType}:
 * <ul>
 *     <li>{@link org.opcfoundation.ua.UADataType}
 *     <li>{@link org.opcfoundation.ua.UAObjectType}
 *     <li>{@link org.opcfoundation.ua.UAVariableType}
 *     <li>{@link org.opcfoundation.ua.UAReferenceType}
 * </ul>
 *
 * @param <T> the {@link UAType} contained in this tree node.
 */
public abstract class TypeInfo<T extends UAType> {

  private final List<TypeInfo<T>> children = new ArrayList<>();

  private final @Nullable TypeInfo<T> parent;
  private final T typeNode;

  protected TypeInfo(@Nullable TypeInfo<T> parent, T typeNode) {
    this.parent = parent;
    this.typeNode = typeNode;
  }

  /**
   * Get the parent {@link TypeInfo} node, if it exists.
   *
   * @return the parent {@link TypeInfo} node, or {@code null} if this node is the root.
   */
  public @Nullable TypeInfo<T> getParent() {
    return parent;
  }

  /**
   * Get the {@link UAType} node.
   *
   * @return the {@link UAType} node.
   */
  public T getTypeNode() {
    return typeNode;
  }

  /**
   * Get whether {@link UAType} contained by this {@link TypeInfo} represents an abstract type.
   *
   * @return {@code true} if the type is abstract, {@code false} otherwise.
   */
  public boolean isAbstract() {
    return typeNode.isIsAbstract();
  }

  /**
   * Add a child {@link TypeInfo} node to this node.
   *
   * @param child the child {@link TypeInfo} node to add.
   */
  public void addChild(TypeInfo<T> child) {
    children.add(child);
  }

  /**
   * Get the children of this {@link TypeInfo} node.
   *
   * @return the children of this {@link TypeInfo} node.
   */
  public List<TypeInfo<T>> getChildren() {
    return children;
  }

  public void traverse(Consumer<TypeInfo<T>> consumer) {
    traverse(this, (n, depth) -> consumer.accept(n), 0, null);
  }

  private static <T extends UAType> void traverse(
      TypeInfo<T> tree,
      BiConsumer<TypeInfo<T>, Integer> c,
      int depth,
      @Nullable Comparator<TypeInfo<T>> comparator
  ) {

    c.accept(tree, depth);

    if (comparator != null) {
      tree.children.stream()
          .sorted(comparator)
          .forEach(t -> traverse(t, c, depth + 1, comparator));
    } else {
      tree.children.forEach(t -> traverse(t, c, depth + 1, null));
    }
  }

}
