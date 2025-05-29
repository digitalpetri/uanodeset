package com.digitalpetri.opcua.uanodeset.namespace;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import com.digitalpetri.opcua.uanodeset.DataTypeInfo;
import com.digitalpetri.opcua.uanodeset.DataTypeInfoTree;
import com.digitalpetri.opcua.uanodeset.NodeSet;
import com.digitalpetri.opcua.uanodeset.parser.IndexUtil;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.milo.opcua.sdk.core.types.codec.DynamicCodecFactory;
import org.eclipse.milo.opcua.sdk.core.typetree.DataType;
import org.eclipse.milo.opcua.sdk.core.typetree.DataTypeTree;
import org.eclipse.milo.opcua.sdk.server.ObjectTypeManager;
import org.eclipse.milo.opcua.sdk.server.VariableTypeManager;
import org.eclipse.milo.opcua.sdk.server.nodes.UaDataTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaReferenceTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaViewNode;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaSerializationException;
import org.eclipse.milo.opcua.stack.core.encoding.DataTypeCodec;
import org.eclipse.milo.opcua.stack.core.encoding.EncodingContext;
import org.eclipse.milo.opcua.stack.core.encoding.xml.OpcUaXmlDecoder;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelExType;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessRestrictionType;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumField;
import org.eclipse.milo.opcua.stack.core.types.structured.PermissionType;
import org.eclipse.milo.opcua.stack.core.types.structured.RolePermissionType;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.eclipse.milo.opcua.stack.core.util.SecureXmlUtil;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.AliasTable;
import org.opcfoundation.ua.DataTypeDefinition;
import org.opcfoundation.ua.DataTypeField;
import org.opcfoundation.ua.ListOfRolePermissions;
import org.opcfoundation.ua.LocalizedText;
import org.opcfoundation.ua.NodeIdAlias;
import org.opcfoundation.ua.ObjectFactory;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.RolePermission;
import org.opcfoundation.ua.UADataType;
import org.opcfoundation.ua.UAMethod;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UANodeSet;
import org.opcfoundation.ua.UAObject;
import org.opcfoundation.ua.UAObjectType;
import org.opcfoundation.ua.UAReferenceType;
import org.opcfoundation.ua.UAVariable;
import org.opcfoundation.ua.UAVariableType;
import org.opcfoundation.ua.UAView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class NodeSetNodeLoader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final JAXBContext jaxbContext;
  private final Marshaller marshaller;

  private final NodeSet nodeSet;
  private final UaNodeContext context;
  private final EncodingContext encodingContext;
  private final Predicate<String> namespaceFilter;

  public NodeSetNodeLoader(
      NodeSet nodeSet,
      UaNodeContext context,
      EncodingContext encodingContext,
      Predicate<String> namespaceFilter) {

    this.nodeSet = nodeSet;
    this.context = context;
    this.encodingContext = encodingContext;
    this.namespaceFilter = namespaceFilter;

    try {
      jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
      marshaller = jaxbContext.createMarshaller();
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  public void loadNodes() {
    // Add references for all nodes.
    for (UANode node : nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod()) {
      String nodeId = resolveAlias(node.getNodeId());
      String namespaceUri =
          nodeSet.getNodeSet().getNamespaceUris().getUri().get(getNamespaceIndex(nodeId));

      if (namespaceFilter.test(namespaceUri)) {
        for (Reference reference : node.getReferences().getReference()) {
          org.eclipse.milo.opcua.sdk.core.Reference ref = reindexReference(node, reference);

          context.getNodeManager().addReferences(ref, context.getServer().getNamespaceTable());
        }
      }
    }

    // Build all type nodes.
    for (UANode node : nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod()) {
      String nodeId = resolveAlias(node.getNodeId());
      String namespaceUri =
          nodeSet.getNodeSet().getNamespaceUris().getUri().get(getNamespaceIndex(nodeId));

      if (namespaceFilter.test(namespaceUri)) {
        if (node instanceof UAObjectType objectType) {
          UaNode objectTypeNode = buildObjectTypeNode(objectType);

          context.getNodeManager().addNode(objectTypeNode);
        } else if (node instanceof UAVariableType variableType) {
          UaNode variableTypeNode = buildVariableTypeNode(variableType);

          context.getNodeManager().addNode(variableTypeNode);
        } else if (node instanceof UADataType dataType) {
          UaDataTypeNode dataTypeNode = buildDataTypeNode(dataType);

          context.getNodeManager().addNode(dataTypeNode);
        } else if (node instanceof UAReferenceType referenceType) {
          UaNode referenceTypeNode = buildReferenceTypeNode(referenceType);

          context.getNodeManager().addNode(referenceTypeNode);
        }
      }
    }

    // Build all other node types.
    for (UANode node : nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod()) {
      String nodeId = resolveAlias(node.getNodeId());
      String namespaceUri =
          nodeSet.getNodeSet().getNamespaceUris().getUri().get(getNamespaceIndex(nodeId));

      if (namespaceFilter.test(namespaceUri)) {
        if (node instanceof UAObject object) {
          UaNode objectNode = buildObjectNode(object);

          context.getNodeManager().addNode(objectNode);
        } else if (node instanceof UAMethod method) {
          UaNode methodNode = buildMethodNode(method);

          context.getNodeManager().addNode(methodNode);
        } else if (node instanceof UAVariable variable) {
          UaNode variableNode = buildVariableNode(variable);

          context.getNodeManager().addNode(variableNode);
        } else if (node instanceof UAView view) {
          UaNode viewNode = buildViewNode(view);

          context.getNodeManager().addNode(viewNode);
        }
      }
    }

    context.getServer().updateDataTypeTree();
    context.getServer().updateReferenceTypeTree();

    // Set DataTypeDefinitions for all DataTypes.
    DataTypeInfoTree dataTypeTree = DataTypeInfoTree.create(nodeSet);

    for (UANode node : nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod()) {
      String nodeId = resolveAlias(node.getNodeId());
      String namespaceUri =
          nodeSet.getNodeSet().getNamespaceUris().getUri().get(getNamespaceIndex(nodeId));

      if (namespaceFilter.test(namespaceUri)) {
        if (node instanceof UADataType dataType) {
          UaDataTypeNode dataTypeNode =
              (UaDataTypeNode)
                  context.getNodeManager().getNode(reindexNodeId(nodeId)).orElseThrow();

          var dataTypeDefinition = newDataTypeDefinition(dataType, dataTypeTree);

          if (dataTypeDefinition != null) {
            dataTypeNode.setDataTypeDefinition(dataTypeDefinition);

            registerDynamicDataType(dataTypeNode.getNodeId());
          }
        }
      }
    }

    // Set values for all Variable and VariableType nodes.
    for (UANode node : nodeSet.getNodeSet().getUAObjectOrUAVariableOrUAMethod()) {
      String nodeId = resolveAlias(node.getNodeId());
      String namespaceUri =
          nodeSet.getNodeSet().getNamespaceUris().getUri().get(getNamespaceIndex(nodeId));

      if (namespaceFilter.test(namespaceUri)) {
        if (node instanceof UAVariable variable) {
          if (variable.getValue() != null) {
            Object any = variable.getValue().getAny();

            try {
              Variant value = decodeXmlValue(variable.getDataType(), any);

              UaVariableNode variableNode =
                  (UaVariableNode)
                      context.getNodeManager().getNode(reindexNodeId(nodeId)).orElseThrow();

              variableNode.setValue(new DataValue(value, StatusCode.GOOD, DateTime.now()));
            } catch (Exception e) {
              logger.warn("Failed to decode XML value for Variable: {}", nodeId, e);
            }
          }
        } else if (node instanceof UAVariableType variableType) {
          if (variableType.getValue() != null) {
            Object any = variableType.getValue().getAny();

            try {
              Variant value = decodeXmlValue(variableType.getDataType(), any);

              UaVariableTypeNode variableTypeNode =
                  (UaVariableTypeNode)
                      context
                          .getNodeManager()
                          .getNode(reindexNodeId(variableType.getNodeId()))
                          .orElseThrow();

              variableTypeNode.setValue(
                  new DataValue(value, StatusCode.GOOD, null, DateTime.now()));
            } catch (Exception e) {
              logger.warn(
                  "Failed to decode XML value for VariableType: {}", variableType.getNodeId(), e);
            }
          }
        }
      }
    }
  }

  private UaDataTypeNode buildDataTypeNode(UADataType dataType) {
    return new UaDataTypeNode(
        context,
        reindexNodeId(dataType.getNodeId()),
        reindexQualifiedName(dataType.getBrowseName()),
        newLocalizedText(dataType.getDisplayName()),
        newLocalizedText(dataType.getDescription()),
        uint(dataType.getWriteMask()),
        uint(dataType.getUserWriteMask()),
        newRolePermissionTypeArray(dataType.getRolePermissions()),
        null,
        newAccessRestrictionType(dataType.getAccessRestrictions()),
        dataType.isIsAbstract(),
        null);
  }

  private UaNode buildMethodNode(UAMethod method) {
    return new UaMethodNode(
        context,
        reindexNodeId(method.getNodeId()),
        reindexQualifiedName(method.getBrowseName()),
        newLocalizedText(method.getDisplayName()),
        newLocalizedText(method.getDescription()),
        uint(method.getWriteMask()),
        uint(method.getUserWriteMask()),
        newRolePermissionTypeArray(method.getRolePermissions()),
        null,
        newAccessRestrictionType(method.getAccessRestrictions()),
        method.isExecutable(),
        method.isUserExecutable());
  }

  private UaNode buildObjectNode(UAObject object) {
    NodeId typeDefinitionId = NodeId.NULL_VALUE;

    List<Reference> references = nodeSet.getExplicitReferences(object.getNodeId());

    for (Reference reference : references) {
      String referenceType = resolveAlias(reference.getReferenceType());

      if (referenceType.equals("i=40")) {
        String typeDefinition = reference.getValue();

        typeDefinitionId = reindexNodeId(resolveAlias(typeDefinition));
        break;
      }
    }

    ObjectTypeManager.ObjectNodeConstructor constructor =
        context
            .getServer()
            .getObjectTypeManager()
            .getNodeConstructor(typeDefinitionId)
            .orElse(null);

    if (constructor != null) {
      UaObjectNode objectNode =
          constructor.apply(
              context,
              reindexNodeId(object.getNodeId()),
              reindexQualifiedName(object.getBrowseName()),
              newLocalizedText(object.getDisplayName()),
              newLocalizedText(object.getDescription()),
              uint(object.getWriteMask()),
              uint(object.getUserWriteMask()),
              newRolePermissionTypeArray(object.getRolePermissions()),
              null,
              newAccessRestrictionType(object.getAccessRestrictions()));

      objectNode.setEventNotifier(ubyte(object.getEventNotifier()));

      return objectNode;
    } else {
      return new UaObjectNode(
          context,
          reindexNodeId(object.getNodeId()),
          reindexQualifiedName(object.getBrowseName()),
          newLocalizedText(object.getDisplayName()),
          newLocalizedText(object.getDescription()),
          uint(object.getWriteMask()),
          uint(object.getUserWriteMask()),
          newRolePermissionTypeArray(object.getRolePermissions()),
          null,
          newAccessRestrictionType(object.getAccessRestrictions()),
          ubyte(object.getEventNotifier()));
    }
  }

  private UaNode buildObjectTypeNode(UAObjectType objectType) {
    return new UaObjectTypeNode(
        context,
        reindexNodeId(objectType.getNodeId()),
        reindexQualifiedName(objectType.getBrowseName()),
        newLocalizedText(objectType.getDisplayName()),
        newLocalizedText(objectType.getDescription()),
        uint(objectType.getWriteMask()),
        uint(objectType.getUserWriteMask()),
        newRolePermissionTypeArray(objectType.getRolePermissions()),
        null,
        newAccessRestrictionType(objectType.getAccessRestrictions()),
        objectType.isIsAbstract());
  }

  private UaNode buildReferenceTypeNode(UAReferenceType referenceType) {
    // Allow the UaReferenceTypeNode to have a null inverseName rather than a "null" inverseName,
    // which results in Bad_AttributeIdUnknown when a Client reads the optional InverseName
    // attribute, and it has not been specified. The CTT is expecting that the attribute does not
    // exist when there is no inverse name, rather than receiving a "null" LocalizedText value.
    org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText inverseName = null;
    if (referenceType.getInverseName() != null && !referenceType.getInverseName().isEmpty()) {
      inverseName = newLocalizedText(referenceType.getInverseName());
    }

    return new UaReferenceTypeNode(
        context,
        reindexNodeId(referenceType.getNodeId()),
        reindexQualifiedName(referenceType.getBrowseName()),
        newLocalizedText(referenceType.getDisplayName()),
        newLocalizedText(referenceType.getDescription()),
        uint(referenceType.getWriteMask()),
        uint(referenceType.getUserWriteMask()),
        newRolePermissionTypeArray(referenceType.getRolePermissions()),
        null,
        newAccessRestrictionType(referenceType.getAccessRestrictions()),
        referenceType.isIsAbstract(),
        referenceType.isSymmetric(),
        inverseName);
  }

  private UaNode buildVariableNode(UAVariable variable) {
    Variant value = Variant.NULL_VALUE;

    NodeId typeDefinitionId = NodeId.NULL_VALUE;

    List<Reference> references = nodeSet.getExplicitReferences(variable.getNodeId());

    for (Reference reference : references) {
      String referenceType = resolveAlias(reference.getReferenceType());

      if (referenceType.equals("i=40")) {
        String typeDefinition = reference.getValue();

        typeDefinitionId = reindexNodeId(resolveAlias(typeDefinition));
        break;
      }
    }

    VariableTypeManager.VariableNodeConstructor constructor =
        context
            .getServer()
            .getVariableTypeManager()
            .getNodeConstructor(typeDefinitionId)
            .orElse(null);

    if (constructor != null) {
      UaVariableNode variableNode =
          constructor.apply(
              context,
              reindexNodeId(variable.getNodeId()),
              reindexQualifiedName(variable.getBrowseName()),
              newLocalizedText(variable.getDisplayName()),
              newLocalizedText(variable.getDescription()),
              uint(variable.getWriteMask()),
              uint(variable.getUserWriteMask()),
              newRolePermissionTypeArray(variable.getRolePermissions()),
              null,
              newAccessRestrictionType(variable.getAccessRestrictions()),
              new DataValue(value, StatusCode.GOOD, DateTime.now()),
              reindexNodeId(variable.getDataType()),
              variable.getValueRank(),
              newArrayDimensions(variable.getArrayDimensions()));

      variableNode.setAccessLevel(ubyte(variable.getAccessLevel()));
      variableNode.setUserAccessLevel(ubyte(variable.getUserAccessLevel()));
      variableNode.setMinimumSamplingInterval(variable.getMinimumSamplingInterval());
      variableNode.setHistorizing(variable.isHistorizing());
      variableNode.setAccessLevelEx(new AccessLevelExType(uint(variable.getAccessLevel())));

      return variableNode;
    } else {
      return new UaVariableNode(
          context,
          reindexNodeId(variable.getNodeId()),
          reindexQualifiedName(variable.getBrowseName()),
          newLocalizedText(variable.getDisplayName()),
          newLocalizedText(variable.getDescription()),
          uint(variable.getWriteMask()),
          uint(variable.getUserWriteMask()),
          newRolePermissionTypeArray(variable.getRolePermissions()),
          null,
          newAccessRestrictionType(variable.getAccessRestrictions()),
          new DataValue(value, StatusCode.GOOD, DateTime.now()),
          reindexNodeId(variable.getDataType()),
          variable.getValueRank(),
          newArrayDimensions(variable.getArrayDimensions()),
          ubyte(variable.getAccessLevel()),
          ubyte(variable.getUserAccessLevel()),
          variable.getMinimumSamplingInterval(),
          variable.isHistorizing(),
          new AccessLevelExType(uint(variable.getAccessLevel())));
    }
  }

  private UaNode buildVariableTypeNode(UAVariableType variableType) {
    Variant value = Variant.NULL_VALUE;

    return new UaVariableTypeNode(
        context,
        reindexNodeId(variableType.getNodeId()),
        reindexQualifiedName(variableType.getBrowseName()),
        newLocalizedText(variableType.getDisplayName()),
        newLocalizedText(variableType.getDescription()),
        uint(variableType.getWriteMask()),
        uint(variableType.getUserWriteMask()),
        newRolePermissionTypeArray(variableType.getRolePermissions()),
        null,
        newAccessRestrictionType(variableType.getAccessRestrictions()),
        new DataValue(value, StatusCode.GOOD, DateTime.NULL_VALUE, DateTime.now()),
        reindexNodeId(variableType.getDataType()),
        variableType.getValueRank(),
        newArrayDimensions(variableType.getArrayDimensions()),
        variableType.isIsAbstract());
  }

  private UaNode buildViewNode(UAView view) {
    return new UaViewNode(
        context,
        reindexNodeId(view.getNodeId()),
        reindexQualifiedName(view.getBrowseName()),
        newLocalizedText(view.getDisplayName()),
        newLocalizedText(view.getDescription()),
        uint(view.getWriteMask()),
        uint(view.getUserWriteMask()),
        newRolePermissionTypeArray(view.getRolePermissions()),
        null,
        newAccessRestrictionType(view.getAccessRestrictions()),
        view.isContainsNoLoops(),
        ubyte(view.getEventNotifier()));
  }

  private void registerDynamicDataType(NodeId dataTypeId) {
    DataTypeTree dataTypeTree = context.getServer().getDataTypeTree();
    DataType dataType = dataTypeTree.getDataType(dataTypeId);
    assert dataType != null;

    var codec = DynamicCodecFactory.create(dataType, dataTypeTree);

    context
        .getServer()
        .getDynamicDataTypeManager()
        .registerType(
            dataTypeId,
            codec,
            dataType.getBinaryEncodingId(),
            dataType.getXmlEncodingId(),
            dataType.getJsonEncodingId());
  }

  private Variant decodeXmlValue(String dataTypeId, Object value) throws Exception {
    StringWriter sw = new StringWriter();
    if (value instanceof JAXBElement<?> jaxbElement) {
      try {
        marshaller.marshal(jaxbElement, sw);
      } catch (JAXBException e) {
        logger.warn("unable to marshal JAXB element: {}", jaxbElement, e);
      }
    } else if (value instanceof Node node) {
      try {
        Transformer transformer = SecureXmlUtil.SHARED_TRANSFORMER_FACTORY.newTransformer();
        transformer.setOutputProperty("omit-xml-declaration", "yes");
        transformer.transform(new DOMSource(node), new StreamResult(sw));
      } catch (TransformerException e) {
        logger.warn("unable to transform dom node: {}", node, e);
      }
    }

    String xmlString = sw.toString();

    OpcUaXmlDecoder decoder =
        new OpcUaXmlDecoder(encodingContext) {
          @Override
          protected NodeId reindexNodeId(NodeId nodeId) {
            String namespaceUri =
                nodeSet
                    .getNodeSet()
                    .getNamespaceUris()
                    .getUri()
                    .get(nodeId.getNamespaceIndex().intValue());

            return nodeId.reindex(context.getServer().getNamespaceTable(), namespaceUri);
          }

          @Override
          protected ExpandedNodeId reindexExpandedNodeId(ExpandedNodeId expandedNodeId) {
            String namespaceUri =
                nodeSet
                    .getNodeSet()
                    .getNamespaceUris()
                    .getUri()
                    .get(expandedNodeId.getNamespaceIndex().intValue());

            return expandedNodeId.reindex(context.getServer().getNamespaceTable(), namespaceUri);
          }

          @Override
          protected QualifiedName reindexQualifiedName(QualifiedName qualifiedName) {
            String namespaceUri =
                nodeSet
                    .getNodeSet()
                    .getNamespaceUris()
                    .getUri()
                    .get(qualifiedName.getNamespaceIndex().intValue());

            return qualifiedName.reindex(context.getServer().getNamespaceTable(), namespaceUri);
          }
        };

    decoder.setInput(new StringReader(xmlString));
    Object valueObject = decoder.decodeVariantValue();

    if (valueObject instanceof ExtensionObject xo) {
      // Transcode the ExtensionObject from its XML encoding to Binary encoding.
      // We have to roll our own transcoding instead of using ExtensionObject::transcode
      // so that we can use the OpcUaXmlDecoder declared above, which is set up for reindexing.

      try {
        DataType dataType =
            context.getServer().getDataTypeTree().getDataType(reindexNodeId(dataTypeId));

        if (dataType == null) {
          throw new IllegalStateException("DataType not found for NodeId: " + dataTypeId);
        }

        DataTypeCodec codec =
            encodingContext.getDataTypeManager().getCodec(xo.getEncodingOrTypeId());

        if (codec != null) {
          XmlElement xmlBody = (XmlElement) xo.getBody();
          String xml = xmlBody.getFragmentOrEmpty();

          try {
            decoder.setInput(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
          } catch (IOException | SAXException e) {
            throw new UaSerializationException(StatusCodes.Bad_DecodingError, e);
          }

          UaStructuredType decoded = decoder.decodeStruct(null, codec);

          NodeId binaryEncodingId = dataType.getBinaryEncodingId();
          assert binaryEncodingId != null;

          valueObject = ExtensionObject.encode(encodingContext, decoded);
        } else {
          throw new UaSerializationException(
              StatusCodes.Bad_DecodingError,
              "no codec registered for encodingId=" + xo.getEncodingOrTypeId());
        }
      } catch (Exception e) {
        logger.warn("Failed to transcode ExtensionObject: {}", xo, e);
      }
    }

    return Variant.of(valueObject);
  }

  /**
   * Re-index {@code nodeId} from its original namespace index to the corresponding index in the
   * Server for its original namespace URI.
   *
   * @param nodeIdString a NodeId String from a {@link UANodeSet}.
   * @return a {@link NodeId} that has been re-indexed for the current server.
   */
  protected NodeId reindexNodeId(String nodeIdString) {
    nodeIdString = resolveAlias(nodeIdString);

    int namespaceIndex = getNamespaceIndex(nodeIdString);

    if (namespaceIndex == 0) {
      return NodeId.parse(nodeIdString);
    } else {
      return NodeId.parse(nodeIdString)
          .reindex(
              context.getServer().getNamespaceTable(),
              nodeSet.getNodeSet().getNamespaceUris().getUri().get(namespaceIndex));
    }
  }

  /**
   * Re-index {@code qualifiedName} from its original namespace index to the corresponding index in
   * the Server for its original namespace URI.
   *
   * @param qualifiedName a QualifiedName String from a {@link UANodeSet}.
   * @return a {@link QualifiedName} that has been re-indexed for the current server.
   */
  protected QualifiedName reindexQualifiedName(String qualifiedName) {
    Matcher matcher = IndexUtil.PATTERN_QUALIFIED_NAME.matcher(qualifiedName);

    if (matcher.matches()) {
      int namespaceIndex = Integer.parseInt(matcher.group(1));

      if (namespaceIndex == 0) {
        return new QualifiedName(0, qualifiedName);
      } else {
        return new QualifiedName(namespaceIndex, matcher.group(2))
            .reindex(
                context.getServer().getNamespaceTable(),
                nodeSet.getNodeSet().getNamespaceUris().getUri().get(namespaceIndex));
      }
    } else {
      return new QualifiedName(0, qualifiedName);
    }
  }

  protected org.eclipse.milo.opcua.sdk.core.Reference reindexReference(
      UANode sourceNode, Reference reference) {
    NodeId sourceNodeId = reindexNodeId(sourceNode.getNodeId());
    NodeId referenceTypeId = reindexNodeId(reference.getReferenceType());
    NodeId targetNodeId = reindexNodeId(reference.getValue());

    return new org.eclipse.milo.opcua.sdk.core.Reference(
        sourceNodeId,
        referenceTypeId,
        targetNodeId.expanded(context.getServer().getNamespaceTable()),
        reference.isIsForward());
  }

  private String resolveAlias(String nodeIdString) {
    // TODO put these into a map to improve lookup performance
    AliasTable aliasTable = nodeSet.getNodeSet().getAliases();
    if (aliasTable != null) {
      List<NodeIdAlias> aliases = aliasTable.getAlias();
      for (NodeIdAlias alias : aliases) {
        if (alias.getAlias().equals(nodeIdString)) {
          return alias.getValue();
        }
      }
    }
    return nodeIdString;
  }

  private AccessRestrictionType newAccessRestrictionType(@Nullable Integer value) {
    if (value == null) {
      value = 0;
    }
    return new AccessRestrictionType(ushort(value));
  }

  private org.eclipse.milo.opcua.stack.core.types.structured.@Nullable DataTypeDefinition
      newDataTypeDefinition(UADataType dataType, DataTypeInfoTree dataTypeTree) {

    DataTypeDefinition definition = dataType.getDefinition();

    if (definition == null) {
      // This is expected for some DataTypes, e.g. simple/alias types.
      LoggerFactory.getLogger(getClass())
          .debug(
              "DataType {} has no DataTypeDefinition",
              getParseableIdentifier(dataType.getNodeId()));
      return null;
    }

    DataTypeInfo typeInfo = dataTypeTree.getTypeInfo(dataType.getNodeId());

    if (dataTypeTree.isEnumeration(dataType.getNodeId())) {
      return new EnumDefinition(
          definition.getField().stream().map(this::newEnumField).toArray(EnumField[]::new));
    } else if (dataTypeTree.isStructure(dataType.getNodeId())) {
      NodeId baseDataType = NodeId.NULL_VALUE;
      if (typeInfo != null && typeInfo.getParent() != null) {
        baseDataType = NodeId.parse(typeInfo.getParent().getTypeNode().getNodeId());
      }

      if (dataTypeTree.isOptionSet(dataType.getNodeId())) {
        // OptionSets are a special case and use EnumDefinition.
        return new EnumDefinition(
            definition.getField().stream().map(this::newEnumField).toArray(EnumField[]::new));
      }

      StructureType structureType = StructureType.Structure;

      if (dataTypeTree.isStructureWithOptionalFields(dataType.getNodeId())) {
        structureType = StructureType.StructureWithOptionalFields;
      } else if (dataTypeTree.isStructureWithSubtypedValues(dataType.getNodeId())) {
        structureType = StructureType.StructureWithSubtypedValues;
      } else if (dataTypeTree.isUnionWithSubtypedValues(dataType.getNodeId())) {
        structureType = StructureType.UnionWithSubtypedValues;
      } else if (dataTypeTree.isUnion(dataType.getNodeId())) {
        structureType = StructureType.Union;
      }

      // TODO find HasEncoding reference to "Default Binary" encoding?
      Stream<DataTypeField> fieldStream = definition.getField().stream();
      if (typeInfo != null) {
        fieldStream = Stream.concat(typeInfo.getInheritedFields().stream(), fieldStream);
      }

      return new StructureDefinition(
          NodeId.NULL_VALUE,
          baseDataType,
          structureType,
          fieldStream.map(this::newStructureField).toArray(StructureField[]::new));
    } else {
      return null;
    }
  }

  private EnumField newEnumField(DataTypeField field) {
    return new EnumField(
        (long) field.getValue(),
        newLocalizedText(field.getDisplayName()),
        newLocalizedText(field.getDescription()),
        field.getName());
  }

  private StructureField newStructureField(DataTypeField field) {
    return new StructureField(
        field.getName(),
        newLocalizedText(field.getDescription()),
        reindexNodeId(field.getDataType()),
        field.getValueRank(),
        newArrayDimensions(field.getArrayDimensions()),
        uint(field.getMaxStringLength()),
        field.isIsOptional() || field.isAllowSubTypes());
  }

  private UInteger[] newArrayDimensions(String arrayDimensions) {
    if (arrayDimensions == null || arrayDimensions.isEmpty()) {
      return null;
    }

    String[] dimensions = arrayDimensions.split(",");

    UInteger[] uintArray = new UInteger[dimensions.length];

    for (int i = 0; i < dimensions.length; i++) {
      uintArray[i] = uint(Integer.parseInt(dimensions[i]));
    }

    return uintArray;
  }

  private org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText newLocalizedText(
      List<LocalizedText> displayName) {

    String locale = null;
    String text = null;

    if (displayName != null && !displayName.isEmpty()) {
      LocalizedText localizedText = displayName.get(0);
      locale = localizedText.getLocale();
      text = localizedText.getValue();
    }

    return new org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText(locale, text);
  }

  private RolePermissionType[] newRolePermissionTypeArray(
      @Nullable ListOfRolePermissions rolePermissions) {
    var rolePermissionTypes = new ArrayList<RolePermissionType>();

    if (rolePermissions != null) {
      for (RolePermission rolePermission : rolePermissions.getRolePermission()) {
        NodeId roleId = reindexNodeId(rolePermission.getValue());
        PermissionType permissions = new PermissionType(uint(rolePermission.getPermissions()));

        rolePermissionTypes.add(new RolePermissionType(roleId, permissions));
      }
    }

    return rolePermissionTypes.toArray(new RolePermissionType[0]);
  }

  private static int getNamespaceIndex(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);
    if (matcher.matches()) {
      return Integer.parseInt(matcher.group(1));
    } else {
      return 0;
    }
  }

  private static String getParseableIdentifier(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);
    if (matcher.matches()) {
      return matcher.group(2);
    } else {
      return nodeId;
    }
  }
}
