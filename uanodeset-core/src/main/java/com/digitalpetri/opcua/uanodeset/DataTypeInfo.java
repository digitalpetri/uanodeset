package com.digitalpetri.opcua.uanodeset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.DataTypeDefinition;
import org.opcfoundation.ua.DataTypeField;
import org.opcfoundation.ua.UADataType;

/**
 * Type hierarchy node for a {@link UADataType}.
 *
 * <p>In addition to the generic parent/children behavior from {@link TypeInfo}, datatype nodes
 * expose the fields declared directly on the datatype and the fields inherited from ancestor
 * datatypes. Code generators and namespace loaders use this wrapper when they need to interpret a
 * structure, union, enumeration, option set, or simple subtype from the same public tree API.
 */
public class DataTypeInfo extends TypeInfo<UADataType> {

  private List<DataTypeField> fields;
  private List<DataTypeField> inheritedFields;

  /**
   * Create a datatype hierarchy node.
   *
   * @param parent the parent datatype, or {@code null} for {@code BaseDataType}.
   * @param typeNode the JAXB datatype node.
   */
  public DataTypeInfo(@Nullable DataTypeInfo parent, UADataType typeNode) {
    super(parent, typeNode);
  }

  /**
   * Get the direct supertype as a datatype-specific node.
   *
   * @return the parent datatype, or {@code null} for {@code BaseDataType}.
   */
  @Override
  public @Nullable DataTypeInfo getParent() {
    return (DataTypeInfo) super.getParent();
  }

  /**
   * Get the fields declared directly by this datatype definition.
   *
   * @return the declared fields, or an empty list when the datatype has no definition.
   */
  public List<DataTypeField> getFields() {
    if (fields == null) {
      DataTypeDefinition definition = getTypeNode().getDefinition();
      if (definition != null) {
        fields = List.copyOf(definition.getField());
      } else {
        fields = Collections.emptyList();
      }
    }

    return fields;
  }

  /**
   * Get the fields inherited from ancestor datatype definitions.
   *
   * <p>Fields are returned from the oldest ancestor toward the direct parent. If an ancestor field
   * is redeclared by a later ancestor, the later declaration wins while preserving hierarchy order.
   *
   * @return the inherited fields visible before this datatype's own declared fields.
   */
  public List<DataTypeField> getInheritedFields() {
    if (inheritedFields == null) {
      var parentTypeInfos = new ArrayList<TypeInfo<UADataType>>();

      DataTypeInfo parentTypeInfo = getParent();
      while (parentTypeInfo != null) {
        parentTypeInfos.add(parentTypeInfo);

        parentTypeInfo = parentTypeInfo.getParent();
      }

      Collections.reverse(parentTypeInfos);

      var fields = new LinkedHashMap<String, DataTypeField>();

      parentTypeInfos.forEach(
          typeInfo -> {
            DataTypeDefinition definition = typeInfo.getTypeNode().getDefinition();
            if (definition != null) {
              definition.getField().forEach(field -> fields.put(field.getName(), field));
            }
          });

      inheritedFields = List.copyOf(fields.values());
    }

    return inheritedFields;
  }
}
