package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildOrbitalStationAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        // TODO: Figure out a way for specific factions to use their preferred station type
        return Industries.ORBITALSTATION_MID;
    }

    @Override
    public float getPriority() {
        return 2f;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) && geIndustryWithTag(market, Industries.TAG_STATION) == null;
    }
}
