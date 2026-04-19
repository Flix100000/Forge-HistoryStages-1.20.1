# **History Stages API & REFACTOR BRANCH**
> [!WARNING]
> This is a development branch. For release versions and main project code refer to `1.20.x` branch. This branch is kept up to date with the main branch, however it features  breaking changes, significant code refactors and incomplete features


# History Stages (Original Description)
History Stages is a progression and gatekeeping mod for
Minecraft 1.20.1 (Forge). It allows modpack creators to
implement a global "Era" system and per-player progression
by locking items, recipes, dimensions, mobs, and mod content
behind custom research stages.

----------------------------------------------------------------
1. KEY FEATURES
----------------------------------------------------------------

PROGRESSION MODES:
- Global Stages: When a stage is unlocked, it is available
  for everyone on the server.
- Individual Stages: Per-player progression with UUID-based
  persistence. Each player can have their own unlocked stages
  independently from global progression.

CONTENT LOCKING:
- Deep Gating: Lock content by Item ID, Recipe ID, Tags,
  or entire Mod IDs.
- NBT Item Locking: Lock items by specific NBT criteria
  (e.g., only Sharpness 1-4 enchanted books instead of all
  enchanted books). Includes a visual NBT editor with property
  tree, autocomplete, and validation warnings.
- Mod Exceptions: Exclude specific items from mod-wide locking
  using the mod_exceptions field, with full NBT support.
- Recipe Locking: Locked recipes show a visual "Locked" overlay
  in JEI/EMI instead of being hidden, working universally
  across all recipe types (vanilla and modded).
- Dimension Access: Prevent players from entering specific
  dimensions (Nether, End, etc.) without the required stage.
  Dimensions can overlap between global and individual stages.
- Item Usage Lock: Prevent players from using locked items
  (equipping armor, using weapons, eating food, etc.).
- Block Breaking Lock: Locked blocks are much harder to break
  (configurable speed multiplier) and drop nothing.
- Block GUI Lock: Prevent opening the GUI of locked blocks
  (chests, furnaces, etc.) for both global and individual stages.
- Container Interaction Lock: Prevent moving individually-locked
  items in containers.
- Enchantment Lock: Prevent applying locked enchantments via
  anvil and enchanting table.
- Entity Control: Two lock modes for entities:
  - Attacklock: Prevent players from damaging specific mobs.
  - Spawnlock: Prevent entities from spawning entirely.
    Spawnlocked entities are also automatically attacklocked.
- Smart Loot: Locked items are removed from chests or replaced
  with configurable items.
- Mob Loot Lock: Locked items are removed from mob drops.
- Entity Item Protection: Prevent interacting with or breaking
  armor stands and item frames containing locked items.

RESEARCH SYSTEM:
- Research Pedestal and Research Scrolls with configurable
  research time per stage.
- Creative Scroll: Unlocks all stages when researched in the
  pedestal.
- Individual stage scrolls show owner name in tooltip and
  pedestal GUI.

EDITOR & UI:
- In-Game Stage Editor: Full GUI for creating, editing,
  duplicating, and deleting stages without leaving the game.
  Supports both global and individual stages with overlay
  creation dialog and global overlap warnings.
- Search Bar: Filterable stage overview by display name and
  stage ID.
- Inventory Mode: Item and entity pickers support both
  registry browsing and player inventory selection.
- NBT Editor: Visual editor for NBT-based item locking with
  autocomplete and validation.
- In-Game Config Editor: Modify all mod settings from within
  the game with organized categories and reset-to-defaults.
- Lock Icon Overlay: Locked items show a lock icon in
  inventories and JEI. Gold icon for global stages, silver
  icon for individual stages (auto-disabled with EMI).

INTEGRATIONS:
- JEI/EMI Support: Locked items are marked and locked recipes
  show a visual overlay across all recipe types.
- Jade Support: Shows stage information on locked blocks,
  armor stands, and item frames. Supports both global and
  individual stages.
- FTB Quests Support: Custom task type (check if a stage is
  unlocked) and reward type (unlock/lock a stage) for both
  global and individual stages. Event-driven task completion.
- Forge Events: Fires StageEvent.Unlocked/Locked events for
  KubeJS, CraftTweaker, and other mod integrations.

OTHER:
- Toast Notifications: Advancement-style popup notifications
  when stages are unlocked.
- Debug Logging: Comprehensive diagnostic reports with
  config validation, registry checks, and stage content
  overview. Runtime event logging for tracking stage
  changes, blocked actions, and inventory issues.
- Multiplayer Stability: Thread-safe caching, editor sync
  across admins, and config persistence with client sync.
- Localization: English and German translations included.

----------------------------------------------------------------
2. HOW TO USE
----------------------------------------------------------------

GLOBAL STAGES:
Global stages are defined via JSON files in:
  /config/historystages/global/
