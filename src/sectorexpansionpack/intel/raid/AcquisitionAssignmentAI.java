package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.util.Misc;

/**
 * @deprecated
 */
@Deprecated(forRemoval = true)
public class AcquisitionAssignmentAI extends RaidAssignmentAI {
    public AcquisitionAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route, FleetActionDelegate delegate) {
        super(fleet, route, delegate);
    }

    @Override
    protected void addLocalAssignment(RouteManager.RouteSegment current, boolean justSpawned) {
        if (justSpawned) {
            float progress = current.getProgress();
            RouteLocationCalculator.setLocation(this.fleet, progress,
                    current.from, current.getDestination());
        }

        if (current.from != null && current.to == null && !current.isFromSystemCenter()) {
            this.fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, current.from,
                    current.daysMax - current.elapsed, getInSystemActionText(current),
                    goNextScript(current));
            return;
        }

        if (current.daysMax - current.elapsed <= 0) {
            this.route.goToAtLeastNext(current);
            return;
        }

        SectorEntityToken target = null;
        if (current.from.getContainingLocation() instanceof StarSystemAPI) {
            target = ((StarSystemAPI) current.from.getContainingLocation()).getCenter();
        } else {
            target = Global.getSector().getHyperspace().createToken(current.from.getLocation().x, current.from.getLocation().y);
        }

        if (this.fleet.getContainingLocation() == target.getContainingLocation()) {
            this.fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, target,
                    current.daysMax - current.elapsed, getInSystemActionText(current));
        } else {
            this.fleet.addAssignment(FleetAssignment.DELIVER_MARINES, target, // force fleet to goto location
                    0.1f, getTravelActionText(current), null);
        }
    }

    @Override
    protected void checkColonyAction() {
        if (!canTakeAction()) {
            return;
        }
        if (this.fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.RECENTLY_PERFORMED_RAID)) {
            return;
        }
        RouteManager.RouteSegment currSegment = this.route.getCurrent();
        if (currSegment == null) {
            return;
        }
        if (currSegment.getFrom() == null) {
            return;
        }
        if (currSegment.getFrom().getMarket() == null) {
            return;
        }
        MarketAPI target = currSegment.getFrom().getMarket();
        if (this.fleet.getContainingLocation() != target.getContainingLocation()) {
            return;
        }
        float distToTarget = Misc.getDistance(this.fleet, target.getPrimaryEntity());
        if (distToTarget > 2000f) {
            return;
        }
        for (CampaignFleetAPI other : Misc.getNearbyFleets(target.getPrimaryEntity(), 2000f)) {
            if (other == this.fleet) {
                continue;
            }

            // Checks if the other fleet near target is an enemy
            //  and is stronger or is station then don't do raid
            if (other.isHostileTo(this.fleet)) {
                SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelTo(this.fleet);
                boolean canSee = vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS || vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS;
                if (!canSee && other.getFaction() != this.fleet.getFaction()) {
                    continue;
                }
                if (other.getAI() instanceof ModularFleetAIAPI ai) {
                    if (ai.isFleeing()) {
                        continue;
                    }
                    if (ai.isMaintainingContact()) {
                        continue;
                    }
                    if (ai.getTacticalModule().getTarget() == this.fleet) {
                        return;
                    }

                    MemoryAPI mem = other.getMemoryWithoutUpdate();
                    boolean smuggler = mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER);
                    boolean trader = mem.getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET);
                    if (smuggler || trader) {
                        continue;
                    }
                }
                if (other.getFleetPoints() > this.fleet.getFleetPoints() * 0.25f || other.isStationMode()) {
                    return;
                }
            }

            // Checks if another friendly raider fleet is doing a raid?
            if (other.getFaction() == this.fleet.getFaction()) {
                if (other.isStationMode()) {
                    continue;
                }
                boolean otherFromSameRaid = this.delegate != null && this.delegate.canRaid(other, target);
                if (!(Misc.isRaider(other) && !Misc.isWarFleet(other) && !otherFromSameRaid)) {
                    continue;
                }
                if (other.getFleetPoints() > this.fleet.getFleetPoints()) {
                    return;
                }
                if (other.getFleetPoints() == this.fleet.getFleetPoints()) {
                    float dist = Misc.getDistance(other, target.getPrimaryEntity());
                    if (dist < distToTarget) {
                        return;
                    }
                }
            }
        }

        giveRaidOrder(target);
    }
}
