/**
 * Public APIs for parsing, normalizing, indexing, and inspecting OPC UA UANodeSet XML models.
 *
 * <h2>Responsibilities</h2>
 *
 * <p>The package starts from JAXB-generated {@link org.opcfoundation.ua.UANodeSet} objects and
 * turns them into a {@link NodeSetContext} that tools can query without repeatedly reimplementing
 * namespace, alias, reference, and base-model handling. {@link NodeSet} is the main entry point for
 * callers that need a merged model containing the bundled OPC UA base NodeSet plus one or more
 * companion or vendor NodeSets.
 *
 * <h2>Type Hierarchies</h2>
 *
 * <p>{@link TypeInfo} and {@link TypeInfoTree} expose the OPC UA {@code HasSubtype} hierarchy as a
 * small public tree API. Concrete trees, such as {@link DataTypeInfoTree}, {@link
 * ObjectTypeInfoTree}, {@link VariableTypeInfoTree}, and {@link ReferenceTypeInfoTree}, are rooted
 * at the standard OPC UA root types and provide traversal, lookup by NodeId, and subtype checks.
 *
 * <h2>Boundaries</h2>
 *
 * <p>This package models OPC UA facts that are useful outside a single code generator: parsed
 * nodes, resolved references, datatype classification, and generic type inheritance. Java package
 * names, generated class names, and other language-specific generation policy belong in downstream
 * codegen modules.
 */
package com.digitalpetri.opcua.uanodeset;