When a global stage is unlocked, it applies to all players.

INDIVIDUAL STAGES:
Individual (per-player) stages are defined via JSON files in:
  /config/historystages/individual/
Each player unlocks individual stages independently.
Dimensions and entities can overlap between global and
individual stages.

Example format:
{
  "display_name": "Bronze Age",
  "research_time": 60,
  "items": ["minecraft:iron_ingot"],
  "tags": ["forge:ores/iron"],
  "mods": ["mekanism"],
  "mod_exceptions": ["mekanism:configurator"],
  "recipes": ["minecraft:iron_pickaxe"],
  "dimensions": ["minecraft:the_nether"],
  "entities": {
    "attacklock": ["minecraft:zombie"],
    "spawnlock": ["minecraft:skeleton"]
  }
}

Items can also be specified with NBT criteria:
{
  "items": [
    "minecraft:diamond_sword",
    {
      "id": "minecraft:enchanted_book",
      "nbt": {
        "StoredEnchantments": [
          {"id": "minecraft:sharpness", "lvl": "1-4"}
        ]
      }
    }
  ]
}

FIELDS:
- display_name: Human-readable name shown in messages and tooltips.
- research_time: (Optional) Research duration in seconds for this
  stage. If omitted or 0, uses the global config default.
- items: List of item IDs or objects with id+nbt to lock.
  Supports NBT-based matching for granular item control.
- tags: List of item tags to lock (e.g. "forge:ores/iron").
- mods: List of mod IDs to lock all items from (e.g. "mekanism").
- mod_exceptions: List of item IDs to exclude from mod-wide
  locking (with full NBT support).
- recipes: List of recipe IDs to lock (shown with overlay).
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
4. The stage is now unlocked (globally or for the player,
   depending on the scroll type).

The pedestal emits light (level 13) while actively researching
and progress is saved in the scroll item's NBT data.

Special scrolls:
- Creative Scroll: Unlocks all stages when researched.
- Individual scrolls show the owner's name in the tooltip
  and pedestal GUI. Locked individual scroll slots are
  grayed out for other players.

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

GLOBAL STAGE COMMANDS:
/history global unlock <stage>  - Unlocks a global stage. '*' for all.
/history global lock <stage>    - Relocks a global stage. '*' for all.
/history global list            - Shows all global stages.
/history global info <stage>    - Shows stage details.

INDIVIDUAL STAGE COMMANDS:
/history individual unlock <player> <stage>  - Unlocks for a player.
/history individual lock <player> <stage>    - Relocks for a player.
/history individual list <player>            - Shows player's stages.
/history individual info <stage>             - Shows stage details.

Supports multi-player selectors (@a, @p, etc.) and '*' wildcard
for stages.

GENERAL COMMANDS:
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
- lockBlockGUI: Prevent opening GUI of locked blocks like
  chests and furnaces (default: true).
- lockContainerInteraction: Prevent moving individually-locked
  items in containers (default: true).
- lockEnchanting: Prevent applying locked enchantments via
  anvil and enchanting table (default: true).
- broadcastChat: Broadcast unlock/lock messages to all players.
- unlockMessageFormat: Customize the unlock message text
  (supports {stage} placeholder and & color codes).
- individualUnlockMessageFormat: Customize the individual unlock
  message text (supports {stage} and {player} placeholders).
- useActionbar: Show messages in actionbar.
- useSounds: Play notification sounds.
- useToasts: Show advancement-style toast popups.
- researchTimeInSeconds: Default research duration (default: 20s).
- enableRuntimeLogging: Log runtime events (stage changes,
  blocked actions, inventory tracking) to file (default: false).
- useReplacements: Replace locked loot with alternative items.
- replacementItems: List of replacement item IDs.
- replacementTag: List of item tags for replacement fallback.

Individual stages have their own independent config toggles for
block breaking (with separate speed multiplier), item usage,
and notification options (chat, actionbar, sounds, toasts).

Config files are located at:
- Client: /config/historystages-client.toml
- Common: /config/historystages-common.toml

----------------------------------------------------------------
7. FTB QUESTS INTEGRATION
----------------------------------------------------------------

If FTB Quests is installed, History Stages adds two new types
to the FTB Quests editor:

TASK - "History Stage":
  Checks if a specific History Stage is unlocked (global or
  individual). Event-driven: auto-completes when the stage is
  unlocked, and checks on player login for already-unlocked
  stages. Configure the Stage ID in the quest editor.

REWARD - "History Stage":
  Unlocks (or locks) a History Stage when the quest reward is
  claimed. Supports both global and individual stages. Uses
  the same broadcast effects as the Research Pedestal (chat
  messages, actionbar, sounds, toasts) based on your config.
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
  'net.astr0.historystages.api.events.StageEvent$Unlocked',
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
