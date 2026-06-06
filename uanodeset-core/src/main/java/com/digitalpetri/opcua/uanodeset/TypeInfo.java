package com.digitalpetri.opcua.uanodeset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.UAType;

/**
 * One node in a {@link TypeInfoTree} built from OPC UA {@code HasSubtype} references.
 *
 * <p>A {@link TypeInfo} keeps the original JAXB {@link UAType} node together with its parent and
 * children in the type hierarchy. Concrete subclasses preserve the specific JAXB type, such as
 * {@link org.opcfoundation.ua.UADataType}, {@link org.opcfoundation.ua.UAObjectType}, {@link
 * org.opcfoundation.ua.UAVariableType}, or {@link org.opcfoundation.ua.UAReferenceType}, while
 * sharing traversal and parent/child behavior.
 *
 * @param <T> the kind of OPC UA type represented by this tree node.
 */
public abstract class TypeInfo<T extends UAType> {

  private final List<TypeInfo<T>> children = new ArrayList<>();

  private final @Nullable TypeInfo<T> parent;
  private final T typeNode;

  /**
   * Create a tree node for a type in a {@link TypeInfoTree}.
   *
   * @param parent the parent type, or {@code null} when this is the root type.
   * @param typeNode the JAXB node that defines the OPC UA type.
   */
  protected TypeInfo(@Nullable TypeInfo<T> parent, T typeNode) {
    this.parent = parent;
    this.typeNode = typeNode;
  }

  /**
   * Get the direct supertype in this hierarchy.
   *
   * @return the parent type, or {@code null} when this is the root type.
   */
  public @Nullable TypeInfo<T> getParent() {
    return parent;
  }

  /**
   * Get the JAXB node that defines this OPC UA type.
   *
   * @return the JAXB type node.
   */
  public T getTypeNode() {
    return typeNode;
  }

  /**
   * Check whether this OPC UA type is abstract.
   *
   * @return {@code true} if the type definition is abstract.
   */
  public boolean isAbstract() {
    return typeNode.isIsAbstract();
  }

  /**
   * Add a direct subtype to this node.
   *
   * <p>Tree builders call this while walking {@code HasSubtype} references. Normal callers should
   * treat completed trees as read-only.
   *
   * @param child the direct subtype to add.
   */
  public void addChild(TypeInfo<T> child) {
    children.add(child);
  }

  /**
   * Get the direct subtypes of this type.
   *
   * @return the direct child type nodes.
   */
  public List<TypeInfo<T>> getChildren() {
    return children;
  }

  /**
   * Visit this node and all descendants in depth-first order.
   *
   * @param consumer the callback invoked once for each visited type.
   */
  public void traverse(Consumer<TypeInfo<T>> consumer) {
    traverse(this, (n, depth) -> consumer.accept(n), 0, null);
  }

  private static <T extends UAType> void traverse(
      TypeInfo<T> tree,
      BiConsumer<TypeInfo<T>, Integer> c,
      int depth,
      @Nullable Comparator<TypeInfo<T>> comparator) {

    c.accept(tree, depth);

    if (comparator != null) {
      tree.children.stream().sorted(comparator).forEach(t -> traverse(t, c, depth + 1, comparator));
    } else {
      tree.children.forEach(t -> traverse(t, c, depth + 1, null));
    }
  }
}
