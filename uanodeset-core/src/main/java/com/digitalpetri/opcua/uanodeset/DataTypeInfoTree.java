package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.opcfoundation.ua.DataTypeField;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UADataType;
import org.opcfoundation.ua.UANode;

public class DataTypeInfoTree extends TypeInfoTree<UADataType, DataTypeInfo> {

  private static final boolean DEBUG = false;

  public DataTypeInfoTree(DataTypeInfo rootTypeInfo) {
    super(rootTypeInfo);
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a built-in type.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a built-in type, {@code false} otherwise.
   */
  public boolean isBuiltinType(String nodeId) {
    return OpcUaDataType.isBuiltin(NodeId.parse(nodeId));
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is an enumeration.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is an enumeration, {@code false} otherwise.
   */
  public boolean isEnumeration(NodeId nodeId) {
    return isEnumeration(NodeIdUtil.get(nodeId));
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is an enumeration.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is an enumeration, {@code false} otherwise.
   */
  public boolean isEnumeration(String nodeId) {
    if (NodeIdUtil.equals(NodeIds.Enumeration, nodeId)) {
      return true;
    } else {
      return isSubtypeOf(nodeId, NodeIdUtil.get(NodeIds.Enumeration));
    }
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a structure.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a structure, {@code false} otherwise.
   */
  public boolean isStructure(NodeId nodeId) {
    return isStructure(NodeIdUtil.get(nodeId));
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a structure.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a structure, {@code false} otherwise.
   */
  public boolean isStructure(String nodeId) {
    if (NodeIdUtil.equals(NodeIds.Structure, nodeId)) {
      return true;
    } else {
      TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

      return typeInfo != null && typeInfo.getParent() != null &&
          isStructure(typeInfo.getParent().getTypeNode().getNodeId());
    }
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a structure with one or more optional
   * fields.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a structure with one or more optional fields,
   * {@code false} otherwise.
   */
  public boolean isStructureWithOptionalFields(NodeId nodeId) {
    return isStructureWithOptionalFields(NodeIdUtil.get(nodeId));
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a structure with one or more optional
   * fields.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a structure with one or more optional fields,
   * {@code false} otherwise.
   */
  public boolean isStructureWithOptionalFields(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    if (typeInfo == null) {
      return false;
    } else {
      Stream<DataTypeField> fields = Stream.concat(
          typeInfo.getFields().stream(),
          typeInfo.getInheritedFields().stream()
      );

      return fields.anyMatch(DataTypeField::isIsOptional);
    }
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a structure with one or more fields
   * that allow subtypes.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a structure with one or more fields that allow
   * subtypes, {@code false} otherwise.
   */
  public boolean isStructureWithSubtypedValues(NodeId nodeId) {
    return isStructureWithSubtypedValues(NodeIdUtil.get(nodeId));
  }

  /**
   * Check if the UADataType identified by {@code nodeId} is a structure with one or more fields
   * that allow subtypes.
   *
   * @param nodeId the NodeId of the UADataType to check.
   * @return {@code true} if the UADataType is a structure with one or more fields that allow
   * subtypes, {@code false} otherwise.
   */
  public boolean isStructureWithSubtypedValues(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    if (typeInfo == null) {
      return false;
    } else {
      Stream<DataTypeField> fields = Stream.concat(
          typeInfo.getFields().stream(),
          typeInfo.getInheritedFields().stream()
      );

      return fields.anyMatch(DataTypeField::isAllowSubTypes);
    }
  }

  public boolean isOptionSet(NodeId nodeId) {
    return isOptionSet(NodeIdUtil.get(nodeId));
  }

  public boolean isOptionSet(String nodeId) {
    TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null &&
        typeInfo.getTypeNode().getDefinition().isIsOptionSet() && isSubtypeOf(nodeId,
        NodeIds.OptionSet);
  }

  public boolean isOptionSetUInteger(NodeId nodeId) {
    return isOptionSetUInteger(NodeIdUtil.get(nodeId));
  }

  public boolean isOptionSetUInteger(String nodeId) {
    TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null &&
        typeInfo.getTypeNode().getDefinition().isIsOptionSet() && isSubtypeOf(nodeId,
        NodeIds.UInteger);
  }

  public boolean isUnion(NodeId nodeId) {
    return isUnion(NodeIdUtil.get(nodeId));
  }

  public boolean isUnion(String nodeId) {
    TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null &&
        typeInfo.getTypeNode().getDefinition().isIsUnion() && isSubtypeOf(nodeId, NodeIds.Union);
  }

  public boolean isUnionWithSubtypedValues(NodeId nodeId) {
    return isUnionWithSubtypedValues(NodeIdUtil.get(nodeId));
  }

  public boolean isUnionWithSubtypedValues(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    if (typeInfo == null) {
      return false;
    } else {
      Stream<DataTypeField> fields =
          Stream.concat(typeInfo.getFields().stream(), typeInfo.getInheritedFields().stream());

      return typeInfo.getTypeNode().getDefinition().isIsUnion() &&
          isSubtypeOf(nodeId, NodeIds.Union) && fields.anyMatch(DataTypeField::isAllowSubTypes);
    }
  }

  public static DataTypeInfoTree create(NodeSetContext context) {
    UANode node = context.getNode(NodeIdUtil.get(NodeIds.BaseDataType));

    if (node instanceof UADataType dataTypeNode) {
      var rootTypeInfo = new DataTypeInfo(null, dataTypeNode);

      addChildren(context, rootTypeInfo, 0);

      return new DataTypeInfoTree(rootTypeInfo);
    } else {
      throw new IllegalStateException("UADataType BaseDataType not found");
    }
  }

  private static void addChildren(NodeSetContext context, DataTypeInfo typeInfo, int level) {
    List<Reference> references = context.getReferences(typeInfo.getTypeNode().getNodeId());

    List<UADataType> subTypes = references.stream()
        .filter(r -> r.isIsForward() && NodeIdUtil.equals(NodeIds.HasSubtype, r.getReferenceType()))
        .map(r -> context.getNode(r.getValue()))
        .filter(n -> n instanceof UADataType)
        .map(n -> (UADataType) n)
        .toList();

    for (UADataType dataType : subTypes) {
      if (DEBUG) {
        for (int i = 0; i < level; i++) {
          System.out.print("  ");
        }
        System.out.println(dataType.getBrowseName());
      }

      var child = new DataTypeInfo(typeInfo, dataType);
      typeInfo.addChild(child);

      addChildren(context, child, level + 1);
    }
  }

}
