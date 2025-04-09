package sectorexpansionpack.intel.group;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;

public class IncursionManager extends BaseEventManager {
    public static void register() {
        if (!Global.getSector().hasScript(IncursionManager.class)) {
            Global.getSector().addTransientScript(new IncursionManager());
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
        IncursionFGI intel = new IncursionFGI();
        if (intel.isDone()) {
            intel = null;
        }
        return intel;
    }
}
