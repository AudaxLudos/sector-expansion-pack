package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

public class AcquisitionAssignmentAI extends RaidAssignmentAI {
    public AcquisitionAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route, FleetActionDelegate delegate) {
        super(fleet, route, delegate);
    }

    @Override
    protected void pickNext(boolean justSpawned) {
        RouteManager.RouteSegment current = route.getCurrent();
        if (current == null) return;

        List<RouteManager.RouteSegment> segments = route.getSegments();
        int index = route.getSegments().indexOf(route.getCurrent());


        if (index == 0 && route.getMarket() != null && !current.isTravel()) {
            System.out.println("SHOULD NOT HAPPEN");
            if (current.getFrom() != null && (current.getFrom().isSystemCenter() || current.getFrom().getMarket() != route.getMarket())) {
                addLocalAssignment(current, justSpawned);
            } else {
                addStartingAssignment(current, justSpawned);
            }
            return;
        }

        if (index == segments.size() - 1 && route.getMarket() != null && !current.isTravel()
                && (current.elapsed >= current.daysMax || current.getFrom() == route.getMarket().getPrimaryEntity())) {
            System.out.println("SHOULD NOT HAPPEN");
            addEndingAssignment(current, justSpawned);
            return;
        }

        // transiting from current to next; may or may not be in the same star system
        if (current.isTravel()) {
            System.out.println("SHOULD HAPPEN");
            if (index == segments.size() - 1 &&
                    fleet.getContainingLocation() == current.to.getContainingLocation() &&
                    current.elapsed >= current.daysMax) {
                addEndingAssignment(current, justSpawned);
            } else {
                System.out.println("SHOULD HAPPEN");
                addTravelAssignment(current, justSpawned);
            }
            return;
        }

        // in a system or in a hyperspace location for some time
        if (!current.isTravel()) {
            addLocalAssignment(current, justSpawned);
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
