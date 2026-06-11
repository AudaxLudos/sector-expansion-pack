package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import sectorexpansionpack.colonies.ProductionData;

public class BuildHeavyIndustryAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.HEAVYINDUSTRY;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) && ProductionData.canSupportIndustry(market, industryToBuild());
    }
}
