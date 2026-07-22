package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.List;
import java.util.Optional;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UANodeSet;
import org.opcfoundation.ua.UAObject;
import org.opcfoundation.ua.UAVariable;

/**
 * Query boundary for a normalized UANodeSet model.
 *
 * <p>NodeId overloads compare identifiers semantically, so namespace-zero spellings such as {@code
 * i=58} and {@code ns=0;i=58} are interchangeable. Reference queries expose both explicit
 * declarations and synthesized inverse references, allowing relationship helpers to behave the same
 * regardless of which side declared a relationship in XML. Namespace indexes belong to this
 * context's {@link UANodeSet} URI table; they are model-space indexes rather than indexes from a
 * runtime server or another merged model.
 */
public interface NodeSetContext {

  /**
   * Get the {@link UANodeSet} that this {@link NodeSetContext} is operating on.
   *
   * @return the {@link UANodeSet} that this {@link NodeSetContext} is operating on.
   */
  UANodeSet getNodeSet();

  /**
   * Get the {@link UANode} identified by {@code nodeId}.
   *
   * @param nodeId the NodeId of the {@link UANode} to get.
   * @return the {@link UANode} identified by {@code nodeId}, or {@code null} if no such node
   *     exists.
   */
  @Nullable UANode getNode(String nodeId);

  /**
   * Get the {@link UANode} identified by a semantic NodeId.
   *
   * @param nodeId the NodeId of the node to get.
   * @return the identified node, or {@code null} if no such node exists.
   */
  default @Nullable UANode getNode(NodeId nodeId) {
    return getNode(NodeIdUtil.get(nodeId));
  }

  /**
   * Get the {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   *
   * @param nodeId the NodeId of the {@link UANode} to get references for.
   * @return the {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   */
  List<Reference> getReferences(String nodeId);

  /**
   * Get all explicit and implicit references known for a node.
   *
   * @param nodeId the NodeId of the node to inspect.
   * @return the deduplicated references for the node.
   */
  default List<Reference> getReferences(NodeId nodeId) {
    return getReferences(NodeIdUtil.get(nodeId));
  }

  /**
   * Get the explicit {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   *
   * <p>Explicit references are those that are defined explicitly in the {@link UANodeSet} file.
   *
   * @param nodeId the NodeId of the {@link UANode} to get explicit references for.
   * @return the explicit {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   */
  List<Reference> getExplicitReferences(String nodeId);

  /**
   * Get references declared directly on a node in the source XML.
   *
   * @param nodeId the NodeId of the node to inspect.
   * @return the explicit references for the node.
   */
  default List<Reference> getExplicitReferences(NodeId nodeId) {
    return getExplicitReferences(NodeIdUtil.get(nodeId));
  }

  /**
   * Get the implicit {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   *
   * <p>Implicit references are those that are not defined explicitly in the {@link UANodeSet} file
   * but are created implicitly, usually as the inverse of an explicit reference.
   *
   * @param nodeId the NodeId of the {@link UANode} to get implicit references for.
   * @return the implicit {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   */
  List<Reference> getImplicitReferences(String nodeId);

  /**
   * Get inverse references synthesized from declarations on other nodes.
   *
   * @param nodeId the NodeId of the node to inspect.
   * @return the implicit references for the node.
   */
  default List<Reference> getImplicitReferences(NodeId nodeId) {
    return getImplicitReferences(NodeIdUtil.get(nodeId));
  }

  /**
   * Resolve the direct type definition of an Object or Variable node.
   *
   * <p>The combined reference view makes a forward {@code HasTypeDefinition} visible on the
   * instance whether the source XML declares it forward on the instance or inverse on the type.
   * Other node classes do not have a type definition through this relationship.
   *
   * @param nodeId the NodeId of the instance to inspect.
   * @return the direct type definition, or an empty value for an unknown node or a node without a
   *     {@code HasTypeDefinition} relationship.
   */
  default Optional<NodeId> getTypeDefinition(NodeId nodeId) {
    UANode node = getNode(nodeId);
    return node != null ? getTypeDefinition(node) : Optional.empty();
  }

  /**
   * Resolve the direct type definition of an Object or Variable node.
   *
   * @param nodeId the parseable NodeId of the instance to inspect.
   * @return the direct type definition, or an empty value when it cannot be resolved.
   */
  default Optional<NodeId> getTypeDefinition(String nodeId) {
    return getTypeDefinition(NodeIdUtil.parse(nodeId));
  }

  /**
   * Resolve the direct type definition of an Object or Variable node.
   *
   * @param node the normalized JAXB node to inspect.
   * @return the direct type definition, or an empty value when the node class has no type
   *     definition or no relationship is present.
   */
  default Optional<NodeId> getTypeDefinition(UANode node) {
    if (!(node instanceof UAObject || node instanceof UAVariable)) {
      return Optional.empty();
    }

    return getReferences(NodeIdUtil.parse(node.getNodeId())).stream()
        .filter(Reference::isIsForward)
        .filter(
            reference -> NodeIdUtil.equals(NodeIds.HasTypeDefinition, reference.getReferenceType()))
        .map(Reference::getValue)
        .map(NodeIdUtil::parse)
        .findFirst();
  }
}
