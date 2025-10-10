package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.missions.EntityFinderMission;

public class ExpeditionFleetManager extends BaseEventManager {
    public static final String KEY = "$sep_core_artifactExpeditionManager";
    public static Logger log = Global.getLogger(ExpeditionFleetManager.class);

    public ExpeditionFleetManager() {
        super();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
    }

    public static ExpeditionFleetManager getInstance() {
        Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        return (ExpeditionFleetManager) test;
    }

    @Override
    protected float getBaseInterval() {
        // Do this every 4 months
        return 94f;
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
        ExpeditionFleetIntel event = new ExpeditionFleetIntel();
        if (event.isDone()) {
            log.info("Failed to create expedition event");
            event = null;
        }

        if (event == null) {
            // Stop forcing event if passed a certain amount of checks;
            if (this.counter <= 5) {
                // Force an expedition to ensure at least one happens every 4 months
                // Doing this to make it more consistent
                this.tracker.forceIntervalElapsed();
                this.counter++;
            } else {
                this.counter = 0;
            }
        } else {
            this.counter = 0;
        }

        return event;
    }
}
