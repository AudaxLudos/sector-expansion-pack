package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class ClearDebrisFieldsIntelCreator implements GenericMissionManager.GenericMissionCreator {
    protected transient WeightedRandomPicker<MarketAPI> marketPicker = null;

    @Override
    public float getMissionFrequencyWeight() {
        return 10f;
    }

    @Override
    public EveryFrameScript createMissionIntel() {
        MarketAPI market = pickMarket();
        if (market == null) {
            return null;
        }
        
        return new ClearDebrisFieldsMissionIntel(market);
    }

    protected void initPicker() {
        this.marketPicker = new WeightedRandomPicker<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.isHidden() || market.getFaction().isPlayerFaction()) {
                continue;
            }

            this.marketPicker.add(market);
        }
    }

    protected MarketAPI pickMarket() {
        if (this.marketPicker == null) {
            initPicker();
        }

        MarketAPI market = this.marketPicker.pick();
        for (EveryFrameScript s : GenericMissionManager.getInstance().getActive()) {
            if (s instanceof ClearDebrisFieldsMissionIntel intel) {
                if (market == intel.getMarket()) {
                    return null;
                }
            }
        }

        return market;
    }
}
