package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UAType;

/**
 * Builds a validated, rooted {@link TypeInfoTree} model from normalized {@code HasSubtype}
 * relationships.
 *
 * <p>The builder treats forward and inverse declarations identically through {@link
 * NodeSetContext#getReferences(NodeId)}, links only parents that are present in the context, and
 * rejects cycles or multiple declared supertypes before constructing the public tree. Types whose
 * known parent is absent remain disconnected from the standard root and are therefore not assigned
 * speculative ancestry.
 */
final class TypeInfoTreeBuilder {

  static <T extends UAType, N extends TypeInfo<T>> N build(
      NodeSetContext context,
      NodeId rootTypeId,
      Class<T> typeClass,
      TypeInfoFactory<T, N> factory) {

    UANode rootNode = context.getNode(rootTypeId);
    if (!typeClass.isInstance(rootNode)) {
      throw new IllegalStateException(typeClass.getSimpleName() + " " + rootTypeId + " not found");
    }

    Map<NodeId, T> typeNodes = collectTypeNodes(context, typeClass);
    Map<NodeId, NodeId> parentByChild = collectKnownParents(context, typeNodes);

    if (parentByChild.containsKey(rootTypeId)) {
      throw new IllegalStateException(
          "root type " + rootTypeId + " declares supertype " + parentByChild.get(rootTypeId));
    }

    validateAcyclic(parentByChild);

    Map<NodeId, List<T>> childrenByParent = new LinkedHashMap<>();
    for (T typeNode : typeNodes.values()) {
      NodeId typeId = NodeIdUtil.parse(typeNode.getNodeId());
      NodeId parentId = parentByChild.get(typeId);
      if (parentId != null) {
        childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(typeNode);
      }
    }

    T rootTypeNode = typeClass.cast(rootNode);
    N rootTypeInfo = factory.create(null, rootTypeNode);
    addChildren(rootTypeInfo, rootTypeId, childrenByParent, factory);
    return rootTypeInfo;
  }

  private static <T extends UAType> Map<NodeId, T> collectTypeNodes(
      NodeSetContext context, Class<T> typeClass) {

    Map<NodeId, T> typeNodes = new LinkedHashMap<>();
    for (UANode node : context.getNodeSet().getUAObjectOrUAVariableOrUAMethod()) {
      if (typeClass.isInstance(node)) {
        T typeNode = typeClass.cast(node);
        typeNodes.put(NodeIdUtil.parse(typeNode.getNodeId()), typeNode);
      }
    }
    return typeNodes;
  }

  private static <T extends UAType> Map<NodeId, NodeId> collectKnownParents(
      NodeSetContext context, Map<NodeId, T> typeNodes) {

    Map<NodeId, NodeId> parentByChild = new LinkedHashMap<>();

    for (Map.Entry<NodeId, T> entry : typeNodes.entrySet()) {
      NodeId typeId = entry.getKey();
      Set<NodeId> declaredParentIds = new LinkedHashSet<>();

      for (Reference reference : context.getReferences(typeId)) {
        if (!reference.isIsForward()
            && NodeIdUtil.equals(NodeIds.HasSubtype, reference.getReferenceType())) {
          declaredParentIds.add(NodeIdUtil.parse(reference.getValue()));
        }
      }

      if (declaredParentIds.size() > 1) {
        throw new IllegalStateException(
            "type " + typeId + " declares multiple supertypes: " + declaredParentIds);
      }

      if (!declaredParentIds.isEmpty()) {
        NodeId parentId = declaredParentIds.iterator().next();
        if (typeNodes.containsKey(parentId)) {
          parentByChild.put(typeId, parentId);
        }
      }
    }

    return parentByChild;
  }

  private static void validateAcyclic(Map<NodeId, NodeId> parentByChild) {
    Map<NodeId, VisitState> states = new HashMap<>();
    for (NodeId typeId : parentByChild.keySet()) {
      validateAcyclic(typeId, parentByChild, states);
    }
  }

  private static void validateAcyclic(
      NodeId typeId, Map<NodeId, NodeId> parentByChild, Map<NodeId, VisitState> states) {

    VisitState state = states.get(typeId);
    if (state == VisitState.VISITING) {
      throw new IllegalStateException("cycle detected in type hierarchy at " + typeId);
    } else if (state == VisitState.VISITED) {
      return;
    }

    states.put(typeId, VisitState.VISITING);
    NodeId parentId = parentByChild.get(typeId);
    if (parentId != null) {
      validateAcyclic(parentId, parentByChild, states);
    }
    states.put(typeId, VisitState.VISITED);
  }

  private static <T extends UAType, N extends TypeInfo<T>> void addChildren(
      N parent,
      NodeId parentId,
      Map<NodeId, List<T>> childrenByParent,
      TypeInfoFactory<T, N> factory) {

    for (T childNode : childrenByParent.getOrDefault(parentId, List.of())) {
      N child = factory.create(parent, childNode);
      parent.addChild(child);
      addChildren(child, NodeIdUtil.parse(childNode.getNodeId()), childrenByParent, factory);
    }
  }

  @FunctionalInterface
  interface TypeInfoFactory<T extends UAType, N extends TypeInfo<T>> {

    N create(@Nullable N parent, T typeNode);
  }

  private enum VisitState {
    VISITING,
    VISITED
  }

  private TypeInfoTreeBuilder() {}
}
