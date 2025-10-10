package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;

public class ExpeditionFleetManager extends BaseEventManager {
    public static final String KEY = "$sep_core_artifactExpeditionManager";
    protected int counter = 0;

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
            event = null;
        }

        if (event == null) {
            // Stop forcing event if passed a certain amount of checks;
            if (this.counter < 5) {
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
