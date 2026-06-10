package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

public class ColonyUpgradeAction extends ColonyBuildAction {
    protected String industryId;

    public ColonyUpgradeAction(String industryId) {
        this.industryId = industryId;
    }

    @Override
    public String id() {
        return super.id().replace("build", "upgrade");
    }

    @Override
    public String name() {
        return super.name().replace("Build", "Upgrade");
    }

    @Override
    protected String industryToBuild() {
        return this.industryId;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return super.canPerform(market) && !getSpec().getUpgrade().isBlank();
    }

    @Override
    public void perform(MarketAPI market) {
        if (canPerform(market)) {
            Industry industry = market.getIndustry(industryToBuild());
            if (industry != null) {
                industry.startUpgrading();
            }
        }
    }
}
