package sectorexpansionpack.colonies;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.colonies.actions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FactionColonyAI extends BaseIntelPlugin {
    public static List<ColonyAction> COLONY_ACTIONS = new ArrayList<>();
    public static Logger log = Global.getLogger(FactionColonyAI.class);
    protected FactionAPI faction;
    protected IntervalUtil actionTimer;
    protected MarketAPI prevMarket;
    protected ColonyAction prevAction;

    static {
        COLONY_ACTIONS.add(new BuildFarmingAction());
        COLONY_ACTIONS.add(new BuildMiningAction());
        COLONY_ACTIONS.add(new BuildWaystationAction());
        COLONY_ACTIONS.add(new BuildGroundDefensesAction());
        COLONY_ACTIONS.add(new BuildPatrolHQAction());
        COLONY_ACTIONS.add(new BuildOrbitalStationAction());
        for (IndustrySpecAPI indSpec : Global.getSettings().getAllIndustrySpecs()) {
            if (!indSpec.getUpgrade().isBlank()) {
                COLONY_ACTIONS.add(new ColonyUpgradeAction(indSpec.getId()));
            }
        }
    }

    public FactionColonyAI(FactionAPI faction) {
        this.faction = faction;
        this.actionTimer = new IntervalUtil(30f, 60f);

        Global.getSector().getIntelManager().addIntel(this, true);
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
        info.addPara( daysLeft+ " " + daysText + " until next colony action meeting", 10f);
        if (this.prevMarket != null && this.prevAction != null) {
            info.addPara("A " + this.prevAction.name() + " was performed " + this.prevMarket.getOnOrAt() +
                    " " + this.prevMarket.getName() + " in the " + this.prevMarket.getStarSystem(), 10f);
        }
    }
}
