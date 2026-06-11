package sectorexpansionpack.colonies.actions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class ColonyUpgradeAction extends ColonyBuildAction {
    protected String industryId;

    public ColonyUpgradeAction(String industryId) {
        this.industryId = industryId;
    }

    @Override
    public String getId() {
        return super.getId().replace("build", "upgrade");
    }

    @Override
    public String getName() {
        return super.getName().replace("Build", "Upgrade");
    }

    @Override
    public float getPriority() {
        return 5f;
    }

    @Override
    protected String industryToBuild() {
        return this.industryId;
    }

    @Override
    public boolean canPerform(MarketAPI market) {
        return market.hasIndustry(industryToBuild()) && !getSpec().getUpgrade().isBlank();
    }

    @Override
    public void perform(MarketAPI market) {
        if (canPerform(market)) {
            Industry industry = market.getIndustry(industryToBuild());
            if (industry != null) {
                industry.startUpgrading();
            }
            log.info(getName() + " performed " + market.getOnOrAt() + " " + market.getName() + " in the " + market.getStarSystem());
        }
    }
}
