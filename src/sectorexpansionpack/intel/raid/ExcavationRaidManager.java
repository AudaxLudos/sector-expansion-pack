package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import sectorexpansionpack.Settings;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.EntityFinderMission;

public class ExcavationRaidManager extends BaseEventManager {
    public static final String KEY = "$sep_core_excavationRaidManager";
    protected EntityFinderMission efm;
    protected int counter = 0;

    public ExcavationRaidManager() {
        super();
        this.efm = new EntityFinderMission();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
    }

    public static ExcavationRaidManager getInstance() {
        Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        return (ExcavationRaidManager) test;
    }

    protected Object readResolve() {
        if (this.efm == null) {
            this.efm = new EntityFinderMission();
        }
        return this;
    }

    @Override
    protected float getBaseInterval() {
        // Do this every 6 months
        return Settings.EXCAVATIONS_TIMER;
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
        MarketAPI source = pickSource();
        SectorEntityToken target = pickTarget();

        SpecialItemSpecAPI specialItem = null;
        if (source != null) {
            specialItem = pickSpecialItem(source);
        }

        ExcavationRaidIntel event = null;
        if (source != null && target != null && specialItem != null) {
            event = new ExcavationRaidIntel(source, target, specialItem);
        }

        if (event != null && event.isDone()) {
            event = null;
        }

        if (event == null && this.counter < 5) {
            // Try to force an event 5 times before stopping
            // Doing this to make it more consistent as the first few calls could be null
            this.counter++;
            return createEvent();
        } else {
            this.counter = 0;
        }

        return event;
    }

    protected MarketAPI pickSource() {
        this.efm.resetSearch();
        this.efm.requireMarketNotHidden();
        this.efm.requireMarketFactionNotPlayer();
        this.efm.requireMarketFactionNoMemoryFlag(ExcavationRaidIntel.SOURCE_KEY);
        this.efm.preferMarketMilitary();
        return this.efm.pickMarket();
    }

    protected SectorEntityToken pickTarget() {
        this.efm.resetSearch();
        if (this.efm.rollProbability(ExcavationRaidIntel.WRECK_CHANCE)) {
            this.efm.requireEntityNoMemoryFlag(ExcavationRaidIntel.TARGET_KEY);
            this.efm.requireEntityNoSpecialSalvage();
            this.efm.requireEntityType(Entities.WRECK);
            this.efm.preferEntityInDirectionOfOtherMissions();
            this.efm.preferEntityUndiscovered();
            return this.efm.pickEntity();
        }

        this.efm.requirePlanetNoMemoryFlag(ExcavationRaidIntel.TARGET_KEY);
        this.efm.requirePlanetWithRuins();
        this.efm.requirePlanetUnexploredRuins();
        this.efm.preferPlanetInDirectionOfOtherMissions();
        return this.efm.pickPlanet();
    }

    public SpecialItemSpecAPI pickSpecialItem(MarketAPI source) {
        WeightedRandomPicker<SpecialItemSpecAPI> specialItemPicker = new WeightedRandomPicker<>();
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            if (!Settings.COLONY_ITEM_WHITELIST.contains(spec.getId())) {
                continue;
            }
            if (spec.getParams() == null || spec.getParams().isEmpty()) {
                continue;
            }
            float weight = 1f;
            for (Industry industry : source.getIndustries()) {
                if (!industry.wantsToUseSpecialItem(new SpecialItemData(spec.getId(), spec.getParams()))) {
                    continue;
                }
                if (Utils.canSpecialItemBeInstalled(spec.getId(), industry)) {
                    weight = 3f;
                }
            }
            specialItemPicker.add(spec, weight);
        }

        return specialItemPicker.pick();
    }

    @Override
    public boolean isDone() {
        return !Settings.EXCAVATIONS_ENABLED;
    }
}
