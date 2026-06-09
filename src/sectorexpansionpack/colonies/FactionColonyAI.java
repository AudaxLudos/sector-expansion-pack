package sectorexpansionpack.colonies;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.colonies.actions.ColonyAction;

import java.util.*;

public class FactionColonyAI extends BaseIntelPlugin {
    public static final String PERSISTENT_DATA_KEY = "sep_factionColonyAI";
    public static List<ColonyAction> COLONY_ACTIONS = new ArrayList<>();
    public static Logger log = Global.getLogger(FactionColonyAI.class);
    protected FactionAPI faction;
    protected IntervalUtil actionTimer;
    protected MarketAPI prevMarket;
    protected ColonyAction prevAction;

    public FactionColonyAI(FactionAPI faction) {
        this.faction = faction;
        this.actionTimer = new IntervalUtil(30f, 60f);

        Global.getSector().getIntelManager().addIntel(this, true);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, FactionColonyAI> getFactionColonyAIs() {
        if (!Global.getSector().getPersistentData().containsKey(PERSISTENT_DATA_KEY)) {
            Global.getSector().getPersistentData().put(PERSISTENT_DATA_KEY, new HashMap<String, FactionColonyAI>());
        }

        return (Map<String, FactionColonyAI>) Global.getSector().getPersistentData().get(PERSISTENT_DATA_KEY);
    }

    public static FactionColonyAI getFactionColonyAI(FactionAPI faction) {
        return getFactionColonyAIs().get(faction.getId());
    }

    public static Set<FactionAPI> getFactionsWithMarkets() {
        Set<FactionAPI> factionsWithMarkets = new HashSet<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.isPlayerOwned()) {
                continue;
            }
            if (market.getFaction() == null) {
                continue;
            }
            factionsWithMarkets.add(market.getFaction());
        }
        return factionsWithMarkets;
    }

    @Override
    protected void advanceImpl(float amount) {
        this.actionTimer.advance(amount);
        if (!this.actionTimer.intervalElapsed()) {
            return;
        }

        List<MarketAPI> markets = Misc.getFactionMarkets(this.faction);
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
        for (MarketAPI market : markets) {
            if (!market.isInEconomy()) {
                continue;
            }
            if (market.isHidden()) {
                continue;
            }
            boolean hasAvailableAction = false;
            for (ColonyAction action : COLONY_ACTIONS) {
                if (action.canPerform(market)) {
                    hasAvailableAction = true;
                    break;
                }
            }
            if (!hasAvailableAction) {
                continue;
            }
            marketPicker.add(market);
        }
        MarketAPI market = marketPicker.pick();
        if (market == null) {
            this.prevMarket = null;
            this.prevAction = null;
            return;
        }

        WeightedRandomPicker<ColonyAction> actionPicker = new WeightedRandomPicker<>();
        for (ColonyAction action : COLONY_ACTIONS) {
            if (action.canPerform(market)) {
                actionPicker.add(action, action.priority());
            }
        }
        ColonyAction action = actionPicker.pick();
        if (action == null) {
            this.prevMarket = null;
            this.prevAction = null;
            return;
        }

        action.perform(market);
        this.prevMarket = market;
        this.prevAction = action;
        log.info("A " + this.prevAction.name() + " was performed " + this.prevMarket.getOnOrAt() +
                " " + this.prevMarket.getName() + " in the " + this.prevMarket.getStarSystem());
    }

    @Override
    public String getIcon() {
        return this.faction.getCrest();
    }

    @Override
    protected String getName() {
        return Misc.ucFirst(this.faction.getPersonNamePrefix()) + " Colony AI";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(this.faction.getId());
        return tags;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        int daysLeft = (int) (this.actionTimer.getIntervalDuration() - this.actionTimer.getElapsed());
        String daysText = daysLeft <= 1 ? "day" : "days";
        info.addPara(daysLeft + " " + daysText + " until next colony action meeting", 10f);
        if (this.prevMarket != null && this.prevAction != null) {
            info.addPara("A " + this.prevAction.name() + " was performed " + this.prevMarket.getOnOrAt() +
                    " " + this.prevMarket.getName() + " in the " + this.prevMarket.getStarSystem(), 10f);
        }
    }
}
