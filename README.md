**History Stages**

History Stages is a progression and gatekeeping mod for
Minecraft 1.20.1 (Forge). It allows modpack creators to
implement a global "Era" system by locking items, recipes,
dimensions, mobs, and mod content behind custom research stages.

----------------------------------------------------------------
1. KEY FEATURES
----------------------------------------------------------------

- Global Progression: When a stage is unlocked, it is
  available for everyone on the server.
- Deep Gating: Lock content by Item ID, Recipe ID, Tags,
  or entire Mod IDs.
- Recipe Locking: Hide specific crafting recipes from
  crafting menus by recipe ID.
- Dimension Access: Prevent players from entering specific
  dimensions (Nether, End, etc.) without the required stage.
- Item Usage Lock: Prevent players from using locked items
  (equipping armor, using weapons, eating food, etc.).
- Block Breaking Lock: Locked blocks are much harder to break
  (configurable speed multiplier) and drop nothing.
- Entity Control: Two lock modes for entities:
  - Attacklock: Prevent players from damaging specific mobs.
  - Spawnlock: Prevent entities from spawning entirely.
    Spawnlocked entities are also automatically attacklocked.
- Smart Loot: Locked items are removed from chests or replaced
  with configurable items.
- Mob Loot Lock: Locked items are removed from mob drops.
- Lock Icon Overlay: Locked items show a lock icon in
  inventories and JEI (auto-disabled with EMI).
- Research System: Uses a Research Pedestal and Research Scrolls
  with configurable research time per stage.
- In-Game Stage Editor: Full GUI for creating, editing,
  duplicating, and deleting stages without leaving the game.
- In-Game Config Editor: Modify all mod settings from within
  the game with organized categories and reset-to-defaults.
- JEI/EMI Support: Automatically hides or marks locked items.
- Jade Support: Shows stage information on locked blocks in
  the Jade block overlay.
- Debug Logging: Comprehensive diagnostic reports with
  config validation, registry checks, and stage content
  overview. Runtime event logging for tracking stage
  changes, blocked actions, and inventory issues.
- FTB Quests Support: Custom task type (check if a stage is
  unlocked) and reward type (unlock/lock a stage) for seamless
  quest-based progression.
- Forge Events: Fires StageEvent.Unlocked/Locked events for
  KubeJS, CraftTweaker, and other mod integrations.
- Toast Notifications: Advancement-style popup notifications
  when stages are unlocked.
- Localization: English and German translations included.

----------------------------------------------------------------
2. HOW TO USE
----------------------------------------------------------------

STAGES:
Stages are defined via JSON files in: /config/historystages/
Example format:
{
  "display_name": "Bronze Age",
  "research_time": 60,
  "items": ["minecraft:iron_ingot"],
  "tags": ["forge:ores/iron"],
  "mods": ["mekanism"],
  "recipes": ["minecraft:iron_pickaxe"],
  "dimensions": ["minecraft:the_nether"],
  "entities": {
    "attacklock": ["minecraft:zombie"],
    "spawnlock": ["minecraft:skeleton"]
  }
}

FIELDS:
- display_name: Human-readable name shown in messages and tooltips.
- research_time: (Optional) Research duration in seconds for this
  stage. If omitted or 0, uses the global config default.
- items: List of item IDs to lock (e.g. "minecraft:diamond").
- tags: List of item tags to lock (e.g. "forge:ores/iron").
- mods: List of mod IDs to lock all items from (e.g. "mekanism").
- recipes: List of recipe IDs to hide from crafting menus.
- dimensions: List of dimension IDs to block access to.
- entities: Object with two optional subcategories:
  - attacklock: Entities that cannot be attacked by players.
  - spawnlock: Entities that are prevented from spawning entirely.
    Spawnlocked entities are also automatically attacklocked.

Note: JSON files prefixed with underscore (e.g. _exampleStage.json)
are ignored during loading.

RESEARCH SCROLLS & PEDESTAL:
1. Place a Research Pedestal.
2. Put a Research Scroll into the input slot.
3. Wait for the research process to finish.
4. The stage is now unlocked globally.

The pedestal emits light (level 13) while actively researching
and progress is saved in the scroll item's NBT data.

IMPORTANT: This mod does NOT include default crafting recipes
for the pedestal or scrolls. You MUST add them yourself using
KubeJS, CraftTweaker, or a Datapack.

----------------------------------------------------------------
3. IN-GAME STAGE EDITOR
----------------------------------------------------------------

History Stages includes a full in-game editor for creating,
editing, duplicating, and deleting stages. No need to leave
the game or manually edit JSON files. All stage configs and
mod settings can be managed directly from within the GUI.

