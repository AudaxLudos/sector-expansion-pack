package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.List;

public class AcquisitionReturnStage extends TravelStage {
    protected AcquisitionRaidIntel acquisitionIntel;

    public AcquisitionReturnStage(RaidIntel raid, SectorEntityToken from, SectorEntityToken to, boolean requireNearTarget) {
        super(raid, from, to, requireNearTarget);
        this.acquisitionIntel = (AcquisitionRaidIntel) raid;
    }

    @Override
    public void notifyStarted() {
        super.notifyStarted();
        this.acquisitionIntel.sendUpdateIfPlayerHasIntel(RaidIntel.UPDATE_RETURNING, false);
    }

    @Override
    protected void updateRoutes() {
        resetRoutes();

        List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(this.intel.getRouteSourceId());
        for (RouteManager.RouteData route : routes) {
            float travelDays = RouteLocationCalculator.getTravelDays(this.from, this.to);
            if (DebugFlags.RAID_DEBUG || DebugFlags.FAST_RAIDS) {
                travelDays *= 0.1f;
            }

            route.addSegment(new RouteManager.RouteSegment(travelDays, this.from, this.to));
            route.addSegment(new RouteManager.RouteSegment(1000f, this.to, AssembleStage.WAIT_STAGE));

            this.maxDays = Math.max(this.maxDays, travelDays);
        }
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = this.intel.getCurrentStage();
        int index = this.intel.getStageIndex(this);

        String forces = this.acquisitionIntel.getForcesNoun();
        float opad = 10f;

        ActionStage actionStage = this.acquisitionIntel.getActionStage();

        if (curr == index && actionStage.getStatus() == RaidIntel.RaidStageStatus.SUCCESS) {
            info.addPara("The " + forces + " are returning to " + this.to.getName() +
                    " in the " + this.to.getStarSystem().getNameWithLowercaseTypeShort() +
                    " and is currently carrying a special item.", opad);
        }
    }
}
