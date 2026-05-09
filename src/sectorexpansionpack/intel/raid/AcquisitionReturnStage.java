package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class AcquisitionReturnStage extends AcquisitionTravelStage {
    public AcquisitionReturnStage(RaidIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
        super(raid, from, to, requireNearTarget);
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = this.intel.getCurrentStage();
        int index = this.intel.getStageIndex(this);

        float opad = 10f;

        ActionStage actionStage = this.acquisitionIntel.getActionStage();

        // Failure descriptions are handled in acquisition raid intel
        if (curr == index && actionStage.getStatus() == RaidIntel.RaidStageStatus.SUCCESS) {
            info.addPara("Returning to " + this.to.getName() + " in the " +
                    this.to.getStarSystem().getNameWithLowercaseTypeShort() +
                    " and is currently carrying a special item.", opad);
        }
    }
}
