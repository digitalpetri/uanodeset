package com.digitalpetri.opcua.uanodeset;

import com.digitalpetri.opcua.uanodeset.parser.IndexUtil;
import com.digitalpetri.opcua.uanodeset.parser.UANodeSetMerger;
import com.digitalpetri.opcua.uanodeset.parser.UANodeSetParser;

import jakarta.xml.bind.JAXBException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.jspecify.annotations.Nullable;
import org.opcfoundation.ua.*;

/**
 * Holds a UANodeSet and does some post-processing to make it a little more usable.
 *
 * <ul>
 *   <li>The URI table contains the base OPC UA namespace URI and the UANodeSet contains the base
 *       OPC UA model
 *   <li>Fields containing NodeIds are replaced with NodeIds that have had any potential alias
 *       resolved
 *   <li>RolePermissions and AccessRestrictions defined at the model level have been applied to
 *       individual UANodes
 *   <li>Implicit References are created for all References that are not explicitly defined in the
 *       UANodeSet
 * </ul>
 */
public class NodeSet implements NodeSetContext {

  private final Map<String, String> aliases = new HashMap<>();
  private final Map<String, UANode> nodeMap = new HashMap<>();

  private final CombinedReferences combinedReferences = new CombinedReferences();
  private final Map<String, List<Reference>> explicitReferences = new HashMap<>(); 
  private final Map<String, List<Reference>> implicitReferences = new HashMap<>(); 

  private final UANodeSet nodeSet;

  /**
   * Create a new NodeSet from a UANodeSet.
   *
   * <p>This UANodeSet must contain the base OPC UA model.
   *
   * <p>Typically, this will be a merged UANodeSet containing the base model and some other model(s)
   * that are being loaded into an address space or fed to a code generator.
   *
   * @param nodeSet the UANodeSet to create a NodeSet from.
   */
  public NodeSet(UANodeSet nodeSet) {
    this.nodeSet = nodeSet;

    assert nodeSet.getModels().getModel().stream()
        .anyMatch(e -> Objects.equals(Namespaces.OPC_UA, e.getModelUri()));

    AliasTable aliasTable = nodeSet.getAliases();
    if (aliasTable != null) {
      aliasTable.getAlias().forEach(nia -> aliases.put(nia.getAlias(), nia.getValue()));
    }

    UriTable namespaceUris = nodeSet.getNamespaceUris();
    if (namespaceUris == null) {
      namespaceUris = new UriTable();
      namespaceUris.getUri().add(Namespaces.OPC_UA);
      nodeSet.setNamespaceUris(namespaceUris);
    }

    var rolePermissionsByModelUri = new HashMap<String, Optional<ListOfRolePermissions>>();
    var accessRestrictionsByModelUri = new HashMap<String, Optional<Integer>>();

    nodeSet
        .getUAObjectOrUAVariableOrUAMethod()
        .forEach(
            node -> {
              node.setNodeId(resolveAlias(node.getNodeId()));

              if (node instanceof UADataType dataType) {
                DataTypeDefinition definition = dataType.getDefinition();

                if (definition != null) {
                  definition
                      .getField()
                      .forEach(field -> field.setDataType(resolveAlias(field.getDataType())));
                }
              }

              if (node instanceof UAVariable variable) {
                variable.setDataType(resolveAlias(variable.getDataType()));
              }

              if (node instanceof UAVariableType variableType) {
                variableType.setDataType(resolveAlias(variableType.getDataType()));
              }

              // TODO other nodes with aliases that need resolving?

              // Maybe set RolePermissions from the model
              if (!node.isHasNoPermissions()) {
                ListOfRolePermissions nodeRolePermissions = node.getRolePermissions();

                if (nodeRolePermissions == null
                    || nodeRolePermissions.getRolePermission().isEmpty()) {

                  Optional<ListOfRolePermissions> modelRolePermissions =
                      rolePermissionsByModelUri.computeIfAbsent(
                          getNamespaceUri(node.getNodeId()),
                          namespaceUri -> {
                            Optional<ModelTableEntry> modelTableEntry =
                                nodeSet.getModels().getModel().stream()
                                    .filter(e -> namespaceUri.equals(e.getModelUri()))
                                    .findFirst();

                            return modelTableEntry.flatMap(
                                e -> Optional.ofNullable(e.getRolePermissions()));
                          });

                  modelRolePermissions.ifPresent(node::setRolePermissions);
                }
              }

              // Maybe set AccessRestrictions from the model
              if (node.getAccessRestrictions() == null) {
                Optional<Integer> modelAccessRestrictions =
                    accessRestrictionsByModelUri.computeIfAbsent(
                        getNamespaceUri(node.getNodeId()),
                        namespaceUri -> {
                          Optional<ModelTableEntry> modelTableEntry =
                              nodeSet.getModels().getModel().stream()
                                  .filter(e -> namespaceUri.equals(e.getModelUri()))
                                  .findFirst();

                          return modelTableEntry.flatMap(
                              e -> Optional.of(e.getAccessRestrictions()));
                        });

                modelAccessRestrictions.ifPresent(node::setAccessRestrictions);
              }

              nodeMap.put(node.getNodeId(), node);

              ListOfReferences references = node.getReferences();

              // resolve Reference aliases and add explicit/implicit References
              if (references != null) {
                references
                    .getReference()
                    .forEach(
                        reference -> {
                          reference.setValue(resolveAlias(reference.getValue()));
                          reference.setReferenceType(resolveAlias(reference.getReferenceType()));
                          explicitReferences.computeIfAbsent(node.getNodeId(), k -> new ArrayList<>()).add(reference);
                          var inverse = new Reference();
                          inverse.setValue(node.getNodeId());
                          inverse.setIsForward(!reference.isIsForward());
                          inverse.setReferenceType(reference.getReferenceType());
                          implicitReferences.computeIfAbsent(reference.getValue(), k -> new ArrayList<>()).add(inverse);
                        });
              }
            });
  }

