package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildPatrolHQAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.PATROLHQ;
    }

    @Override
    public float getPriority() {
        return 2f;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) &&
                geIndustryWithTag(market, Industries.TAG_MILITARY) == null && geIndustryWithTag(market, Industries.TAG_COMMAND) == null;
    }
}