----------------------------------------------------------------
4. ADMIN COMMANDS (Permission Level 2)
----------------------------------------------------------------

/history unlock <stage>  - Unlocks a stage. Use '*' for all.
/history lock <stage>    - Relocks a stage. Use '*' for all.
/history list            - Shows all registered stages.
/history info <stage>    - Shows details (items, mods, dims, entities).
/history reload          - Reloads JSONs and syncs players.

----------------------------------------------------------------
5. OBTAINING SCROLLS VIA COMMAND
----------------------------------------------------------------

/give @s historystages:research_scroll{StageResearch:"stage_id"}

(Replace "stage_id" with your JSON filename, e.g. "bronze_age")

----------------------------------------------------------------
6. CONFIGURATION
----------------------------------------------------------------

CLIENT CONFIG (per player):
- hideInJei: Hide locked items from JEI/EMI.
- showTooltips: Show information tooltips on locked items.
- showStageName: Show required stage name in tooltips.
- showAllUntilComplete: Show all required stages until unlocked.
- showLockIcons: Show lock icon overlay on locked items
  (auto-disabled with EMI).
- Jade settings: jadeShowInfo, jadeStageName,
  jadeShowAllUntilComplete (requires Jade mod).
- Dimension lock feedback: Actionbar and/or chat messages.
- Mob lock feedback: Actionbar and/or chat messages.

COMMON CONFIG (server-side):
- showWelcomeMessage: Display welcome message on player join.
- showDebugErrors: Show config validation errors in chat.
- lockMobLoot: Remove locked items from mob drops.
- lockBlockBreaking: Make locked blocks harder to break and
  prevent their drops (default: true).
- lockedBlockBreakSpeedMultiplier: Break speed multiplier for
  locked blocks (default: 0.05 = 20x slower).
- lockItemUsage: Prevent using locked items (default: true).
- lockEntityItems: Prevent interacting with or breaking armor
  stands and item frames containing locked items (default: true).
- broadcastChat: Broadcast unlock/lock messages to all players.
- unlockMessageFormat: Customize the unlock message text
  (supports {stage} placeholder and & color codes).
- useActionbar: Show messages in actionbar.
- useSounds: Play notification sounds.
- useToasts: Show advancement-style toast popups.
- researchTimeInSeconds: Default research duration (default: 20s).
- enableRuntimeLogging: Log runtime events (stage changes,
  blocked actions, inventory tracking) to file (default: false).
- useReplacements: Replace locked loot with alternative items.
- replacementItems: List of replacement item IDs.
- replacementTag: List of item tags for replacement fallback.

Config files are located at:
- Client: /config/historystages-client.toml
- Common: /config/historystages-common.toml

----------------------------------------------------------------
7. FTB QUESTS INTEGRATION
----------------------------------------------------------------

If FTB Quests is installed, History Stages adds two new types
to the FTB Quests editor:

TASK - "History Stage":
  Checks if a specific History Stage is globally unlocked.
  The task auto-completes once the stage is unlocked (checks
  every second). Configure the Stage ID in the quest editor.

REWARD - "History Stage":
  Unlocks (or locks) a History Stage when the quest reward is
  claimed. Uses the same broadcast effects as the Research
  Pedestal (chat messages, actionbar, sounds, toasts) based
  on your mod config settings.
  - Stage ID: The stage to unlock/lock.
  - Lock instead of Unlock: If enabled, locks the stage instead.

The integration is fully optional and crash-safe. If FTB Quests
is not installed, the mod works normally without it.

----------------------------------------------------------------
8. FORGE EVENTS (FOR MOD/SCRIPT AUTHORS)
----------------------------------------------------------------

History Stages fires custom Forge events on the EVENT_BUS:

- StageEvent.Unlocked: Fired after a stage is unlocked
  (via command, Research Pedestal, or FTB Quests reward).
- StageEvent.Locked: Fired after a stage is locked
  (via command or FTB Quests reward).

Both events are also fired by FTB Quests rewards.
Both events provide: getStageId() and getDisplayName().

KubeJS example:
ForgeEvents.onEvent(
  'net.bananemdnsa.historystages.events.StageEvent$Unlocked',
  event => {
    console.log('Stage unlocked: ' + event.getStageId());
  }
);

----------------------------------------------------------------
9. DEPENDENCIES
----------------------------------------------------------------

- Required: Lootr
- Optional: JEI (recipe viewer integration)
- Optional: EMI (alternative recipe viewer integration)
- Optional: Jade (block overlay integration)

- Optional: FTB Quests (quest task and reward integration)

----------------------------------------------------------------
License: GPL-3.0
Authors: Flix100000, PixlStudios
================================================================
