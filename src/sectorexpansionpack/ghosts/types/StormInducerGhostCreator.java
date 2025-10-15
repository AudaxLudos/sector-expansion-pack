package sectorexpansionpack.ghosts.types;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ghosts.BaseSensorGhostCreator;
import com.fs.starfarer.api.impl.campaign.ghosts.GhostFrequencies;
import com.fs.starfarer.api.impl.campaign.ghosts.SensorGhost;
import com.fs.starfarer.api.impl.campaign.ghosts.SensorGhostManager;

import java.util.ArrayList;
import java.util.List;

public class StormInducerGhostCreator extends BaseSensorGhostCreator {
    public static void register() {
        SensorGhostManager.CREATORS.add(new StormInducerGhostCreator());
    }

    @Override
    public List<SensorGhost> createGhost(SensorGhostManager manager) {
        if (!Global.getSector().getCurrentLocation().isHyperspace()) {
            return null;
        }

        List<SensorGhost> result = new ArrayList<>();
        SensorGhost g = new StormInducerGhost(manager, 200f);
        if (!g.isCreationFailed()) {
            result.add(g);
        }
        return result;
    }

    @Override
    public float getFrequency(SensorGhostManager manager) {
        return 10f *
                GhostFrequencies.getNotInCoreFactor() *
                (0.25f + 0.75f * GhostFrequencies.getFringeFactor()) *
                GhostFrequencies.getSBFactor(manager, 1f, 4f);
    }

    @Override
    public boolean canSpawnWhilePlayerInOrNearSlipstream() {
        return true;
    }
}
