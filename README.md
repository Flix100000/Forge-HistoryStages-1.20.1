**History Stages**

History Stages is a progression and gatekeeping mod for
Minecraft. It allows modpack creators to
implement a global "Era" system and per-player progression
by locking items, recipes, dimensions, mobs, and mod content
behind custom research stages.

For full documentation on commands, configuration, Forge
events, and advanced usage, see the Wiki:
https://github.com/Flix100000/History-Stages/wiki

----------------------------------------------------------------
1. KEY FEATURES
----------------------------------------------------------------

PROGRESSION MODES:
- Global Stages: When a stage is unlocked, it is available
  for everyone on the server.
- Individual Stages: Per-player progression with UUID-based
  persistence. Each player can have their own unlocked stages
  independently from global progression.
- Dual-Phase Stages: Items, tags, or mods that appear in both
  a global and an individual stage are automatically dual-phase
  locked. Phase 1 (global): the item is locked for everyone
  until all paired global stages are unlocked. Phase 2
  (individual): once the global phase is complete, the item
  is locked per-player until that player unlocks their
  individual stage. Dual-phase entries are marked with [Dual]
  in the editor and shown with a dedicated lock icon in
  inventories.

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
- Structure Lock: Prevent players from entering specific
  structures until the required stage is unlocked. Supports
  plain structure IDs (e.g. minecraft:stronghold) and structure
  tags via a # prefix (e.g. #minecraft:village). Optionally
  deals damage while inside a locked structure and blocks
  container/spawner interactions.
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

STAGE DEPENDENCIES:
- Stages can require prerequisites before they can be unlocked:
  - Entity Kills: Kill a specified number of certain entities.
  - XP Level: Reach a required experience level.
  - Statistics: Reach a certain value in any Minecraft statistic.
  - Individual Stage: Another per-player stage must be unlocked.
- Dependencies are configured via a visual Dependency Editor in
  the in-game stage editor and are validated client-server.
- Multiple dependencies can be grouped; all must be met before
  the stage can be unlocked.

RESEARCH SYSTEM:
- Research Pedestal and Research Scrolls with configurable
  research time per stage.
- The pedestal GUI expands dynamically to show a dependency
  side panel with a checklist of all required conditions.
- Items and XP can be deposited directly into the pedestal to
  satisfy deposit-based dependencies.
- Owner Protection: Only the scroll owner can interact with
  their scroll slot in the pedestal.
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
  icon for individual stages, dedicated icon for dual-phase
  Phase 1 (auto-disabled with EMI).

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
  when stages are unlocked. Each stage can define a custom
  item icon for its toast; a global fallback icon is
  configurable via defaultStageIcon in the config.
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

DUAL-PHASE STAGES:
If an item, tag, or mod entry appears in both a global and an
individual stage config, it is automatically treated as
dual-phase. No extra configuration is needed. The game log
will show an INFO message confirming the registration.
Phase 1 ends when all global stages containing that entry are
globally unlocked. If a player already has the individual
stage unlocked when Phase 1 completes, they gain access
immediately.

Example format:
{
  "display_name": "Bronze Age",
  "research_time": 60,
  "icon": "minecraft:iron_ingot",
  "items": ["minecraft:iron_ingot"],
  "tags": ["forge:ores/iron"],
  "mods": ["mekanism"],
  "mod_exceptions": ["mekanism:configurator"],
  "recipes": ["minecraft:iron_pickaxe"],
  "dimensions": ["minecraft:the_nether"],
  "structures": ["minecraft:stronghold", "#minecraft:village"],
  "entities": {
    "attacklock": ["minecraft:zombie"],
    "spawnlock": ["minecraft:skeleton"]
  },
  "dependencies": [
    {"type": "xp_level", "level": 10},
    {"type": "entity_kills", "entity": "minecraft:zombie", "count": 20},
    {"type": "statistic", "stat": "minecraft:killed.minecraft:zombie", "value": 20},
    {"type": "individual_stage", "stage": "stone_age"}
  ]
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
- icon: (Optional) Item ID shown as the icon in unlock toast
  notifications. Falls back to the global defaultStageIcon config.
- items: List of item IDs or objects with id+nbt to lock.
  Supports NBT-based matching for granular item control.
- tags: List of item tags to lock (e.g. "forge:ores/iron").
- mods: List of mod IDs to lock all items from (e.g. "mekanism").
- mod_exceptions: List of item IDs to exclude from mod-wide
  locking (with full NBT support).
- recipes: List of recipe IDs to lock (shown with overlay).
- dimensions: List of dimension IDs to block access to.
- structures: List of structure IDs or tag IDs (# prefix) to
  block access to (e.g. "minecraft:stronghold", "#minecraft:village").
- entities: Object with two optional subcategories:
  - attacklock: Entities that cannot be attacked by players.
  - spawnlock: Entities that are prevented from spawning entirely.
    Spawnlocked entities are also automatically attacklocked.
- dependencies: (Optional) List of prerequisite conditions that
  must be met before this stage can be unlocked. Each entry has
  a "type" field and type-specific parameters:
  - xp_level: Requires reaching a minimum XP level.
    Parameters: "level" (integer).
  - entity_kills: Requires killing a number of specific entities.
    Parameters: "entity" (entity ID), "count" (integer).
  - statistic: Requires reaching a value in a Minecraft statistic.
    Parameters: "stat" (statistic ID), "value" (integer).
  - individual_stage: Requires another individual stage to be
    unlocked for this player. Parameters: "stage" (stage ID).

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
4. ADMIN COMMANDS & CONFIGURATION
----------------------------------------------------------------

See the Wiki for a full command reference, all config options,
and Forge event documentation:
https://github.com/Flix100000/History-Stages/wiki

Quick reference:
/history global unlock|lock <stage>
/history individual unlock|lock <player> <stage>
/history reload
/history debug structure|nbt preset|custom

Scrolls can also be obtained via command:
/give @s historystages:research_scroll{StageResearch:"stage_id"}

----------------------------------------------------------------
5. FTB QUESTS INTEGRATION
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
6. DEPENDENCIES
----------------------------------------------------------------

- Required: Lootr
- Optional: JEI
- Optional: EMI
- Optional: Jade
- Optional: FTB Quests

----------------------------------------------------------------
License: GPL-3.0
Authors: Flix100000, PixlStudios
================================================================
