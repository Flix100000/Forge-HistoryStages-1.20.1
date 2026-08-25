/**
 * The public surface of HistoryStages — what an addon implements, calls, and can rely on.
 *
 * <p>Everything outside this package is internal: it may be public in the Java sense, because a
 * facade in here has to reach it, but it carries no promise and can be renamed, split or deleted
 * without notice.
 *
 * <p>The promise is <em>not</em> "this never changes". It is <strong>"what changes here is in the
 * changelog, and breaking changes wait for a major version"</strong>. What an addon author gets is
 * not permanence but warning.
 *
 * <p>Two tests hold that line rather than a convention. {@code ApiSurfaceGuardTest} fails when a
 * type in here names an internal type in a public signature — which would drag that type into the
 * contract whether anyone meant it or not. {@code DemoUsesOnlyApiTest} fails when the demo addon
 * has to reach past this package, because whatever the demo needs, a real addon needs too.
 *
 * <p>A few types the api hands you — {@code StageEntry} above all — live in {@code data} rather
 * than here. That is not an oversight: {@code StageEntry} is the mod's central data structure,
 * named in over a hundred files by the loader, the editor, the network layer and the graph, and
 * moving it in here would mislabel it. They are as stable as anything in this package.
 *
 * <p><strong>How to use any of this is documented in the wiki:</strong>
 * <a href="https://github.com/Flix100000/History-Stages/wiki/Addon-Development">Addon
 * Development</a>.
 */
package net.bananemdnsa.historystages.api;
