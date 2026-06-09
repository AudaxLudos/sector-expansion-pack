package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

public class ColonyBuildAction extends ColonyAction {
    @Override
    public String name() {
        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(industryToBuild());
        String industryName = " ";
        if (spec != null) {
            industryName = spec.getName();
        }
        return "build " + industryName + " action";
    }

    protected String industryToBuild() {
        return null;
    }

    @Override
    public float priority() {
        return 1;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return market.hasIndustry(industryToBuild());
    }

    @Override
    public void perform(MarketAPI market) {
        if (canPerform(market)) {
            IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(industryToBuild());
            if (spec != null) {
                market.getConstructionQueue().addToEnd(spec.getId(), (int) spec.getCost());
            }
        }
    }
}
