/**
 * The public surface of HistoryStages — what an addon implements, calls, and can rely on.
 *
 * <h2>What the promise is</h2>
 *
 * This is the addon-facing surface. Everything outside this package is internal: it may be
 * public in the Java sense, because a facade in here has to reach it, but it carries no promise
 * and can be renamed, split or deleted without notice.
 *
 * <p>The promise is <em>not</em> "this never changes". It is <strong>"what changes here is in
 * the changelog"</strong>. A widget that grows a better layout or a method that gets a clearer
 * name is a design decision worth making; hiding from it for a decade is not. What an addon
 * author gets is not permanence but warning.
 *
 * <p>Two tests hold the line rather than a convention. {@code ApiSurfaceGuardTest} fails when a
 * type in here names an internal type in a public signature — which would drag that type into
 * the contract whether anyone meant it or not. {@code DemoUsesOnlyApiTest} fails when the demo
 * addon has to reach past this package, because whatever the demo needs, a real addon needs too.
 *
 * <h2>Where things are</h2>
 *
 * One sub-package per extension point, plus the editor toolkit that all of them share:
 *
 * <ul>
 *   <li>{@code api.stage} — scopes, the "what is unlocked" view, unlocking and relocking, and
 *       the event fired when either happens.</li>
 *   <li>{@code api.lock} — new kinds of gated thing. Register a category, say when one of your
 *       entries gates one of your objects, and ask whether it is gated right now.</li>
 *   <li>{@code api.dependency} — new kinds of "what must be done to unlock this stage".</li>
 *   <li>{@code api.trigger} — new kinds of "what unlocks this stage by itself".</li>
 *   <li>{@code api.settings} — a group of your own settings on every stage.</li>
 *   <li>{@code api.config} — a section of your own in the mod's config screen. Note the
 *       asymmetry with settings, and it is deliberate: a stage setting belongs to the stage and
 *       is stored by us, a config value belongs to you and is stored by you. We lend the screen.</li>
 *   <li>{@code api.editor} and {@code api.editor.widget} — the tabs, entry actions and widgets
 *       the built-in editor is made of. Using them is what makes an addon's tab look like it
 *       shipped with the mod, which is the point of the whole platform.</li>
 * </ul>
 *
 * <h2>How you plug in</h2>
 *
 * Through NeoForge mod-bus events, one per extension point, all named {@code Register…Event}.
 * That is the shape a Minecraft mod author already knows, and it is why there is no
 * {@code HistoryStagesAPI.register(...)} facade next to it — a second way to do the same thing
 * is worse than one familiar way. {@link net.bananemdnsa.historystages.api.HistoryStagesAPI}
 * exists as a signpost and a version marker, not as an entry point.
 *
 * <p>The types you register name no loader: a category, a requirement or a settings group is
 * plain Java. Only the ten postboxes mention NeoForge, and a test keeps it that way.
 *
 * <h2>The stage data types</h2>
 *
 * A few types the api hands you — {@code StageEntry} above all — live in {@code data} rather
 * than here. That is not an oversight. {@code StageEntry} is the mod's central data structure,
 * named in over a hundred files by the loader, the editor, the network layer and the graph;
 * moving it in here would mislabel it. They are as stable as anything in this package. In
 * practice you are unlikely to meet them: the demo addon exercises all five extension points
 * and names exactly one.
 */
package net.bananemdnsa.historystages.api;
