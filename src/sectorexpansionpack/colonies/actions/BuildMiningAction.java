package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import sectorexpansionpack.colonies.ProductionData;

public class BuildMiningAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.MINING;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) && ProductionData.canSupportIndustry(market, industryToBuild());
    }
}
