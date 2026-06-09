package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildOrbitalStationAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        // TODO: Figure out a way for specific factions to use their preferred station type
        return Industries.ORBITALSTATION_MID;
    }
}
