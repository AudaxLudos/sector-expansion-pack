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
        String forces = this.acquisitionIntel.getForcesNoun();

        if (curr == index) {
            info.addPara("The " + forces + " are currently travelling to the " +
                    this.intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }
}
