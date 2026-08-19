package net.bananemdnsa.historystages.data.lock.category;

/**
 * Decides whether one stored entry gates one runtime subject.
 *
 * <p>The single thing HistoryStages cannot work out for itself: it has no idea what a villager
 * trade, a spell or a quest reward is. Everything else about a lock check — which stages to
 * consult, global versus individual, what the player has unlocked — stays on this side, because
 * those are the parts an addon would have to reimplement and could get subtly wrong.
 *
 * @param <T> the entry type the category stores
 * @param <S> the runtime object the addon asks about
 */
@FunctionalInterface
public interface CategoryMatcher<T, S> {

    boolean matches(T entry, S subject);
}
