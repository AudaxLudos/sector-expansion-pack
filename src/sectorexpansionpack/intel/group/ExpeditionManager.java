package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;

public class ExpeditionManager extends BaseEventManager {
    public static float EVENT_INTERVAL = 60f; // In days

    public static void register() {
        if (!Global.getSector().hasScript(ExpeditionManager.class)) {
            Global.getSector().addTransientScript(new ExpeditionManager());
        }
    }

    @Override
    protected int getMinConcurrent() {
        return 0;
    }

    @Override
    protected int getMaxConcurrent() {
        return 3;
    }

    @Override
    protected float getBaseInterval() {
        return EVENT_INTERVAL;
    }

    @Override
    protected EveryFrameScript createEvent() {
        ExpeditionFGI intel = new ExpeditionFGI();
        if (intel.isDone()) {
            intel = null;
        }
        return intel;
    }
}
