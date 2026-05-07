package sectorexpansionpack.intel.acquisition;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
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
            this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.ABORTED_IN_PLANNING);
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
    protected String getRaidString() {
        return this.acquisitionIntel.getRaidNoun();
    }

    @Override
    protected String getForcesString() {
        return "The " + this.acquisitionIntel.getForcesNoun();
    }
}
