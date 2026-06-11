package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildGroundDefensesAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.GROUNDDEFENSES;
    }

    @Override
    public float getPriority() {
        return 1f;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) && geIndustryWithTag(market, Industries.TAG_GROUNDDEFENSES) == null;
    }
}
