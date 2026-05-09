package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class AcquisitionTravelStage extends TravelStage {
    protected AcquisitionRaidIntel acquisitionIntel;

    public AcquisitionTravelStage(RaidIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
        super(raid, from, to, requireNearTarget);
        this.acquisitionIntel = (AcquisitionRaidIntel) raid;
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = this.intel.getCurrentStage();
        int index = this.intel.getStageIndex(this);

        float opad = 10f;

        // Failure descriptions are handled in acquisition raid intel
        if (curr == index) {
            info.addPara("Travelling to the " +
                    this.intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }

    public SectorEntityToken getFrom() {
        return this.from;
    }

    public SectorEntityToken getTo() {
        return this.to;
    }
}
