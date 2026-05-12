package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import sectorexpansionpack.Settings;
import sectorexpansionpack.missions.EntityFinderMission;

import java.util.Objects;

public class AcquisitionRaidManager extends BaseEventManager {
    public static final String KEY = "$sep_core_acquisitionRaidManager";
    protected EntityFinderMission efm;
    protected int counter = 0;

    public AcquisitionRaidManager() {
        super();
        this.efm = new EntityFinderMission();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
    }

    public static AcquisitionRaidManager getInstance() {
        Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        return (AcquisitionRaidManager) test;
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
        return Settings.ACQUISITIONS_TIMER;
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
        MarketAPI target = pickTargetWithCompatibleSpecialItems(source);
        SpecialItemSpecAPI specialItem = pickSpecialItem(null, source, target);
        AcquisitionRaidIntel event = new AcquisitionRaidIntel(source, target, specialItem);
        if (event.isDone()) {
            event = null;
        }

        if (event == null && this.counter < 5) {
            // Try to force an incursion 5 times before stopping
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
        this.efm.requireMarketFactionNoMemoryFlag(AcquisitionRaidIntel.SOURCE_KEY);
        this.efm.preferMarketMilitary();
        return this.efm.pickMarket();
    }

    protected MarketAPI pickTargetWithCompatibleSpecialItems(MarketAPI other) {
        this.efm.resetSearch();
        this.efm.requireMarketNotHidden();
        this.efm.requireMarketFactionNot(other.getFactionId());
        this.efm.requireMarketFactionNotPlayer();
        this.efm.requireMarketNoMemoryFlag(AcquisitionRaidIntel.TARGET_KEY);
        this.efm.requireMarketHasCompatibleSpecialItemsWithOther(other);
        return this.efm.pickMarket();
    }

    public SpecialItemSpecAPI pickSpecialItem(String specialItemId, MarketAPI source, MarketAPI target) {
        WeightedRandomPicker<SpecialItemData> picker = new WeightedRandomPicker<>();
        for (Industry targetInd : target.getIndustries()) {
            SpecialItemData otherData = targetInd.getSpecialItem();
            if (otherData != null) {
                for (Industry ind : source.getIndustries()) {
                    if (!Settings.COLONY_ITEM_WHITELIST.contains(otherData.getId())) {
                        continue;
                    }
                    if (Objects.equals(otherData.getId(), specialItemId)) {
                        return Global.getSettings().getSpecialItemSpec(otherData.getId());
                    }
                    if (ind.wantsToUseSpecialItem(otherData)) {
                        picker.add(otherData);
                    }
                }
            }
        }

        SpecialItemData data = picker.pick();

        return Global.getSettings().getSpecialItemSpec(data.getId());
    }

    @Override
    public boolean isDone() {
        return !Settings.ACQUISITIONS_ENABLED;
    }
}
