package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

public class ExcavationActionStage extends ActionStage implements ActionAssignmentAI.SEPFleetActionDelegate {
    protected Object delegate;
    protected SectorEntityToken target;

    public ExcavationActionStage(RaidIntel raid, SectorEntityToken target, float durDays) {
        super(raid);
        this.delegate = raid;
        this.target = target;
        this.maxDays = durDays;
    }

    @Override
    protected void updateRoutes() {
        resetRoutes();

        List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(this.intel.getRouteSourceId());
        for (RouteManager.RouteData route : routes) {
            if (this.target.getStarSystem() != null) {
                route.addSegment(new RouteManager.RouteSegment(0.5f, this.target.getStarSystem().getCenter()));
            }
            route.addSegment(new RouteManager.RouteSegment(1000f, this.target));
        }
    }

    @Override
    protected void updateStatus() {
        abortIfNeededBasedOnFP(true);

        if (this.status != RaidIntel.RaidStageStatus.ONGOING) {
            return;
        }

        if (this.target == null) {
            this.status = RaidIntel.RaidStageStatus.FAILURE;
            giveReturnOrdersToStragglers(getRoutes());
            return;
        }

        boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(this.intel.getSystem().getCenter());
        if (!inSpawnRange && this.elapsed > this.maxDays) {
            autoresolve();
            return;
        }

        boolean targetExcavated = Misc.flagHasReason(
                this.target.getMemoryWithoutUpdate(),
                getRecentAffectedKey(),
                this.intel.getFaction().getId());

        if (targetExcavated && this.elapsed > this.maxDays) {
            this.status = RaidIntel.RaidStageStatus.SUCCESS;
        }

        if (!targetExcavated && this.elapsed > this.maxDays) {
            this.status = RaidIntel.RaidStageStatus.FAILURE;
        }
    }

    protected void autoresolve() {
        this.status = RaidIntel.RaidStageStatus.SUCCESS;
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        if (this.delegate instanceof GenericOrganizeStage.ShowStageInfoDelegate delegate1) {
            delegate1.showStageInfo(info, this, this.status);
        } else {
            super.showStageInfo(info);
        }
    }

    @Override
    public boolean isPlayerTargeted() {
        return false;
    }

    @Override
    public boolean canDoAction(CampaignFleetAPI fleet, SectorEntityToken target) {
        return !Misc.flagHasReason(target.getMemoryWithoutUpdate(), getRecentAffectedKey(), this.intel.getFaction().getId());
    }

    @Override
    public String getActionApproachText(CampaignFleetAPI fleet, SectorEntityToken target) {
        if (target instanceof PlanetAPI) {
            return "moving to excavate ruins at " + target.getName();
        }
        return "moving to salvage " + target.getName();
    }

    @Override
    public String getActionText(CampaignFleetAPI fleet, SectorEntityToken target) {
        if (target instanceof PlanetAPI) {
            return "excavating ruins at " + target.getName();
        }
        return "salvaging " + target.getName();
    }

    @Override
    public void performAction(CampaignFleetAPI fleet, SectorEntityToken target) {
        Misc.setFlagWithReason(target.getMemoryWithoutUpdate(), getRecentAffectedKey(), this.intel.getFaction().getId(), true, 30f);
    }

    @Override
    public String getActionPrepText(CampaignFleetAPI fleet, SectorEntityToken target) {
        if (target instanceof PlanetAPI) {
            return "preparing to excavate ruins at " + target.getName();
        }
        return "preparing to salvage " + target.getName();
    }

    @Override
    public String getActionInSystemText(CampaignFleetAPI fleet) {
        return "exploring " + this.target.getContainingLocation().getNameWithLowercaseType();
    }

    @Override
    public String getActionDefaultText(CampaignFleetAPI fleet) {
        return "travelling to " + this.target.getName();
    }

    @Override
    public String getRecentActedKey() {
        return "$sep_eri_recentlyActedAnExcavation";
    }

    @Override
    public String getRecentAffectedKey() {
        return "$sep_eri_recentlyAffectedByExcavation";
    }

    @Override
    public float getActionDuration() {
        return this.intel.getActionStage().getMaxDays() / 5f;
    }
}
