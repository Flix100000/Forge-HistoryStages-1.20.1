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
- Deep Gating: Lock content by Item ID, Tags, or entire Mod IDs.
- Dimension Access: Prevent players from entering specific
  dimensions (Nether, End, etc.) without the required stage.
- Mob Protection: Prevent players from damaging specific mobs
  until the required stage is unlocked.
- Smart Loot: Locked items are removed from chests or replaced
  with configurable items like Cobblestone.
- Mob Loot Lock: Locked items are removed from mob drops.
- Lock Icon Overlay: Locked items show a lock icon in
  inventories and JEI (auto-disabled with EMI).
- Research System: Uses a Research Pedestal and Research Scrolls
  with configurable research time per stage.
- JEI/EMI Support: Automatically hides or marks locked items.
- Forge Events: Fires StageEvent.Unlocked/Locked events for
  KubeJS, CraftTweaker, and other mod integrations.
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
  "dimensions": ["minecraft:the_nether"],
  "entities": ["minecraft:zombie", "minecraft:skeleton"]
}

FIELDS:
- display_name: Human-readable name shown in messages and tooltips.
- research_time: (Optional) Research duration in seconds for this
  stage. If omitted or 0, uses the global config default.
- items: List of item IDs to lock (e.g. "minecraft:diamond").
- tags: List of item tags to lock (e.g. "forge:ores/iron").
- mods: List of mod IDs to lock all items from (e.g. "mekanism").
- dimensions: List of dimension IDs to block access to.
- entities: List of entity IDs to protect from player damage.

RESEARCH SCROLLS & PEDESTAL:
1. Place a Research Pedestal.
2. Put a Research Scroll into the input slot.
3. Wait for the research process to finish.
4. The stage is now unlocked globally.

IMPORTANT: This mod does NOT include default crafting recipes
for the pedestal or scrolls. You MUST add them yourself using
KubeJS, CraftTweaker, or a Datapack.

----------------------------------------------------------------
3. ADMIN COMMANDS (Permission Level 2)
----------------------------------------------------------------

/history unlock <stage>  - Unlocks a stage. Use '*' for all.
/history lock <stage>    - Relocks a stage. Use '*' for all.
/history list            - Shows all registered stages.
/history info <stage>    - Shows details (items, mods, dims, entities).
/history reload          - Reloads JSONs and syncs players.

----------------------------------------------------------------
4. OBTAINING SCROLLS VIA COMMAND
----------------------------------------------------------------

/give @s historystages:research_scroll{StageResearch:"stage_id"}

(Replace "stage_id" with your JSON filename, e.g. "bronze_age")

----------------------------------------------------------------
5. CONFIGURATION
----------------------------------------------------------------

CLIENT CONFIG (per player):
- hideInJei: Hide locked items from JEI.
- showTooltips: Show required stages on locked items.
- showLockIcons: Show lock icon overlay on locked items.
- Dimension lock feedback: Actionbar and/or chat messages.
- Mob lock feedback: Actionbar and/or chat messages.

COMMON CONFIG (server-side):
- lockMobLoot: Remove locked items from mob drops.
- broadcastChat: Broadcast unlock/lock messages to all players.
- unlockMessageFormat: Customize the unlock message text.
- researchTimeInSeconds: Default research duration (fallback if
  a stage does not define its own 'research_time' in the JSON).
- useReplacements: Replace locked loot with alternative items.

Config files are located at:
- Client: /config/historystages-client.toml
- Common: /config/historystages-common.toml

----------------------------------------------------------------
6. FORGE EVENTS (FOR MOD/SCRIPT AUTHORS)
----------------------------------------------------------------

HistoryStages fires custom Forge events on the EVENT_BUS:

- StageEvent.Unlocked: Fired after a stage is unlocked
  (via command or Research Pedestal).
- StageEvent.Locked: Fired after a stage is locked (via command).

Both events provide: getStageId() and getDisplayName().

KubeJS example:
ForgeEvents.onEvent(
  'net.bananemdnsa.historystages.events.StageEvent$Unlocked',
  event => {
    console.log('Stage unlocked: ' + event.getStageId());
  }
);

----------------------------------------------------------------
7. DEPENDENCIES
----------------------------------------------------------------

- Required: Lootr
- Optional: JEI (recipe viewer integration)
- Optional: EMI (alternative recipe viewer integration)

----------------------------------------------------------------
8. NOTE: IN DEVELOPMENT
----------------------------------------------------------------

This mod is currently in active development. Bugs may occur.
Please report any issues to help improve the mod.

----------------------------------------------------------------
License: MIT
Authors: Flix100000, PixlStudios
================================================================
