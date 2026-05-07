package sectorexpansionpack.intel.acquisition;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.ArrayList;
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
    protected void abortIfNeededBasedOnFP(boolean giveReturnOrders) {
        List<RouteManager.RouteData> routes = getRoutes();
        List<RouteManager.RouteData> stragglers = new ArrayList<RouteManager.RouteData>();

        boolean enoughMadeIt = enoughMadeIt(routes, stragglers);
        if (!enoughMadeIt) {
            this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.FAILED);
            this.status = RaidIntel.RaidStageStatus.FAILURE;
            if (giveReturnOrders) {
                giveReturnOrdersToStragglers(routes);
            }
        }
    }

    @Override
    protected void updateStatusBasedOnReaching(SectorEntityToken dest, boolean giveReturnOrders, boolean requireNearTarget) {
        List<RouteManager.RouteData> routes = getRoutes();
        float maxRange = 2000f;
        if (!requireNearTarget) {
            maxRange = 10000000f;
        }
        List<RouteManager.RouteData> stragglers = getStragglers(routes, dest, maxRange);

        boolean enoughMadeIt = enoughMadeIt(routes, stragglers);

        if (stragglers.isEmpty() && enoughMadeIt) {
            this.status = RaidIntel.RaidStageStatus.SUCCESS;
            return;
        }

        if (this.elapsed > this.maxDays + this.intel.getExtraDays()) {
            if (enoughMadeIt) {
                this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.SUCCEEDED);
                this.status = RaidIntel.RaidStageStatus.SUCCESS;
                if (giveReturnOrders) {
                    giveReturnOrdersToStragglers(stragglers);
                }
            } else {
                this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.FAILED);
                this.status = RaidIntel.RaidStageStatus.FAILURE;
                if (giveReturnOrders) {
                    giveReturnOrdersToStragglers(routes);
                }
            }
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
            info.addPara("The " + forces + " are returning to the " + this.intel.getSystem().getNameWithLowercaseType() + " and is currently carrying a special item.", opad);
        }
    }
}
