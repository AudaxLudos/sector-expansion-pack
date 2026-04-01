package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;

public class ClearDebrisFieldsIntelCreator implements GenericMissionManager.GenericMissionCreator {
    @Override
    public float getMissionFrequencyWeight() {
        return 10f;
    }

    @Override
    public EveryFrameScript createMissionIntel() {
        return new ClearDebrisFieldsMissionIntel();
    }
}
