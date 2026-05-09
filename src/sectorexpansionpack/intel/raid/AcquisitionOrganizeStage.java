package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class AcquisitionOrganizeStage extends OrganizeStage {
    protected AcquisitionRaidIntel acquisitionIntel;

    public AcquisitionOrganizeStage(RaidIntel raid, MarketAPI market, float durDays) {
        super(raid, market, durDays);
        this.acquisitionIntel = (AcquisitionRaidIntel) raid;
    }

    @Override
    public void advance(float amount) {
        if (this.status == RaidIntel.RaidStageStatus.ONGOING &&
                (!this.market.isInEconomy() || (!this.market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY) && this.wasMilitary))) {
            abort();
            return;
        }
        float days = Misc.getDays(amount);

        this.elapsed += days;

        this.statusInterval.advance(days);
        if (this.statusInterval.intervalElapsed()) {
            updateStatus();
        }
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = this.intel.getCurrentStage();
        int index = this.intel.getStageIndex(this);

        float opad = 10f;

        // Failure descriptions are handled in acquisition raid intel
        if (curr == index) {
            BaseHubMission.addStandardMarketDesc("Making preparations in orbit around", getMarket(), info, opad);
        }
    }

    @Override
    protected String getRaidString() {
        return this.acquisitionIntel.getRaidNoun();
    }

    @Override
    protected String getForcesString() {
        return "The " + this.acquisitionIntel.getForcesNoun();
    }
}
