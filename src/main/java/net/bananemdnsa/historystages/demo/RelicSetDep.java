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
 */
public record RelicSetDep(String relic, String rarity) {

    /** The rarities a maintainer may cycle through, in order. */
    public static final java.util.List<String> RARITIES =
            java.util.List.of("common", "rare", "epic");

    public RelicSetDep withNextRarity() {
        int next = (RARITIES.indexOf(rarity) + 1) % RARITIES.size();
        return new RelicSetDep(relic, RARITIES.get(next));
    }
}
