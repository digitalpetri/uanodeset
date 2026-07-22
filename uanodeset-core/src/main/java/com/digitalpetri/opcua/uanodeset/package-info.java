/**
 * Public APIs for parsing, normalizing, indexing, and inspecting OPC UA UANodeSet XML models.
 *
 * <h2>Responsibilities</h2>
 *
 * <p>The package starts from JAXB-generated {@link org.opcfoundation.ua.UANodeSet} objects and
 * turns them into a {@link NodeSetContext} that tools can query without repeatedly reimplementing
 * namespace, alias, reference, and base-model handling. {@link NodeSet} is the main entry point for
 * callers that need a merged model containing the bundled OPC UA base NodeSet plus one or more
 * companion or vendor NodeSets. Node and reference indexes use semantic {@link
 * org.eclipse.milo.opcua.stack.core.types.builtin.NodeId} values, so equivalent namespace-zero
 * spellings resolve identically. These NodeIds use indexes from the merged model's namespace URI
 * table; downstream runtimes remain responsible for reindexing at their boundary.
 *
 * <h2>Type Hierarchies</h2>
 *
 * <p>{@link TypeInfo} and {@link TypeInfoTree} expose the OPC UA {@code HasSubtype} hierarchy as a
 * small public tree API. Concrete trees, such as {@link DataTypeInfoTree}, {@link
 * ObjectTypeInfoTree}, {@link VariableTypeInfoTree}, and {@link ReferenceTypeInfoTree}, are rooted
 * at the standard OPC UA root types and provide traversal, lookup by NodeId, and strict subtype
 * checks. {@link NodeSet#getTypeHierarchy(org.eclipse.milo.opcua.stack.core.types.builtin.NodeId)}
 * resolves an Object or Variable instance to its direct type and known supertypes without exposing
 * tree-parent traversal to consumers. Missing companion types remain exact-only rather than being
 * assigned speculative ancestry.
 *
 * <h2>References</h2>
 *
 * <p>{@link NodeSetContext#getTypeDefinition(org.opcfoundation.ua.UANode)} resolves {@code
 * HasTypeDefinition} from the combined explicit and implicit reference view. The result is
 * independent of whether XML declares the relationship forward on the instance or inverse on the
 * type.
 *
 * <h2>Ownership and validation</h2>
 *
 * <p>{@code NodeSet} normalizes and indexes the supplied JAXB model and exposes that model for
 * inspection. Callers should treat the normalized nodes and references as read-only after
 * construction because semantic indexes and lazily built type trees represent the construction-time
 * model. Type-tree creation rejects known cycles and types with multiple declared supertypes;
 * missing companion types remain disconnected rather than being assigned inferred parents.
 *
 * <h2>Boundaries</h2>
 *
 * <p>This package models OPC UA facts that are useful outside a single code generator: parsed
 * nodes, resolved references, datatype classification, and generic type inheritance. Java package
 * names, generated class names, and other language-specific generation policy belong in downstream
 * codegen modules.
 */
package com.digitalpetri.opcua.uanodeset;
