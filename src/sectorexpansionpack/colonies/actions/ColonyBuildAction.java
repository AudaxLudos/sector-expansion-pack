package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

public class ColonyBuildAction extends ColonyAction {
    @Override
    public String id() {
        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(industryToBuild());
        String industryId = " ";
        if (spec != null) {
            industryId = spec.getId();
        }
        return "build_" + industryId + "_action";
    }

    @Override
    public String name() {
        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(industryToBuild());
        String industryName = " ";
        if (spec != null) {
            industryName = spec.getName();
        }
        return "Build " + industryName.toLowerCase().replaceAll("-.*", "") + " action";
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
        return !isBuiltOrQueued(market) && (!isIndustry() || (isIndustry() && hasAvailableIndustrySlots(market)));
    }

    protected boolean hasAvailableIndustrySlots(MarketAPI market) {
        return Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
    }

    protected boolean isIndustry() {
        IndustrySpecAPI spec = getSpec();
        if (spec == null) {
            return false;
        }
        return spec.hasTag(Industries.TAG_INDUSTRY);
    }

    protected boolean isBuiltOrQueued(MarketAPI market) {
        return market.hasIndustry(industryToBuild()) && isStructureQueued(market);
    }

    protected boolean isStructureQueued(MarketAPI market) {
        return market.getConstructionQueue().hasItem(industryToBuild());
    }

    @Override
    public void perform(MarketAPI market) {
        if (canPerform(market)) {
            IndustrySpecAPI spec = getSpec();
            if (spec != null) {
                market.getConstructionQueue().addToEnd(spec.getId(), (int) spec.getCost());
            }
        }
    }

    public IndustrySpecAPI getSpec() {
        return Global.getSettings().getIndustrySpec(industryToBuild());
    }
}
