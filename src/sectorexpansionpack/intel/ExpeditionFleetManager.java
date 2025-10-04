package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import sectorexpansionpack.missions.EntityFinderMission;

public class ExpeditionFleetManager extends BaseEventManager {
    public static final String KEY = "$sep_core_artifactExpeditionManager";

    public ExpeditionFleetManager() {
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
    }

    public static ExpeditionFleetManager getInstance() {
        Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        return (ExpeditionFleetManager) test;
    }

    @Override
    protected int getMinConcurrent() {
        return 0;
    }

    @Override
    protected int getMaxConcurrent() {
        return 4;
    }

    @Override
    protected EveryFrameScript createEvent() {
        SpecialItemSpecAPI specialItemSpec = pickSpecialItem();
        if (specialItemSpec == null) {
            return null;
        }

        EntityFinderMission efm = new EntityFinderMission();
        efm.requireMarketNotHidden();
        efm.requireMarketFactionNotPlayer();
        efm.requireMarketStabilityAtLeast(8);
        efm.requireMarketCanUseSpecialItem(new SpecialItemData(specialItemSpec.getId(), null));
        MarketAPI source = efm.pickMarket();
        if (source == null) {
            return null;
        }

        efm.requirePlanetUnexploredRuins();
        efm.preferPlanetInDirectionOfOtherMissions();
        SectorEntityToken target = efm.pickPlanet();
        if (target == null) {
            return null;
        }

        ExpeditionFleetIntel event = new ExpeditionFleetIntel(specialItemSpec, source, target);
        if (event.isDone()) {
            return null;
        }

        return event;
    }

    public SpecialItemSpecAPI pickSpecialItem() {
        WeightedRandomPicker<SpecialItemSpecAPI> specialItemPicker = new WeightedRandomPicker<>();
        specialItemPicker.addAll(Global.getSettings().getAllSpecialItemSpecs());

        return specialItemPicker.pick();
    }
}
