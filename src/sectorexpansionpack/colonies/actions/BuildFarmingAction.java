package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildFarmingAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.FARMING;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) &&
                (market.hasCondition(Conditions.FARMLAND_POOR) || market.hasCondition(Conditions.FARMLAND_ADEQUATE) ||
                        market.hasCondition(Conditions.FARMLAND_RICH) || market.hasCondition(Conditions.FARMLAND_BOUNTIFUL));
    }
}
