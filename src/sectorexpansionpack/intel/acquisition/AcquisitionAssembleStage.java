package sectorexpansionpack.intel.acquisition;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class AcquisitionAssembleStage extends AssembleStage {
    protected AcquisitionRaidIntel acquisitionIntel;

    public AcquisitionAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint) {
        super(raid, gatheringPoint);
        this.acquisitionIntel = (AcquisitionRaidIntel) raid;
    }

    @Override
    protected void addRoutesAsNeeded(float amount) {
        if (this.spawnFP <= 0) {
            return;
        }

        float days = Misc.getDays(amount);

        this.interval.advance(days);
        if (!this.interval.intervalElapsed()) {
            return;
        }

        if (this.sources.isEmpty()) {
            this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.ABORTED_IN_PLANNING);
            this.status = RaidIntel.RaidStageStatus.FAILURE;
            return;
        }

        MarketAPI market = this.sources.get(this.currSource);
        if (!market.isInEconomy() || !market.getPrimaryEntity().isAlive()) {
            this.sources.remove(market);
            return;
        }

        this.currSource++;
        this.currSource %= this.sources.size();


        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);

        String sid = this.intel.getRouteSourceId();
        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, this.intel, null);

        extra.fleetType = pickNextType();
        float fp = getFP(extra.fleetType);

        extra.fp = fp;
        extra.strength = Misc.getAdjustedStrength(fp, market); // TODO: Will possibly change this


        float prepDays = 3f + 3f * (float) Math.random();
        float travelDays = RouteLocationCalculator.getTravelDays(market.getPrimaryEntity(), this.gatheringPoint);

        if (DebugFlags.RAID_DEBUG || DebugFlags.FAST_RAIDS) {
            prepDays *= 0.1f;
            travelDays *= 0.1f;
        }

        route.addSegment(new RouteManager.RouteSegment(prepDays, market.getPrimaryEntity(), PREP_STAGE));
        route.addSegment(new RouteManager.RouteSegment(travelDays, market.getPrimaryEntity(), this.gatheringPoint));
        route.addSegment(new RouteManager.RouteSegment(1000f, this.gatheringPoint, WAIT_STAGE));

        this.maxDays = Math.max(this.maxDays, prepDays + travelDays);
    }

    @Override
    protected String pickNextType() {
        if (this.spawnFP >= getFPLarge()) {
            return FleetTypes.PATROL_LARGE;
        } else if (this.spawnFP >= getFPMedium()) {
            return FleetTypes.PATROL_MEDIUM;
        }
        return FleetTypes.PATROL_SMALL;
    }

    @Override
    protected float getFP(String type) {
        float base = getFPSmall();
        if (FleetTypes.PATROL_LARGE.equals(type)) {
            base = getFPLarge();
        } else if (FleetTypes.PATROL_MEDIUM.equals(type)) {
            base = getFPMedium();
        }

        if (base > this.spawnFP) {
            base = this.spawnFP;
        }

        this.spawnFP -= base;

        if (this.spawnFP < getFPSmall() * 0.5f) {
            base += this.spawnFP;
            this.spawnFP = 0f;
        }

        return base;
    }

    @Override
    protected float getFPLarge() {
        return this.acquisitionIntel.getLargeFleetSize();
    }

    @Override
    protected float getFPMedium() {
        return this.acquisitionIntel.getMediumFleetSize();
    }

    @Override
    protected float getFPSmall() {
        return this.acquisitionIntel.getSmallFleetSize();
    }

    // Same as vanilla but sets acquisition outcomes when stage fails
    @Override
    protected void abortIfNeededBasedOnFP(boolean giveReturnOrders) {
        List<RouteManager.RouteData> routes = getRoutes();
        List<RouteManager.RouteData> stragglers = new ArrayList<RouteManager.RouteData>();

        boolean enoughMadeIt = enoughMadeIt(routes, stragglers);
        if (!enoughMadeIt) {
            this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.TASK_FORCE_DEFEATED);
            this.status = RaidIntel.RaidStageStatus.FAILURE;
            if (giveReturnOrders) {
                giveReturnOrdersToStragglers(routes);
            }
        }
    }

    // Same as vanilla but sets acquisition outcomes when stage fails
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
                this.status = RaidIntel.RaidStageStatus.SUCCESS;
                if (giveReturnOrders) {
                    giveReturnOrdersToStragglers(stragglers);
                }
            } else {
                this.acquisitionIntel.terminateEvent(AcquisitionRaidIntel.AcquisitionOutcome.NOT_ENOUGH_MADE_IT);
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

        float opad = 10f;
        String forces = this.acquisitionIntel.getForcesNoun();

        // Failure descriptions are handled in acquisition raid intel
        if (curr == index) {
            if (isSourceKnown()) {
                info.addPara("The " + forces + " is currently assembling in the " + this.gatheringPoint.getContainingLocation().getNameWithLowercaseType() + ".", opad);
            } else {
                info.addPara("The " + forces + " is currently assembling at an unknown location.", opad);
            }
        }
    }
}
