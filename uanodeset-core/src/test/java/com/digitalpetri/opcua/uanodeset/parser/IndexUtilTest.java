package com.digitalpetri.opcua.uanodeset.parser;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.milo.opcua.stack.core.util.Namespaces;
import org.junit.jupiter.api.Test;
import org.opcfoundation.ua.UriTable;

class IndexUtilTest {

  @Test
  public void testReindexNodeId() {
    var nodeId = "ns=1;s=test";

    var merged = new UriTable();
    merged.getUri().add(Namespaces.OPC_UA);
    merged.getUri().add("uri1");
    merged.getUri().add("uri2");

    var original = new UriTable();
    original.getUri().add(Namespaces.OPC_UA);
    original.getUri().add("uri2");

    String reindexed = IndexUtil.reindexNodeId(nodeId, merged, original);

    assertEquals("ns=2;s=test", reindexed);
  }

  @Test
  public void testReindexNodeIdNoOp() {
    var nodeId = "ns=1;s=test";

    var merged = new UriTable();
    merged.getUri().add(Namespaces.OPC_UA);
    merged.getUri().add("uri1");

    var original = new UriTable();
    original.getUri().add(Namespaces.OPC_UA);
    original.getUri().add("uri1");

    String reindexed = IndexUtil.reindexNodeId(nodeId, merged, original);

    assertEquals("ns=1;s=test", reindexed);
  }

  @Test
  public void testReindexNodeIdThrowsOnMissingUri() {
    var nodeId = "ns=1;s=test";

    var merged = new UriTable();
    merged.getUri().add(Namespaces.OPC_UA);
    merged.getUri().add("uri1");

    var original = new UriTable();
    original.getUri().add(Namespaces.OPC_UA);
    original.getUri().add("uri2");

    assertThrows(
        IllegalArgumentException.class, () -> IndexUtil.reindexNodeId(nodeId, merged, original));
  }

  @Test
  public void testReindexQualifiedName() {
    var qualifiedName = "1:Foo";

    var merged = new UriTable();
    merged.getUri().add("uri1");
    merged.getUri().add("uri2");

    var original = new UriTable();
    original.getUri().add("uri2");

    String reindexed = IndexUtil.reindexQualifiedName(qualifiedName, merged, original);

    assertEquals("2:Foo", reindexed);
  }

  @Test
  public void testReindexQualifiedNameThrowsOnMissingUri() {
    var qualifiedName = "1:Foo";

    var merged = new UriTable();
    merged.getUri().add("uri1");

    var original = new UriTable();
    original.getUri().add("uri2");

    assertThrows(
        IllegalArgumentException.class,
        () -> IndexUtil.reindexQualifiedName(qualifiedName, merged, original));
  }
}
