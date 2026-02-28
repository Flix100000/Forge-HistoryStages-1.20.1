**History Stages**

History Stages is a progression and gatekeeping mod for 
Minecraft 1.20.1 (Forge). It allows modpack creators to 
implement a global "Era" system by locking items, recipes, 
dimensions, and mob equipment behind custom research stages.

----------------------------------------------------------------
1. KEY FEATURES
----------------------------------------------------------------

- Global Progression: When a stage is unlocked, it is 
  available for everyone on the server.
- Deep Gating: Lock content by Item ID, Tags, or entire Mod IDs.
- Dimension Access: Prevent players from entering specific 
  dimensions (Nether, End, etc.) without the required stage.
- Smart Loot: Locked items are removed from chests or replaced 
  with configurable items like Cobblestone.
- Anti-Cheese: Mobs won't spawn with gear that hasn't been 
  researched yet.
- Research System: Uses a Research Pedestal and Research Books.
- JEI Support: Automatically hides or marks locked items.

----------------------------------------------------------------
2. HOW TO USE
----------------------------------------------------------------

STAGES:
Stages are defined via JSON files in: /config/historystages/
Example format:
{
  "display_name": "Bronze Age",
  "items": ["minecraft:iron_ingot"],
  "tags": ["forge:ores/iron"],
  "mods": ["mekanism"],
  "dimensions": ["minecraft:the_nether"]
}

RESEARCH BOOKS & Pedestal:
1. Place a Research Pedestal.
2. Put a Research Book into the input slot.
3. Wait for the "Searching..." process to finish.
4. The stage is now unlocked globally.

IMPORTANT: This mod does NOT include default crafting recipes 
for the pedestal or books. You MUST add them yourself using 
KubeJS, CraftTweaker, or a Datapack.

----------------------------------------------------------------
3. ADMIN COMMANDS (Permission Level 2)
----------------------------------------------------------------

/history unlock <stage>  - Unlocks a stage. Use '*' for all.
/history lock <stage>    - Relocks a stage. Use '*' for all.
/history list            - Shows all registered stages.
/history info <stage>    - Shows status of a specific stage.
/history reload          - Reloads JSONs and syncs players.

----------------------------------------------------------------
4. OBTAINING BOOKS VIA COMMAND
----------------------------------------------------------------

/give @s historystages:research_book{StageResearch:"stage_id"}

(Replace "stage_id" with your JSON filename, e.g. "stone_age")

----------------------------------------------------------------
5. NOTE: IN DEVELOPMENT
----------------------------------------------------------------

This mod is currently in active development. Bugs may occur. 
Please report any issues to help improve the mod.

Loot replacements and other settings can be found in:
/config/historystages-common.toml

----------------------------------------------------------------
License: MIT
================================================================
