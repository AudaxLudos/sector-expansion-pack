package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class GenericAssembleStage extends AssembleStage {
    protected Object delegate;
    protected float prepDays;
    protected float travelDays;

    public GenericAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint, float durDays, MarketAPI... sources) {
        super(raid, gatheringPoint);
        this.delegate = raid;
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
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            extra.strength = delegate1.getAdjustedFleetStrength(fp);
        } else {
            extra.strength = Misc.getAdjustedStrength(fp, market);
        }

        route.addSegment(new RouteManager.RouteSegment(this.minDays, market.getPrimaryEntity(), WAIT_STAGE));
        route.addSegment(new RouteManager.RouteSegment(this.travelDays, market.getPrimaryEntity(), this.gatheringPoint));
        route.addSegment(new RouteManager.RouteSegment(1000f, this.gatheringPoint, WAIT_STAGE));
    }

    @Override
    protected String pickNextType() {
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            return delegate1.pickFleetType(this.spawnFP);
        }
        return super.pickNextType();
    }

    @Override
    protected float getFP(String type) {
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            float base = delegate1.getFP(type, this.spawnFP);
            this.spawnFP -= base;
            return base;
        }
        return super.getFP(type);
    }

    @Override
    protected float getLargeSize(boolean limitToSpawnFP) {
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            return delegate1.getLargeSize(limitToSpawnFP);
        }
        return super.getLargeSize(limitToSpawnFP);
    }

    @Override
    protected float getFPLarge() {
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            return delegate1.getFPLarge();
        }
        return super.getFPLarge();
    }

    @Override
    protected float getFPMedium() {
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            return delegate1.getFPMedium();
        }
        return super.getFPMedium();
    }

    @Override
    protected float getFPSmall() {
        if (this.delegate instanceof AssembleStageDelegate delegate1) {
            return delegate1.getFPSmall();
        }
        return super.getFPSmall();
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        if (this.delegate instanceof GenericOrganizeStage.ShowStageInfoDelegate delegate1) {
            delegate1.showStageInfo(info, this, this.status);
        } else {
            super.showStageInfo(info);
        }
    }

    public interface AssembleStageDelegate {
        float getAdjustedFleetStrength(float fp);

        String pickFleetType(float remainingFP);

        float getFP(String fleetType, float remainingFP);

        float getLargeSize(boolean limitToSpawnFP);

        float getFPLarge();

        float getFPMedium();

        float getFPSmall();
    }
}
