package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * @deprecated
 */
@Deprecated(forRemoval = true)
public class AcquisitionAssembleStage extends AssembleStage {
    protected final AcquisitionRaidIntel acquisitionIntel;
    protected float prepDays;
    protected float travelDays;

    public AcquisitionAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint, float durDays, MarketAPI... sources) {
        super(raid, gatheringPoint);
        this.acquisitionIntel = (AcquisitionRaidIntel) raid;
        for (MarketAPI source : sources) {
            addSource(source);
        }
        if (this.sources.isEmpty()) {
            this.status = RaidIntel.RaidStageStatus.FAILURE;
            return;
        }
        for (MarketAPI source : this.sources) {
            this.prepDays = 3f + 3f * (float) Math.random();
            this.travelDays = RouteLocationCalculator.getTravelDays(source.getPrimaryEntity(), this.gatheringPoint);

            this.minDays = Math.max(this.maxDays, this.prepDays + this.travelDays + durDays);
            this.maxDays = this.minDays;
        }
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
        // Add back removed fp per fleet values
        //  during the computation of baseFP
        //  for autoresolve calculations
        extra.strength = fp + this.acquisitionIntel.getBonusStrengthPerFleet();

        route.addSegment(new RouteManager.RouteSegment(this.minDays, market.getPrimaryEntity(), WAIT_STAGE));
        route.addSegment(new RouteManager.RouteSegment(this.travelDays, market.getPrimaryEntity(), this.gatheringPoint));
        route.addSegment(new RouteManager.RouteSegment(1000f, this.gatheringPoint, WAIT_STAGE));
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
    protected float getLargeSize(boolean limitToSpawnFP) {
        return this.acquisitionIntel.getLargeFleetSize();
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

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = this.intel.getCurrentStage();
        int index = this.intel.getStageIndex(this);

        float opad = 10f;

        // Failure descriptions are handled in acquisition raid intel
        if (curr == index) {
            BaseHubMission.addStandardMarketDesc("Making preparations in orbit around", this.gatheringPoint.getMarket(), info, opad);
        }
    }
}
