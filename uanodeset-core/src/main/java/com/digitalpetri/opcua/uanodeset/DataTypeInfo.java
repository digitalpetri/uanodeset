package com.digitalpetri.opcua.uanodeset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.opcfoundation.ua.DataTypeDefinition;
import org.opcfoundation.ua.DataTypeField;
import org.opcfoundation.ua.UADataType;

public class DataTypeInfo extends TypeInfo<UADataType> {

  private List<DataTypeField> fields;
  private List<DataTypeField> inheritedFields;

  public DataTypeInfo(@Nullable DataTypeInfo parent, UADataType typeNode) {
    super(parent, typeNode);
  }

  /**
   * Get the List of {@link DataTypeField} from this datatype's definition.
   *
   * @return the List of {@link DataTypeField} from this datatype's definition.
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
   * Get the List of {@link DataTypeField} inherited from the definition of all parent datatypes.
   *
   * @return the List of {@link DataTypeField} inherited from the definition of all parent
   *     datatypes.
   */
  public List<DataTypeField> getInheritedFields() {
    if (inheritedFields == null) {
      var parentTypeInfos = new ArrayList<TypeInfo<UADataType>>();

      TypeInfo<UADataType> parentTypeInfo = getParent();
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
