package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.EditorTab;
import net.bananemdnsa.historystages.data.DependencyGroup;

/**
 * An {@link EditorTab} over one dependency group: one kind of requirement, shown in the dependency
 * editor.
 *
 * <p>The container is a group rather than a stage, and that is the whole difference from a lock
 * category's tab — but it is a difference with teeth. A stage is opened once and saved once; a
 * stage has up to five groups and the maintainer switches between them while the tab strip stays
 * put. The host must therefore {@code store} into the group it is leaving before it {@code load}s
 * from the one it is entering, or the edits made in the first group are lost without a word.
 */
public interface DependencyTab extends EditorTab<DependencyGroup> {

    String requirementId();
}
