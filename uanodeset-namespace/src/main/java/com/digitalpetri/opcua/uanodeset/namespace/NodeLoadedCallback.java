package com.digitalpetri.opcua.uanodeset.namespace;

/**
 * Attaches behavior or performs initialization after a NodeSet node completes the load phases.
 *
 * <p>The registry uses the same callback contract for type, specific-node, and catch-all
 * registrations. {@link NodeMatch} lets a shared callback distinguish which registration caused
 * each invocation without reconstructing type ancestry or relying on which registration method
 * received its method reference.
 */
@FunctionalInterface
public interface NodeLoadedCallback {

  /**
   * Handle a node after all load phases have completed.
   *
   * <p>Callbacks run synchronously during NodeSet loading. Inspect {@code match} with {@code
   * instanceof} to distinguish {@link NodeMatch.Type}, {@link NodeMatch.SpecificNode}, and {@link
   * NodeMatch.AnyNode}. A node may be passed to the same callback more than once when that callback
   * has multiple matching registrations. Any exception thrown by a callback aborts the load.
   *
   * @param loadedNode the post-load node and its loading context.
   * @param match the registration match that caused this invocation.
   */
  void onNodeLoaded(LoadedNode loadedNode, NodeMatch match);
}
