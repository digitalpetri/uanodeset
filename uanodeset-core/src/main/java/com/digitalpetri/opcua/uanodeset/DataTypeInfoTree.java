package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.OpcUaDataType;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UNumber;
import org.opcfoundation.ua.DataTypeField;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UADataType;
import org.opcfoundation.ua.UANode;

/**
 * Public datatype hierarchy built from {@code BaseDataType}.
 *
 * <p>The tree gives callers a normalized view of OPC UA datatype inheritance and datatype
 * categories. It is useful both for code generation and for namespace tools that need to answer
 * questions such as whether a datatype is a structure, enum, option set, union, builtin-compatible
 * simple type, or a subtype of another datatype.
 */
public class DataTypeInfoTree extends TypeInfoTree<UADataType, DataTypeInfo> {

  private static final boolean DEBUG = false;

  /**
   * Create a datatype tree from a linked root node.
   *
   * <p>Most callers should use {@link #create(NodeSetContext)} so the hierarchy is built from a
   * normalized {@link NodeSetContext}.
   *
   * @param rootTypeInfo the {@code BaseDataType} node.
   */
  public DataTypeInfoTree(DataTypeInfo rootTypeInfo) {
    super(rootTypeInfo);
  }

  /**
   * Check whether a datatype is one of Milo's built-in OPC UA datatypes.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is built in.
   */
  public boolean isBuiltinType(String nodeId) {
    return OpcUaDataType.isBuiltin(NodeId.parse(nodeId));
  }

  /**
   * Check whether a datatype is one of Milo's built-in OPC UA datatypes.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is built in.
   */
  public boolean isBuiltinType(NodeId nodeId) {
    return OpcUaDataType.isBuiltin(nodeId);
  }

  /**
   * Resolve the Java backing class for a datatype.
   *
   * <p>Simple datatypes inherit the backing class of their nearest built-in ancestor. Enumeration
   * and numeric abstract roots resolve to the broad Java types used by Milo's stack layer.
   *
   * @param dataTypeId the NodeId of the datatype to resolve.
   * @return the Java class used to represent values of the datatype.
   * @throws IllegalArgumentException if the datatype is unknown or cannot be traced to a supported
   *     ancestor.
   */
  public Class<?> getBackingClass(String dataTypeId) {
    return getBackingClass(NodeId.parse(dataTypeId));
  }

  /**
   * Resolve the Java backing class for a datatype.
   *
   * <p>Simple datatypes inherit the backing class of their nearest built-in ancestor. Enumeration
   * and numeric abstract roots resolve to the broad Java types used by Milo's stack layer.
   *
   * @param dataTypeId the NodeId of the datatype to resolve.
   * @return the Java class used to represent values of the datatype.
   * @throws IllegalArgumentException if the datatype is unknown or cannot be traced to a supported
   *     ancestor.
   */
  public Class<?> getBackingClass(NodeId dataTypeId) {
    if (OpcUaDataType.isBuiltin(dataTypeId)) {
      OpcUaDataType builtinDataType = OpcUaDataType.fromNodeId(dataTypeId);
      return builtinDataType != null ? builtinDataType.getBackingClass() : null;
    } else if (NodeIds.Enumeration.equals(dataTypeId)) {
      return Integer.class;
    } else if (NodeIds.Number.equals(dataTypeId) || NodeIds.Integer.equals(dataTypeId)) {
      return Number.class;
    } else if (NodeIds.UInteger.equals(dataTypeId)) {
      return UNumber.class;
    } else {
      DataTypeInfo typeInfo = getTypeInfo(NodeIdUtil.get(dataTypeId));
      if (typeInfo != null && typeInfo.getParent() != null) {
        return getBackingClass(typeInfo.getParent().getTypeNode().getNodeId());
      } else {
        throw new IllegalArgumentException("no parent TypeInfo for dataTypeId: " + dataTypeId);
      }
    }
  }

  /**
   * Resolve the Milo built-in datatype that represents values of a datatype.
   *
   * <p>For built-in datatypes this returns the datatype itself. For simple custom datatypes this
   * walks up the datatype hierarchy until it reaches the built-in ancestor.
   *
   * @param dataTypeId the NodeId of the datatype to resolve.
   * @return the corresponding Milo built-in datatype.
   * @throws IllegalArgumentException if the datatype is unknown or cannot be traced to a built-in
   *     ancestor.
   */
  public OpcUaDataType getOpcUaDataType(NodeId dataTypeId) {
    return getOpcUaDataType(NodeIdUtil.get(dataTypeId));
  }

