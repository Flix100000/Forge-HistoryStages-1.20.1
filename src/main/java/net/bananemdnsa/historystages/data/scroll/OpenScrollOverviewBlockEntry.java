package net.bananemdnsa.historystages.data.scroll;

/** One line of the {@code openScrollOverviewBlocks} config: a block and whether it is drawn. */
public record OpenScrollOverviewBlockEntry(OpenScrollOverviewBlock block, boolean enabled) {}
