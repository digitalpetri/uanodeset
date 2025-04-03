package com.digitalpetri.opcua.uanodeset;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UANodeSet;

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
   * Get the {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   *
   * @param nodeId the NodeId of the {@link UANode} to get references for.
   * @return the {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   */
  List<Reference> getReferences(String nodeId);

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
   * Get the implicit {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   *
   * <p>Implicit references are those that are not defined explicitly in the {@link UANodeSet} file
   * but are created implicitly, usually as the inverse of an explicit reference.
   *
   * @param nodeId the NodeId of the {@link UANode} to get implicit references for.
   * @return the implicit {@link Reference}s for the {@link UANode} identified by {@code nodeId}.
   */
  List<Reference> getImplicitReferences(String nodeId);
}
