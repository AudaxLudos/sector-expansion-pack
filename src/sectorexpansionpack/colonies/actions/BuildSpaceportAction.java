package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildSpaceportAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.SPACEPORT;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) && geIndustryWithTag(market, Industries.TAG_SPACEPORT) == null;
    }
}
