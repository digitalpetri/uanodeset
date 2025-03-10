package com.digitalpetri.opcua.uanodeset.parser;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opcfoundation.ua.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class IndexUtil {

    public static final Pattern PATTERN_NODE_ID = Pattern.compile("ns=(\\d+);(.*)");
    public static final Pattern PATTERN_QUALIFIED_NAME = Pattern.compile("(\\d+):(.*)");

    private IndexUtil() {}

    public static void reindexUANode(
        UANode node,
        UriTable mergedTable,
        UriTable originalTable
    ) throws IllegalArgumentException {

        node.setNodeId(reindexNodeId(node.getNodeId(), mergedTable, originalTable));
        node.setBrowseName(reindexQualifiedName(node.getBrowseName(), mergedTable, originalTable));

        reindexRolePermissions(node.getRolePermissions(), mergedTable, originalTable);

        if (node instanceof UADataType dataType) {
            DataTypeDefinition definition = dataType.getDefinition();

            if (definition != null) {
                definition.setBaseType(reindexNodeId(definition.getBaseType(), mergedTable, originalTable));

                definition.getField().forEach(
                    field ->
                        field.setDataType(reindexNodeId(field.getDataType(), mergedTable, originalTable))
                );
            }
        } else if (node instanceof UAVariable variable) {
            variable.setDataType(reindexNodeId(variable.getDataType(), mergedTable, originalTable));

            if (variable.getValue() != null && variable.getValue().getAny() != null) {
                reindexValue(variable.getValue().getAny(), mergedTable, originalTable);
            }
        } else if (node instanceof UAVariableType variableType) {
            variableType.setDataType(reindexNodeId(variableType.getDataType(), mergedTable, originalTable));

            if (variableType.getValue() != null && variableType.getValue().getAny() != null) {
                reindexValue(variableType.getValue().getAny(), mergedTable, originalTable);
            }
        }

        ListOfReferences listOfReferences = Objects.requireNonNullElse(
            node.getReferences(),
            new ListOfReferences()
        );

        listOfReferences.getReference().forEach(
            reference ->
                reindexReference(reference, mergedTable, originalTable)
        );
    }

    /**
     * Re-index a NodeId String from its index in {@code originalTable} to {@code mergedTable}.
     *
     * @param nodeId        the NodeId String to re-index.
     * @param mergedTable   the merged table to re-index for.
     * @param originalTable the original table the index came from.
     * @return a NodeId String re-indexed from {@code originalTable} to {@code mergedTable}.
     * @throws IllegalArgumentException if {@code nodeId}'s URI from {@code originalTable} is not
     *                                  found in {@code mergedTable}.
     */
    public static String reindexNodeId(
        String nodeId,
        UriTable mergedTable,
        UriTable originalTable
    ) throws IllegalArgumentException {

        Matcher m = PATTERN_NODE_ID.matcher(nodeId);

        if (m.matches()) {
            int originalIndex = Integer.parseInt(m.group(1));
            String originalUri = originalTable.getUri().get(originalIndex);
            int mergedIndex = mergedTable.getUri().indexOf(originalUri);

            if (mergedIndex == -1) {
                throw new IllegalArgumentException("URI not found in mergedTable: " + originalUri);
            } else {
                return String.format("ns=%d;%s", mergedIndex, m.group(2));
            }
        } else {
            return nodeId;
        }
    }

    /**
     * Re-index a QualifiedName String from its index in {@code originalTable} to {@code mergedTable}.
     *
     * @param qualifiedName the QualifiedName String to re-index.
     * @param mergedTable   the merged table to re-index for.
     * @param originalTable the original table the index came from.
     * @return a QualifiedName String re-indexed from {@code originalTable} to {@code mergedTable}.
     * @throws IllegalArgumentException if {@code qualifiedName}'s URI from {@code originalTable}
     *                                  is not found in {@code mergedTable}.
     */
    public static String reindexQualifiedName(
        String qualifiedName,
        UriTable mergedTable,
        UriTable originalTable
    ) throws IllegalArgumentException {

        Matcher m = PATTERN_QUALIFIED_NAME.matcher(qualifiedName);

        if (m.matches()) {
            int originalIndex = Integer.parseInt(m.group(1));

            if (originalIndex == 0) {
                return qualifiedName;
            } else {
                String originalUri = originalTable.getUri().get(originalIndex - 1);
                int mergedIndex = mergedTable.getUri().indexOf(originalUri);

                if (mergedIndex == -1) {
                    throw new IllegalArgumentException("URI not found in mergedTable: " + originalUri);
                } else if (mergedIndex + 1 == originalIndex) {
                    // Don't call redundant String.format if the indices turn out to be the same.
                    return qualifiedName;
                } else {
                    return String.format("%d:%s", mergedIndex + 1, m.group(2));
                }
            }
        } else {
            return qualifiedName;
        }
    }

    public static void reindexReference(
        Reference reference,
        UriTable mergedTable,
        UriTable originalTable
    ) throws IllegalArgumentException {

        reference.setReferenceType(reindexNodeId(reference.getReferenceType(), mergedTable, originalTable));
        reference.setValue(reindexNodeId(reference.getValue(), mergedTable, originalTable));
    }

    public static void reindexRolePermissions(
        ListOfRolePermissions listOfRolePermissions,
        UriTable mergedTable,
        UriTable originalTable
    ) {

        if (listOfRolePermissions == null) {
            return;
        }

        listOfRolePermissions.getRolePermission().forEach(
            rolePermission ->
                reindexRolePermission(rolePermission, mergedTable, originalTable)
        );
    }

    public static void reindexRolePermission(
        RolePermission rolePermission,
        UriTable mergedTable,
        UriTable originalTable
    ) {

        rolePermission.setValue(
            reindexNodeId(rolePermission.getValue(), mergedTable, originalTable)
        );
    }

    private static void reindexValue(Object value, UriTable mergedTable, UriTable originalTable) {
        if (value instanceof Node xmlNode) {
            reindexXmlNodeIdentifierElements(xmlNode, mergedTable, originalTable);
        } else {
            System.err.println("Unexpected value: " + value);
        }
    }

  private static void reindexXmlNodeIdentifierElements(
      Node xmlNode, UriTable mergedTable, UriTable originalTable) {

    if ("Identifier".equals(xmlNode.getLocalName())) {
      String nodeValue = xmlNode.getTextContent();
      if (nodeValue != null) {
        xmlNode.setTextContent(reindexNodeId(nodeValue, mergedTable, originalTable));
      }
    } else {
      NodeList childNodes = xmlNode.getChildNodes();
      for (int i = 0; i < childNodes.getLength(); i++) {
        reindexXmlNodeIdentifierElements(childNodes.item(i), mergedTable, originalTable);
      }
    }
  }
}
