package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

public class BuildMiningAction extends ColonyBuildAction {
    @Override
    protected String industryToBuild() {
        return Industries.MINING;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) &&
                (market.hasCondition(Conditions.ORGANICS_TRACE) || market.hasCondition(Conditions.ORGANICS_COMMON) ||
                        market.hasCondition(Conditions.ORGANICS_ABUNDANT) || market.hasCondition(Conditions.ORGANICS_PLENTIFUL) ||
                        market.hasCondition(Conditions.ORE_SPARSE) || market.hasCondition(Conditions.ORE_MODERATE) ||
                        market.hasCondition(Conditions.ORE_ABUNDANT) || market.hasCondition(Conditions.ORE_RICH) ||
                        market.hasCondition(Conditions.ORE_ULTRARICH) ||
                        market.hasCondition(Conditions.RARE_ORE_SPARSE) || market.hasCondition(Conditions.RARE_ORE_MODERATE) ||
                        market.hasCondition(Conditions.RARE_ORE_ABUNDANT) || market.hasCondition(Conditions.RARE_ORE_RICH) ||
                        market.hasCondition(Conditions.RARE_ORE_ULTRARICH));
    }
}
