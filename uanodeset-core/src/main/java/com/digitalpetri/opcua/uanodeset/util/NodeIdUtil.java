package com.digitalpetri.opcua.uanodeset.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;

import com.digitalpetri.opcua.uanodeset.parser.IndexUtil;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

public final class NodeIdUtil {

  private static final ConcurrentMap<NodeId, String> STRING_VALUES = new ConcurrentHashMap<>();

  public static String get(NodeId nodeId) {
    return STRING_VALUES.computeIfAbsent(nodeId, id -> {
      String s = id.toParseableString();
      if (s.startsWith("ns=0;")) {
        int index = s.indexOf(";");
        return s.substring(index + 1);
      } else {
        return s;
      }
    });
  }

  public static boolean equals(NodeId nodeId, String nodeIdString) {
    return equals(nodeIdString, nodeId);
  }

  public static boolean equals(String nodeIdString, NodeId nodeId) {
    return nodeIdString.equals(get(nodeId));
  }

  public static int getNamespaceIndex(String nodeId) {
    Matcher matcher = IndexUtil.PATTERN_NODE_ID.matcher(nodeId);
    if (matcher.matches()) {
      return Integer.parseInt(matcher.group(1));
    } else {
      return 0;
    }
  }

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
