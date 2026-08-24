package net.bananemdnsa.historystages.api;

/**
 * A signpost and a version marker for the HistoryStages addon API.
 *
 * <p>Deliberately holds no registration method. Everything is registered through the NeoForge
 * mod-bus events named {@code Register…Event}, one per extension point, because that is the shape
 * a Minecraft mod author already knows — and a static facade beside it would be a second way to
 * do the same thing. What is left worth putting in one place is the generation number and a map
 * of where to look.
 *
 * <h2>Start here</h2>
 *
 * <table>
 *   <caption>The five extension points and what each one is for</caption>
 *   <tr><th>You want to…</th><th>Look at</th></tr>
 *   <tr><td>gate a new kind of thing</td>
 *       <td>{@link net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent},
 *           {@link net.bananemdnsa.historystages.api.lock.AddonLockCategory}</td></tr>
 *   <tr><td>add a new way to earn a stage</td>
 *       <td>{@link net.bananemdnsa.historystages.api.dependency.RegisterRequirementTypesEvent},
 *           {@link net.bananemdnsa.historystages.api.dependency.AddonRequirement}</td></tr>
 *   <tr><td>unlock a stage automatically</td>
 *       <td>{@link net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent},
 *           {@link net.bananemdnsa.historystages.api.trigger.TriggerCondition}</td></tr>
 *   <tr><td>put your own settings on every stage</td>
 *       <td>{@link net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent}</td></tr>
 *   <tr><td>put your own section in the config screen</td>
 *       <td>{@link net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent}</td></tr>
 *   <tr><td>ask whether one of your things is gated right now</td>
 *       <td>{@link net.bananemdnsa.historystages.api.lock.CategoryLocks}</td></tr>
 *   <tr><td>unlock or relock a stage yourself</td>
 *       <td>{@link net.bananemdnsa.historystages.api.stage.StageStates}</td></tr>
 *   <tr><td>react when a stage changes</td>
 *       <td>{@link net.bananemdnsa.historystages.api.stage.StageEvent}</td></tr>
 *   <tr><td>give any of the above an editor tab that looks built-in</td>
 *       <td>{@code api.editor} and {@code api.editor.widget}</td></tr>
 * </table>
 *
 * <p>The working example is the demo addon under {@code net.bananemdnsa.historystages.demo}. It
 * exercises all five extension points, draws one of its tabs itself, and — enforced by a test —
 * reaches for nothing outside this package.
 */
public final class HistoryStagesAPI {

    /**
     * The generation of this API surface.
     *
     * <p>Bumped when something here changes in a way an addon would notice, which is the same
     * moment the change goes in the changelog. Not tied to the mod version: a release that only
     * fixes behaviour leaves this alone.
     */
    public static final int API_VERSION = 1;

    private HistoryStagesAPI() {}
}