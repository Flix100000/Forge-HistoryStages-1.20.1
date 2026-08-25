package net.bananemdnsa.historystages.demo;

/**
 * The stand-in addon's own requirement entry, deliberately <em>not</em> shaped like
 * {@code IdCountEntry}.
 *
 * <p>That is the whole point of it: two fields, neither of them a count, so the free editor tier
 * cannot express it and the addon has to bring its own tab. Before the editor framework was
 * opened, a requirement shaped like this could be stored and could gate, but a packmaker had no
 * way to set one up in game.
 *
 * @param relic  which relic, by the same ids the demo category offers
 * @param rarity how rare a copy has to be — the field that makes this more than an id
 * @param count  how many are needed. Edited inline by a number stepper the tab embeds, which is
 *               what proves a tab can take typed input and not only clicks
 */
public record RelicSetDep(String relic, String rarity, int count) {

    /** An entry read from an older file has no count; one is the sensible reading of that. */
    public RelicSetDep {
        if (count < 1) count = 1;
    }

    public RelicSetDep withCount(int newCount) {
        return new RelicSetDep(relic, rarity, newCount);
    }

    /** The rarities a maintainer may cycle through, in order. */
    public static final java.util.List<String> RARITIES =
            java.util.List.of("common", "rare", "epic");

    public RelicSetDep withNextRarity() {
        int next = (RARITIES.indexOf(rarity) + 1) % RARITIES.size();
        return new RelicSetDep(relic, RARITIES.get(next), count);
    }
}
