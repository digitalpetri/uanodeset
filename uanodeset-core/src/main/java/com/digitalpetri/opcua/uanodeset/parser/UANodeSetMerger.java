package com.digitalpetri.opcua.uanodeset.parser;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.opcfoundation.ua.ModelTable;
import org.opcfoundation.ua.ModelTableEntry;
import org.opcfoundation.ua.NodeIdAlias;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UANodeSet;
import org.opcfoundation.ua.UriTable;

public final class UANodeSetMerger {

  private UANodeSetMerger() {}

  /**
   * Merge the contents of two {@link UANodeSet}s.
   *
   * <p>This method mutates objects in both {@code nodeSet1} and {@code nodeSet2}, using {@code
   * nodeSet1} as the merge base that the contents of {@code nodeSet2} are merged into.
   *
   * @param baseNodeSet a {@link UANodeSet} to use as the merge base.
   * @param incomingNodeSet a {@link UANodeSet} to merge into the base.
   * @return {@code nodeSet1} having had all of {@code nodeSet2} merged into it.
   */
  public static UANodeSet merge(UANodeSet baseNodeSet, UANodeSet incomingNodeSet) {
    mergeNamespaceUris(baseNodeSet, incomingNodeSet);

    mergeModels(baseNodeSet, incomingNodeSet);

    mergeAliases(baseNodeSet, incomingNodeSet);

    mergeNodes(baseNodeSet, incomingNodeSet);

    return baseNodeSet;
  }

  private static void mergeNamespaceUris(UANodeSet baseNodeSet, UANodeSet incomingNodeSet) {
    UriTable baseTable = baseNodeSet.getNamespaceUris();
    if (baseTable == null) {
      baseTable = new UriTable();
      baseTable.getUri().add(Namespaces.OPC_UA);
      baseNodeSet.setNamespaceUris(baseTable);
    }
    if (!baseTable.getUri().isEmpty() && !baseTable.getUri().get(0).equals(Namespaces.OPC_UA)) {
      baseTable.getUri().add(0, Namespaces.OPC_UA);
    }

    UriTable incomingTable =
        Objects.requireNonNullElseGet(
            incomingNodeSet.getNamespaceUris(),
            () -> {
              UriTable uriTable = new UriTable();
              uriTable.getUri().add(Namespaces.OPC_UA);
              incomingNodeSet.setNamespaceUris(uriTable);
              return uriTable;
            });

    if (!incomingTable.getUri().isEmpty()
        && !incomingTable.getUri().get(0).equals(Namespaces.OPC_UA)) {

      incomingTable.getUri().add(0, Namespaces.OPC_UA);
    }

    for (String uri : incomingTable.getUri()) {
      if (!baseTable.getUri().contains(uri)) {
        baseTable.getUri().add(uri);
      }
    }
  }

  private static void mergeModels(UANodeSet baseNodeSet, UANodeSet incomingNodeSet) {
    ModelTable baseModelTable = baseNodeSet.getModels();
    if (baseModelTable == null) {
      baseModelTable = new ModelTable();
      baseNodeSet.setModels(baseModelTable);
      System.err.println("baseNodeSet specified no model URIs.");
    }

    ModelTable incomingModelTable = incomingNodeSet.getModels();
    if (incomingModelTable == null) {
      incomingModelTable = new ModelTable();
      incomingNodeSet.setModels(incomingModelTable);
      System.err.println("incomingNodeSet specified no model URIs.");
    }

    for (ModelTableEntry incomingEntry : incomingModelTable.getModel()) {
      if (baseModelTable.getModel().stream()
          .noneMatch(
              baseEntry -> Objects.equals(baseEntry.getModelUri(), incomingEntry.getModelUri()))) {

        baseModelTable.getModel().add(incomingEntry);
      } else {
        System.out.println("Ignoring duplicate model URI: " + incomingEntry.getModelUri());
      }
    }
  }

  private static void mergeAliases(UANodeSet baseNodeSet, UANodeSet incomingNodeSet) {
    Map<String, String> baseAliasMap =
        baseNodeSet.getAliases().getAlias().stream()
            .collect(Collectors.toMap(NodeIdAlias::getAlias, NodeIdAlias::getValue));

    incomingNodeSet
        .getAliases()
        .getAlias()
        .forEach(
            nodeIdAlias -> {
              String incomingAlias = nodeIdAlias.getAlias();
              String incomingNodeId =
                  IndexUtil.reindexNodeId(
                      nodeIdAlias.getValue(),
                      baseNodeSet.getNamespaceUris(),
                      incomingNodeSet.getNamespaceUris());
              nodeIdAlias.setValue(incomingNodeId);

              if (!baseAliasMap.containsKey(incomingAlias)) {
                baseNodeSet.getAliases().getAlias().add(nodeIdAlias);
              } else {
                if (!Objects.equals(incomingNodeId, baseAliasMap.get(incomingAlias))) {
                  String warning =
                      String.format(
                          "Alias collision on \"%s\". "
                              + "NodeId in baseNodeSet: %s, NodeId in incomingNodeSet: %s",
                          incomingAlias, baseAliasMap.get(incomingAlias), incomingNodeId);
                  System.err.println(warning);
                }
              }
            });
  }

  private static void mergeNodes(UANodeSet baseNodeSet, UANodeSet incomingNodeSet) {
    Set<String> existingNodeIds =
        baseNodeSet.getUAObjectOrUAVariableOrUAMethod().stream()
            .map(UANode::getNodeId)
            .collect(Collectors.toSet());

    for (UANode node : incomingNodeSet.getUAObjectOrUAVariableOrUAMethod()) {
      String nodeIdBeforeReindex = node.getNodeId();

      IndexUtil.reindexUANode(
          node, baseNodeSet.getNamespaceUris(), incomingNodeSet.getNamespaceUris());

      if (existingNodeIds.contains(node.getNodeId())) {
        System.err.println(
            "Duplicate node, before=" + nodeIdBeforeReindex + " after=" + node.getNodeId());
      } else {
        baseNodeSet.getUAObjectOrUAVariableOrUAMethod().add(node);
        existingNodeIds.add(node.getNodeId());
      }
    }
  }
}
