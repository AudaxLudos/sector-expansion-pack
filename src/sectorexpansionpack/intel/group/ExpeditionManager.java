package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;

public class ExpeditionManager extends BaseEventManager {
    public static void register() {
        if (!Global.getSector().hasScript(ExpeditionManager.class)) {
            Global.getSector().addTransientScript(new ExpeditionManager());
        }
    }

    @Override
    protected int getMinConcurrent() {
        return 1;
    }

    @Override
    protected int getMaxConcurrent() {
        return 1;
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
