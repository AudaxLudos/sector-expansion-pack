package sectorexpansionpack.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class ConstructObjectiveIntelCreator implements GenericMissionManager.GenericMissionCreator {
    protected transient WeightedRandomPicker<StarSystemAPI> systemPicker = null;

    @Override
    public float getMissionFrequencyWeight() {
        return 10f;
    }

    @Override
    public EveryFrameScript createMissionIntel() {
        StarSystemAPI system = pickSystem();
        if (system == null) {
            return null;
        }

        return new ConstructObjectiveMissionIntel(system);
    }


    protected void initPicker() {
        this.systemPicker = new WeightedRandomPicker<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.THEME_HIDDEN) || system.hasTag(Tags.THEME_CORE)
                    || system.getEntitiesWithTag("stable_location").isEmpty()) {
                continue;
            }
            float weight = 0f;
            if (system.hasTag(Tags.THEME_REMNANT)) {
                weight = 10f;
            } else if (system.hasTag(Tags.THEME_DERELICT)) {
                weight = 10f;
            } else if (system.hasTag(Tags.THEME_RUINS)) {
                weight = 10f;
            } else if (system.hasTag(Tags.THEME_MISC)) {
                weight = 5f;
            }

            if (weight <= 0) {
                continue;
            }

            this.systemPicker.add(system, weight);
        }
    }

    protected StarSystemAPI pickSystem() {
        if (this.systemPicker == null) {
            initPicker();
        }

        StarSystemAPI system = this.systemPicker.pick();
        for (EveryFrameScript s : GenericMissionManager.getInstance().getActive()) {
            if (s instanceof ConstructObjectiveMissionIntel intel) {
                if (system == intel.getSystem()) {
                    return null;
                }
            }
        }

        return system;
    }
}