  @Override
  public UANodeSet getNodeSet() {
    return nodeSet;
  }

  @Override
  public @Nullable UANode getNode(String nodeId) {
    return nodeMap.get(nodeId);
  }

  @Override
  public List<Reference> getReferences(String nodeId) {
    return combinedReferences.get(nodeId);
  }

  @Override
  public List<Reference> getExplicitReferences(String nodeId) {
    return explicitReferences.getOrDefault(nodeId, Collections.emptyList());
  }

  @Override
  public List<Reference> getImplicitReferences(String nodeId) {
    return implicitReferences.getOrDefault(nodeId, Collections.emptyList());
  }

  private String getNamespaceUri(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);

    int namespaceIndex = 0;
    if (matcher.matches()) {
      namespaceIndex = Integer.parseInt(matcher.group(1));
    }

    return nodeSet.getNamespaceUris().getUri().get(namespaceIndex);
  }

  private String resolveAlias(String nodeIdOrAlias) {
    return aliases.getOrDefault(nodeIdOrAlias, nodeIdOrAlias);
  }

  private class CombinedReferences {

    private final Map<String, List<Reference>> references = new HashMap<>();

    private List<Reference> get(String nodeId) {
      return references.computeIfAbsent(nodeId, id -> {
        var combined = new LinkedHashSet<ReferenceWrapper>();
        getExplicitReferences(nodeId).stream().map(ReferenceWrapper::new).forEach(combined::add);
        getImplicitReferences(nodeId).stream().map(ReferenceWrapper::new).forEach(combined::add);
        return combined.stream().map(ReferenceWrapper::get).toList();
      });
    }

    private static class ReferenceWrapper {
      private final Reference reference;

      private ReferenceWrapper(Reference reference) {
        this.reference = reference;
      }

      public Reference get() {
        return reference;
      }

      private boolean equivalent(Reference a, Reference b) {
        if (a == b) {
          return true;
        }
        if (a == null || b == null) {
          return false;
        }
        return Objects.equals(a.getValue(), b.getValue()) && Objects.equals(a.getReferenceType(), b.getReferenceType())
            && a.isIsForward() == b.isIsForward();
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (obj instanceof ReferenceWrapper other) {
          return equivalent(this.reference, other.reference);
        }
        return false;
      }

      @Override
      public int hashCode() {
        if (reference == null)
          return 0;
        return Objects.hash(reference.getReferenceType(), reference.getValue(), reference.isIsForward());
      }
    }
  }
  
  public static NodeSet load(InputStream inputStream) throws JAXBException {
    return load(Collections.singletonList(inputStream));
  }

  public static NodeSet load(List<InputStream> inputStreams) throws JAXBException {
    var nodeSets = new ArrayList<UANodeSet>();
    for (InputStream inputStream : inputStreams) {
      nodeSets.add(UANodeSetParser.parse(inputStream));
    }

    // merge the base OPC UA NodeSet with the provided NodeSets
    UANodeSet mergedNodeSet =
        UANodeSetParser.parse(
            NodeSet.class.getClassLoader().getResourceAsStream("1.05/Opc.Ua.NodeSet2.xml"));

    for (UANodeSet nodeSet : nodeSets) {
      mergedNodeSet = UANodeSetMerger.merge(mergedNodeSet, nodeSet);
    }

    return new NodeSet(mergedNodeSet);
  }
}
