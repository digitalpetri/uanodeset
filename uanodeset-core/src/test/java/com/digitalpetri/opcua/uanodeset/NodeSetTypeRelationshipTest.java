/*
 * Copyright (c) 2026 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.digitalpetri.opcua.uanodeset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.opcua.uanodeset.parser.UANodeSetParser;
import com.digitalpetri.opcua.uanodeset.util.NodeIdUtil;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opcfoundation.ua.Reference;
import org.opcfoundation.ua.UANode;
import org.opcfoundation.ua.UANodeSet;
import org.opcfoundation.ua.UAObjectType;

class NodeSetTypeRelationshipTest {

  private NodeSet nodeSet;

  @BeforeEach
  void loadNodeSet() throws JAXBException, IOException {
    try (InputStream inputStream = nodeSetInputStream()) {
      nodeSet = NodeSet.load(inputStream);
    }
  }

  @Nested
  class SemanticNodeIds {

    // Namespace-zero prefixes are optional in UANodeSet XML; callers must not miss the same node
    // because a producer chose the longer spelling.
    @Test
    void namespaceZeroSpellingsResolveTheSameNode() {
      UANode compact = nodeSet.getNode("i=58");

      assertSame(compact, nodeSet.getNode("ns=0;i=58"));
      assertSame(compact, nodeSet.getNode(NodeIds.BaseObjectType));
    }

    // References are indexed by their source NodeId, so semantic source lookup must be as stable
    // as direct node lookup for hierarchy construction and relationship resolution.
    @Test
    void namespaceZeroSpellingsResolveTheSameReferenceBuckets() {
      assertEquals(nodeSet.getReferences("i=58"), nodeSet.getReferences("ns=0;i=58"));
      assertEquals(
          nodeSet.getExplicitReferences("i=58"),
          nodeSet.getExplicitReferences(NodeIds.BaseObjectType));
      assertEquals(
          nodeSet.getImplicitReferences("i=58"), nodeSet.getImplicitReferences("ns=0;i=58"));
    }

    // Type-tree indexes are part of the same model query boundary and must not reintroduce raw
    // String equality after NodeSet lookup has normalized identifiers.
    @Test
    void namespaceZeroSpellingsResolveTheSameTypeInfo() {
      ObjectTypeInfoTree tree = ObjectTypeInfoTree.create(nodeSet);
      ObjectTypeInfo compact = tree.getTypeInfo("i=58");

      assertSame(compact, tree.getTypeInfo("ns=0;i=58"));
      assertSame(compact, tree.getTypeInfo(NodeIds.BaseObjectType));
    }
  }

  @Nested
  class TypeDefinitions {

    // HasTypeDefinition points from an instance to its type, while NodeSet files may serialize the
    // equivalent inverse declaration on the type; both must drive the same model relationship.
    @ParameterizedTest(name = "{2}")
    @CsvSource({
      "2000, 1002, forward declaration on the instance",
      "2001, 1000, inverse declaration on the type"
    })
    void forwardAndInverseDeclarationsResolveTheSameRelationshipContract(
        int instanceIdentifier, int typeIdentifier, String referenceForm) {

      Optional<NodeId> typeDefinition = nodeSet.getTypeDefinition(nodeId(instanceIdentifier));

      assertEquals(Optional.of(nodeId(typeIdentifier)), typeDefinition, referenceForm);
    }
  }

  @Nested
  class TypeHierarchies {

    // Behavior registered on an ObjectType must reach an instance through every known intermediate
    // type, regardless of whether each HasSubtype edge was serialized forward or inverse.
    @Test
    void objectHierarchyReturnsDirectTypeThenAllKnownSupertypes() {
      assertEquals(
          List.of(nodeId(1002), nodeId(1001), nodeId(1000), NodeIds.BaseObjectType),
          nodeSet.getTypeHierarchy(nodeId(2000)));
    }

    // Variable behavior matching needs the same model-space inheritance semantics as Object
    // behavior matching; Milo's runtime VariableTypeTree is not the authority for this chain.
    @Test
    void variableHierarchyReturnsDirectTypeThenAllKnownSupertypes() {
      assertEquals(
          List.of(
              nodeId(1102),
              nodeId(1101),
              nodeId(1100),
              NodeIds.BaseDataVariableType,
              NodeIds.BaseVariableType),
          nodeSet.getTypeHierarchy(nodeId(2100)));
    }

    // Exact matching includes the direct type, while subtype matching is intentionally strict so
    // consumers cannot accidentally treat a type as its own subtype.
    @Test
    void exactAndStrictSubtypeQueriesHaveDistinctSemantics() {
      ObjectTypeInfoTree tree = ObjectTypeInfoTree.create(nodeSet);

      assertTrue(tree.isTypeOrSubtypeOf(nodeId(1000), nodeId(1000)));
      assertFalse(tree.isSubtypeOf(nodeId(1000), nodeId(1000)));
      assertTrue(tree.isSubtypeOf(nodeId(1002), nodeId(1000)));
    }

    // Companion models are often loaded without every dependency; preserving the direct type
    // enables exact behavior without inventing an unsupported supertype match.
    @ParameterizedTest(name = "instance {0} keeps direct type {1}")
    @CsvSource({"2002, 1999", "2200, 1200"})
    void unknownOrDisconnectedHierarchyReturnsOnlyTheDirectType(
        int instanceIdentifier, int typeIdentifier) {

      assertEquals(
          List.of(nodeId(typeIdentifier)), nodeSet.getTypeHierarchy(nodeId(instanceIdentifier)));
    }
  }

  @Nested
  class MalformedHierarchies {

    // A type cycle has no valid most-general endpoint and previously caused unbounded recursive
    // tree construction; rejecting it gives model authors a deterministic diagnostic.
    @Test
    void cycleIsRejectedBeforeTreeTraversal() throws JAXBException, IOException {
      UANodeSet extension = parseExtension();
      UAObjectType baseType = objectType(extension, 1000);
      Reference parentReference = inverseHasSubtypeReference(baseType);
      parentReference.setValue("ns=1;i=1002");
      NodeSet malformed = NodeSet.from(extension);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> ObjectTypeInfoTree.create(malformed));

      assertTrue(exception.getMessage().contains("cycle detected"));
    }

    // OPC UA type inheritance has one direct supertype; accepting two parents makes callback order
    // and inherited declarations ambiguous.
    @Test
    void multipleParentsAreRejected() throws JAXBException, IOException {
      UANodeSet extension = parseExtension();
      UAObjectType leafType = objectType(extension, 1002);
      Reference secondParent = new Reference();
      secondParent.setIsForward(false);
      secondParent.setReferenceType("ns=0;i=45");
      secondParent.setValue("ns=1;i=1000");
      leafType.getReferences().getReference().add(secondParent);
      NodeSet malformed = NodeSet.from(extension);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> ObjectTypeInfoTree.create(malformed));

      assertTrue(exception.getMessage().contains("multiple supertypes"));
    }
  }

  private static NodeId nodeId(int identifier) {
    return new NodeId(1, identifier);
  }

  private static InputStream nodeSetInputStream() {
    return Objects.requireNonNull(
        NodeSetTypeRelationshipTest.class.getResourceAsStream("/TypeRelationships.NodeSet2.xml"));
  }

  private static UANodeSet parseExtension() throws JAXBException, IOException {
    try (InputStream inputStream = nodeSetInputStream()) {
      return UANodeSetParser.parse(inputStream);
    }
  }

  private static UAObjectType objectType(UANodeSet nodeSet, int identifier) {
    String expectedNodeId = "ns=1;i=" + identifier;
    return nodeSet.getUAObjectOrUAVariableOrUAMethod().stream()
        .filter(UAObjectType.class::isInstance)
        .map(UAObjectType.class::cast)
        .filter(type -> type.getNodeId().equals(expectedNodeId))
        .findFirst()
        .orElseThrow();
  }

  private static Reference inverseHasSubtypeReference(UAObjectType type) {
    return type.getReferences().getReference().stream()
        .filter(reference -> !reference.isIsForward())
        .filter(
            reference ->
                reference.getReferenceType().equals("HasSubtype")
                    || NodeIdUtil.equals(NodeIds.HasSubtype, reference.getReferenceType()))
        .findFirst()
        .orElseThrow();
  }
}
