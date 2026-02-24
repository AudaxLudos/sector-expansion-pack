package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import sectorexpansionpack.Settings;

public class IncursionFleetManager extends BaseEventManager {
    public static final String KEY = "$sep_core_artifactIncursionManager";
    protected int counter = 0;

    public IncursionFleetManager() {
        super();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);
    }

    public static IncursionFleetManager getInstance() {
        Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        return (IncursionFleetManager) test;
    }

    @Override
    protected float getBaseInterval() {
        // Do this every 6 months
        return 183f;
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
        IncursionFleetIntel event = new IncursionFleetIntel();
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

    @Override
    public boolean isDone() {
        return !Settings.INCURSIONS_ENABLED;
    }
}
