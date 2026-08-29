package net.bananemdnsa.historystages.client.tooltip;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.research.TierMode;

import java.util.List;

/**
 * Everything the tooltip needs to know about one scroll. Real tooltips derive it from the
 * stack (see {@code ScrollTooltipRenderer}'s private {@code fromStack}); the editor's preview
 * supplies a made-up one, which is the only way to show every configurable line at once.
 *
 * <p>Field semantics, matching the pre-split behaviour exactly:
 * <ul>
 *   <li>{@code stageName} — empty string when there is no stage.</li>
 *   <li>{@code individual} — for the badge and owner rows, resolved via
 *       {@code StageManager.isIndividualStage}.</li>
 *   <li>{@code ownerName} — null or empty means the owner row does not render.</li>
 *   <li>{@code minTier} / {@code tierMode} — the tier row renders only when
 *       {@code minTier > 1 || tierMode == TierMode.EXACT} (and the visual config toggle is on).
 *       A null {@code tierMode} means "no stage" and the row does not render.</li>
 *   <li>{@code groups} — the dependency groups; empty means the dependency block does not
 *       render.</li>
 *   <li>{@code result} — the cached dependency result; may be null, meaning "no cache entry
 *       yet".</li>
 * </ul>
 */
public record ScrollTooltipContext(String stageName, boolean individual, String ownerName,
                                    int minTier, TierMode tierMode,
                                    List<DependencyGroup> groups, RequirementResult result) {
}