  /**
   * Resolve the Milo built-in datatype that represents values of a datatype.
   *
   * <p>For built-in datatypes this returns the datatype itself. For simple custom datatypes this
   * walks up the datatype hierarchy until it reaches the built-in ancestor.
   *
   * @param dataTypeId the NodeId of the datatype to resolve.
   * @return the corresponding Milo built-in datatype.
   * @throws IllegalArgumentException if the datatype is unknown or cannot be traced to a built-in
   *     ancestor.
   */
  public OpcUaDataType getOpcUaDataType(String dataTypeId) {
    NodeId nodeId = NodeId.parse(dataTypeId);

    if (OpcUaDataType.isBuiltin(nodeId)) {
      return OpcUaDataType.fromNodeId(nodeId);
    } else if (NodeIds.Enumeration.equals(nodeId)) {
      return OpcUaDataType.Int32;
    } else {
      DataTypeInfo typeInfo = getTypeInfo(dataTypeId);
      if (typeInfo != null && typeInfo.getParent() != null) {
        return getOpcUaDataType(typeInfo.getParent().getTypeNode().getNodeId());
      } else {
        throw new IllegalArgumentException("no parent TypeInfo for dataTypeId: " + dataTypeId);
      }
    }
  }

  /**
   * Check whether a datatype is {@code Enumeration} or one of its descendants.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is an enumeration.
   */
  public boolean isEnumeration(NodeId nodeId) {
    return isEnumeration(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a datatype is {@code Enumeration} or one of its descendants.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is an enumeration.
   */
  public boolean isEnumeration(String nodeId) {
    if (NodeIdUtil.equals(NodeIds.Enumeration, nodeId)) {
      return true;
    } else {
      return isSubtypeOf(nodeId, NodeIdUtil.get(NodeIds.Enumeration));
    }
  }

  /**
   * Check whether a datatype is {@code Structure} or one of its descendants.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is a structure.
   */
  public boolean isStructure(NodeId nodeId) {
    return isStructure(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a datatype is {@code Structure} or one of its descendants.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is a structure.
   */
  public boolean isStructure(String nodeId) {
    if (NodeIdUtil.equals(NodeIds.Structure, nodeId)) {
      return true;
    } else {
      TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

      return typeInfo != null
          && typeInfo.getParent() != null
          && isStructure(typeInfo.getParent().getTypeNode().getNodeId());
    }
  }

  /**
   * Check whether a datatype is represented as a scalar stack value rather than a generated
   * structure, enumeration, union, or option-set integer type.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype should be treated as a simple value type.
   */
  public boolean isSimpleType(NodeId nodeId) {
    return isSimpleType(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a datatype is represented as a scalar stack value rather than a generated
   * structure, enumeration, union, or option-set integer type.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype should be treated as a simple value type.
   */
  public boolean isSimpleType(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    return typeInfo != null
        && !isStructure(nodeId)
        && !isEnumeration(nodeId)
        && !isOptionSetUInteger(nodeId);
  }

  /**
   * Check whether a structure has any optional fields after inherited fields are considered.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype has at least one optional field.
   */
  public boolean isStructureWithOptionalFields(NodeId nodeId) {
    return isStructureWithOptionalFields(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a structure has any optional fields after inherited fields are considered.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype has at least one optional field.
   */
  public boolean isStructureWithOptionalFields(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    if (typeInfo == null) {
      return false;
    } else {
      Stream<DataTypeField> fields =
          Stream.concat(typeInfo.getFields().stream(), typeInfo.getInheritedFields().stream());

      return fields.anyMatch(DataTypeField::isIsOptional);
    }
  }

  /**
   * Check whether a structure has any field that may carry subtype values.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype has at least one field with subtype values enabled.
   */
  public boolean isStructureWithSubtypedValues(NodeId nodeId) {
    return isStructureWithSubtypedValues(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a structure has any field that may carry subtype values.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype has at least one field with subtype values enabled.
   */
  public boolean isStructureWithSubtypedValues(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    if (typeInfo == null) {
      return false;
    } else {
      Stream<DataTypeField> fields =
          Stream.concat(typeInfo.getFields().stream(), typeInfo.getInheritedFields().stream());

      return fields.anyMatch(DataTypeField::isAllowSubTypes);
    }
  }

  /**
   * Check whether a datatype is an {@code OptionSet} structure.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype definition is marked as an option set and descends from
   *     {@code OptionSet}.
   */
  public boolean isOptionSet(NodeId nodeId) {
    return isOptionSet(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a datatype is an {@code OptionSet} structure.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype definition is marked as an option set and descends from
   *     {@code OptionSet}.
   */
  public boolean isOptionSet(String nodeId) {
    TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null
        && typeInfo.getTypeNode().getDefinition() != null
        && typeInfo.getTypeNode().getDefinition().isIsOptionSet()
        && isSubtypeOf(nodeId, NodeIds.OptionSet);
  }

  /**
   * Check whether a datatype is an option-set integer subtype.
   *
   * <p>These datatypes are encoded as unsigned integer values but are generated with option-set
   * helper APIs rather than treated as plain simple types.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype definition is marked as an option set and descends from
   *     {@code UInteger}.
   */
  public boolean isOptionSetUInteger(NodeId nodeId) {
    return isOptionSetUInteger(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a datatype is an option-set integer subtype.
   *
   * <p>These datatypes are encoded as unsigned integer values but are generated with option-set
   * helper APIs rather than treated as plain simple types.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype definition is marked as an option set and descends from
   *     {@code UInteger}.
   */
  public boolean isOptionSetUInteger(String nodeId) {
    TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null
        && typeInfo.getTypeNode().getDefinition() != null
        && typeInfo.getTypeNode().getDefinition().isIsOptionSet()
        && isSubtypeOf(nodeId, NodeIds.UInteger);
  }

  /**
   * Check whether a datatype is a union.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype definition is marked as a union and descends from {@code
   *     Union}.
   */
  public boolean isUnion(NodeId nodeId) {
    return isUnion(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a datatype is a union.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype definition is marked as a union and descends from {@code
   *     Union}.
   */
  public boolean isUnion(String nodeId) {
    TypeInfo<UADataType> typeInfo = getTypeInfo(nodeId);

    return typeInfo != null
        && typeInfo.getTypeNode().getDefinition() != null
        && typeInfo.getTypeNode().getDefinition().isIsUnion()
        && isSubtypeOf(nodeId, NodeIds.Union);
  }

  /**
   * Check whether a union has any field that may carry subtype values.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is a union and has at least one field with subtype values
   *     enabled.
   */
  public boolean isUnionWithSubtypedValues(NodeId nodeId) {
    return isUnionWithSubtypedValues(NodeIdUtil.get(nodeId));
  }

  /**
   * Check whether a union has any field that may carry subtype values.
   *
   * @param nodeId the NodeId of the datatype to check.
   * @return {@code true} if the datatype is a union and has at least one field with subtype values
   *     enabled.
   */
  public boolean isUnionWithSubtypedValues(String nodeId) {
    DataTypeInfo typeInfo = getTypeInfo(nodeId);

    if (typeInfo == null) {
      return false;
    } else {
      Stream<DataTypeField> fields =
          Stream.concat(typeInfo.getFields().stream(), typeInfo.getInheritedFields().stream());

      return typeInfo.getTypeNode().getDefinition() != null
          && typeInfo.getTypeNode().getDefinition().isIsUnion()
          && isSubtypeOf(nodeId, NodeIds.Union)
          && fields.anyMatch(DataTypeField::isAllowSubTypes);
    }
  }

  /**
   * Build a datatype tree from a normalized node set context.
   *
   * @param context the context that supplies nodes and resolved references.
   * @return a datatype tree rooted at {@code BaseDataType}.
   * @throws IllegalStateException if the context does not contain {@code BaseDataType}.
   */
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

    List<UADataType> subTypes =
        references.stream()
            .filter(
                r -> r.isIsForward() && NodeIdUtil.equals(NodeIds.HasSubtype, r.getReferenceType()))
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
