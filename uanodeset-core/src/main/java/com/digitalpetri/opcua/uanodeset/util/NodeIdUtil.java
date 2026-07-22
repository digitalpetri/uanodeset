package com.digitalpetri.opcua.uanodeset.util;

import com.digitalpetri.opcua.uanodeset.parser.IndexUtil;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/** Utilities for converting between UANodeSet NodeId strings and semantic {@link NodeId}s. */
public final class NodeIdUtil {

  private static final ConcurrentMap<NodeId, String> STRING_VALUES = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, NodeId> PARSED_VALUES = new ConcurrentHashMap<>();

  /**
   * Format a NodeId using the compact namespace-zero spelling used by most UANodeSet files.
   *
   * @param nodeId the NodeId to format.
   * @return the parseable NodeId string, without an explicit {@code ns=0;} prefix.
   */
  public static String get(NodeId nodeId) {
    return STRING_VALUES.computeIfAbsent(
        nodeId,
        id -> {
          String s = id.toParseableString();
          if (s.startsWith("ns=0;")) {
            int index = s.indexOf(";");
            return s.substring(index + 1);
          } else {
            return s;
          }
        });
  }

  /**
   * Parse a UANodeSet NodeId string.
   *
   * <p>Equivalent namespace-zero spellings, such as {@code i=58} and {@code ns=0;i=58}, produce
   * equal {@link NodeId} values.
   *
   * @param nodeId the NodeId string to parse.
   * @return the parsed NodeId.
   */
  public static NodeId parse(String nodeId) {
    return PARSED_VALUES.computeIfAbsent(nodeId, NodeId::parse);
  }

  /**
   * Compare a NodeId with a UANodeSet NodeId string by semantic value.
   *
   * @param nodeId the NodeId to compare.
   * @param nodeIdString the NodeId string to compare.
   * @return {@code true} when both values identify the same node.
   */
  public static boolean equals(NodeId nodeId, String nodeIdString) {
    return equals(nodeIdString, nodeId);
  }

  /**
   * Compare a UANodeSet NodeId string with a NodeId by semantic value.
   *
   * @param nodeIdString the NodeId string to compare.
   * @param nodeId the NodeId to compare.
   * @return {@code true} when both values identify the same node.
   */
  public static boolean equals(String nodeIdString, NodeId nodeId) {
    return parse(nodeIdString).equals(nodeId);
  }

  /**
   * Get the namespace index encoded in a UANodeSet NodeId string.
   *
   * @param nodeId the NodeId string to inspect.
   * @return the encoded namespace index, or {@code 0} when it is omitted.
   */
  public static int getNamespaceIndex(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);
    if (matcher.matches()) {
      return Integer.parseInt(matcher.group(1));
    } else {
      return 0;
    }
  }

  /**
   * Get the identifier portion of a UANodeSet NodeId string.
   *
   * @param nodeId the NodeId string to inspect.
   * @return the identifier without a namespace prefix.
   */
  public static String getParseableIdentifier(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);
    if (matcher.matches()) {
      return matcher.group(2);
    } else {
      return nodeId;
    }
  }

  private NodeIdUtil() {}
}
