package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.JsonAdapter;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntryListAdapter;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntryListAdapter;

/**
 * The {@code trades} block on a stage: three lists that together answer one question — which
 * merchant offers this player may see.
 *
 * <p>The {@code offers} list names single trades. Gating an item for trading wherever it turns up
 * is the item action {@code trade} on an ordinary item entry, and lives in the items tab — three
 * ways to say the same thing was one too many, and the broad rule was already there.
 *
 * <p>Shaped after {@link EntityLocks}, and for the same reason. Three things that are asked
 * separately and have nothing in common except the tab they share are three lists, not one list
 * of a mixed type: a mixed type would force every reader to ask what kind of entry it is holding,
 * which is exactly the question the lock categories exist to remove.
 *
 * <p>Professions carry their own level narrowing, so "librarians from apprentice up" can be said
 * without taking every other profession's apprentices with it. The block's own {@code levels} list
 * stays what it was: the levels gated for <em>every</em> merchant, which is the rule a pack wants
 * for "until the Bronze Age there are only novices" and would be tedious to repeat per profession.
 *
 * <p>Levels are held as strings although they are numbers. The lock categories are defined over
 * lists of ids, and the editor tab edits lists of strings; converting at both ends to store an
 * {@code int} would buy nothing. {@link TradeLevelListAdapter} keeps the JSON numeric, which is
 * what someone editing a stage file by hand expects to see.
 */
public class TradeLocks {

    @JsonAdapter(TradeOfferEntryListAdapter.class)
    private List<TradeOfferEntry> offers;

    @JsonAdapter(TradeProfessionEntryListAdapter.class)
    private List<TradeProfessionEntry> professions;

    @JsonAdapter(TradeLevelListAdapter.class)
    private List<String> levels;

    public TradeLocks() {
        this.offers = new ArrayList<>();
        this.professions = new ArrayList<>();
        this.levels = new ArrayList<>();
    }

    public List<TradeOfferEntry> getOffers() {
        return offers != null ? offers : new ArrayList<>();
    }

    public List<TradeProfessionEntry> getProfessions() {
        return professions != null ? professions : new ArrayList<>();
    }

    public List<String> getLevels() {
        return levels != null ? levels : new ArrayList<>();
    }

    public void setOffers(List<TradeOfferEntry> offers) {
        if (offers == null) {
            this.offers = new ArrayList<>();
            return;
        }
        List<TradeOfferEntry> copy = new ArrayList<>(offers.size());
        for (TradeOfferEntry e : offers) copy.add(e.copy());
        this.offers = copy;
    }

    public void setProfessions(List<TradeProfessionEntry> professions) {
        if (professions == null) {
            this.professions = new ArrayList<>();
            return;
        }
        List<TradeProfessionEntry> copy = new ArrayList<>(professions.size());
        for (TradeProfessionEntry e : professions) copy.add(e.copy());
        this.professions = copy;
    }

    public void setLevels(List<String> levels) {
        this.levels = levels != null ? new ArrayList<>(levels) : new ArrayList<>();
    }

    /** What the gated offers hand over — for the overview counters and the debug log. */
    public List<String> getOfferedItemIds() {
        List<String> ids = new ArrayList<>(getOffers().size());
        for (TradeOfferEntry e : getOffers()) ids.add(e.givesId());
        return ids;
    }

    /** Just the profession ids — same three readers, and the dual-phase check. */
    public List<String> getProfessionIds() {
        List<String> ids = new ArrayList<>(getProfessions().size());
        for (TradeProfessionEntry e : getProfessions()) ids.add(e.getId());
        return ids;
    }
}
