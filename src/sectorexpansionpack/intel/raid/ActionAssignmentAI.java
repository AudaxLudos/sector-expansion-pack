package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class ActionAssignmentAI extends RouteFleetAssignmentAI implements FleetActionTextProvider {
    public static final String RECENTLY_ACTED_KEY = "$sep_eri_recentlyActedAnAction";
    public static final String RECENTLY_AFFECTED_KEY = "$sep_eri_recentlyAffectedByAction";
    protected IntervalUtil assistTracker;
    protected IntervalUtil actionTracker;
    protected SEPFleetActionDelegate delegate;
    protected boolean captureObjectives = false;

    public ActionAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route, SEPFleetActionDelegate delegate, boolean captureObjectives) {
        super(fleet, route);
        fleet.getAI().setActionTextProvider(this);
        this.delegate = delegate;
        this.captureObjectives = captureObjectives;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount, false);

        RouteManager.RouteSegment curr = this.route.getCurrent();
        if (curr != null &&
                (BaseRaidStage.STRAGGLER.equals(this.route.getCustom()) || AssembleStage.WAIT_STAGE.equals(curr.custom) || curr.isTravel())) {
            Misc.setFlagWithReason(this.fleet.getMemoryWithoutUpdate(), MemFlags.FLEET_BUSY, "action_wait", true, 1);
        }

        checkAssist(amount);

        if (this.captureObjectives) {
            checkCapture(amount);
        }

        checkAction(amount);
    }

    protected void checkAssist(float amount) {
        if (this.assistTracker == null) {
            this.assistTracker = new IntervalUtil(0.15f, 0.30f);
        }
        this.assistTracker.advance(Misc.getDays(amount));
        if (!this.assistTracker.intervalElapsed()) {
            return;
        }

        checkAssistAction();
    }

    protected void checkAssistAction() {
        CampaignFleetAPI fleetToAssist = findNearestFleetNeedingAssist();
        if (fleetToAssist != null) {
            giveAssistOrder(fleetToAssist);
        }
    }

    protected void giveAssistOrder(CampaignFleetAPI fleetToAssist) {
        this.fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, fleetToAssist,
                1,
                "assisting nearby fleet",
                null);
        FleetAssignmentDataAPI curr = this.fleet.getCurrentAssignment();
        if (curr != null) {
            curr.setCustom(TEMP_ASSIGNMENT);
        }
    }

    protected CampaignFleetAPI findNearestFleetNeedingAssist() {
        if (this.fleet.getBattle() != null) {
            return null;    // already in our own battle
        }
        if (this.fleet.getAI().getCurrentAssignmentType() == FleetAssignment.INTERCEPT) {
            return null;    // already assisting?
        }
        for (CampaignFleetAPI otherFleet : Misc.getNearbyFleets(this.fleet, 500f)) {
            if (otherFleet == this.fleet) {
                continue;
            }
            if (otherFleet.getFaction() != this.fleet.getFaction()) {
                continue;
            }
            BattleAPI otherBattle = otherFleet.getBattle();
            if (otherBattle != null) {
                boolean aiWantAssist = this.fleet.getAI().wantsToJoin(otherBattle, otherBattle.isPlayerInvolved());
                if (!aiWantAssist) {
                    // fleet.addFloatingText("No assistance wanted", fleet.getFaction().getBaseUIColor(), 1);
                    continue;
                }

                return otherFleet;
            }
        }
        return null;
    }

    protected void checkAction(float amount) {
        if (this.fleet.isInHyperspace()) {
            return;
        }

        if (this.actionTracker == null) {
            this.actionTracker = new IntervalUtil(0.3f, 0.7f);
        }
        this.actionTracker.advance(Misc.getDays(amount));
        if (!this.actionTracker.intervalElapsed()) {
            return;
        }

        checkEntityAction();
    }

    protected void checkEntityAction() {
        if (!canTakeAction()) {
            return;
        }
        if (this.delegate != null && !this.delegate.getRecentActedKey().isBlank()) {
            if (this.fleet.getMemoryWithoutUpdate().getBoolean(this.delegate.getRecentActedKey())) {
                return;
            }
        } else if (this.fleet.getMemoryWithoutUpdate().getBoolean(RECENTLY_ACTED_KEY)) {
            return;
        }
        RouteManager.RouteSegment currSegment = this.route.getCurrent();
        if (currSegment == null) {
            return;
        }
        if (currSegment.getFrom() == null) {
            return;
        }
        SectorEntityToken target = currSegment.getFrom();
        if (this.fleet.getContainingLocation() != target.getContainingLocation()) {
            return;
        }
        if (this.delegate != null && !this.delegate.getRecentAffectedKey().isBlank()) {
            if (target.getMemoryWithoutUpdate().getBoolean(this.delegate.getRecentAffectedKey())) {
                return;
            }
        } else if (target.getMemoryWithoutUpdate().getBoolean(RECENTLY_AFFECTED_KEY)) {
            return;
        }
        float distToTarget = Misc.getDistance(this.fleet, target);
        if (distToTarget > 2000f) {
            return;
        }
        for (CampaignFleetAPI other : Misc.getNearbyFleets(target, 2000f)) {
            if (other == this.fleet) {
                continue;
            }

            // Checks enemy fleets near target
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
                        return; // Targeting current fleet so don't do action
                    }

                    MemoryAPI mem = other.getMemoryWithoutUpdate();
                    boolean smuggler = mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER);
                    boolean trader = mem.getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET);
                    if (smuggler || trader) {
                        continue;
                    }
                }
                if (other.getFleetPoints() > this.fleet.getFleetPoints() * 0.25f || other.isStationMode()) {
                    return; // Stronger than current fleet or is station then don't do action
                }
            }

            // Checks other friendly fleets near target
            if (other.getFaction() == this.fleet.getFaction()) {
                if (other.isStationMode()) {
                    continue;
                }
                boolean otherFromSameRaid = this.delegate != null && this.delegate.canDoAction(this.fleet, target);
                if (!(Misc.isRaider(other) && !Misc.isWarFleet(other) && !otherFromSameRaid)) {
                    continue;
                }
                if (other.getFleetPoints() > this.fleet.getFleetPoints()) {
                    return; // Stronger than current fleet then don't do action
                }
                if (other.getFleetPoints() == this.fleet.getFleetPoints()) {
                    float dist = Misc.getDistance(other, target);
                    if (dist < distToTarget) {
                        return; // Closer to target than current fleet then don't do action
                    }
                }
            }
        }

        giveActionOrder(target);
    }

    protected void giveActionOrder(SectorEntityToken target) {
        clearTempAssignments(this.fleet);

        float actionDuration = 0.5f;
        if (this.delegate != null) {
            actionDuration = this.delegate.getActionDuration();
        }

        Misc.setFlagWithReason(this.fleet.getMemoryWithoutUpdate(), MemFlags.FLEET_BUSY, TEMP_BUSY_REASON, true, actionDuration);

        String name = target.getName();
        String actText = "performing action at " + name;
        String moveText = "moving to perform action at " + name;
        if (this.delegate != null) {
            String s = this.delegate.getActionApproachText(this.fleet, target);
            if (s != null) {
                moveText = s;
            }

            s = this.delegate.getActionText(this.fleet, target);
            if (s != null) {
                actText = s;
            }
        }

        Vector2f loc = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(target.getLocation(), this.fleet.getLocation()));
        float holdRadius = this.fleet.getRadius() * 0.5f + target.getRadius();
        loc.scale(holdRadius);
        Vector2f.add(loc, target.getLocation(), loc);
        SectorEntityToken holdLoc = target.getContainingLocation().createToken(loc);
        holdLoc.setCircularOrbit(target, Misc.getAngleInDegrees(target.getLocation(), this.fleet.getLocation()), holdRadius, 1000000f);
        this.fleet.getContainingLocation().addEntity(holdLoc);
        Misc.fadeAndExpire(holdLoc, 5f);

        final int fpAtStart = this.fleet.getFleetPoints();
        this.fleet.addAssignmentAtStart(FleetAssignment.HOLD, holdLoc, actionDuration, actText, () -> {
            if (fpAtStart == this.fleet.getFleetPoints()) {
                String recentlyActedKey = RECENTLY_ACTED_KEY;
                String recentlyAffectedKey = RECENTLY_AFFECTED_KEY;
                if (this.delegate != null) {
                    recentlyActedKey = this.delegate.getRecentActedKey();
                    recentlyAffectedKey = this.delegate.getRecentAffectedKey();
                }
                if (this.delegate != null) {
                    if (this.delegate.canDoAction(this.fleet, target)) {
                        this.delegate.performAction(this.fleet, target);
                    }
                } else {
                    Misc.setFlagWithReason(target.getMemoryWithoutUpdate(), recentlyAffectedKey, this.fleet.getFaction().getId(), true, 30f);
                }

                this.fleet.getMemoryWithoutUpdate().set(recentlyActedKey, true, 3f);
                clearTempAssignments(this.fleet);
            }
        });

        FleetAssignmentDataAPI curr = this.fleet.getCurrentAssignment();
        if (curr != null) {
            curr.setCustom(TEMP_ASSIGNMENT);
        }

        float dist = Misc.getDistance(target, this.fleet);
        if (dist > this.fleet.getRadius() + target.getRadius()) {
            this.fleet.addAssignmentAtStart(FleetAssignment.DELIVER_CREW, holdLoc, 3f, moveText, null);
            curr = this.fleet.getCurrentAssignment();
            if (curr != null) {
                curr.setCustom(TEMP_ASSIGNMENT);
            }
        }
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

        SectorEntityToken target;
        if (current.from != null && current.from.getContainingLocation() instanceof StarSystemAPI) {
            target = ((StarSystemAPI) current.from.getContainingLocation()).getCenter();
        } else {
            target = Global.getSector().getHyperspace().createToken(current.from.getLocation().x, current.from.getLocation().y);
        }

        this.fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, target, current.daysMax - current.elapsed, getInSystemActionText(current), goNextScript(current));
    }

    @Override
    protected String getInSystemActionText(RouteManager.RouteSegment segment) {
        if (AssembleStage.WAIT_STAGE.equals(segment.custom)) {
            return "waiting at rendezvous point";
        }
        String s = null;
        if (this.delegate != null) {
            s = this.delegate.getActionInSystemText(this.fleet);
        }
        if (s == null) {
            s = "performing action";
        }
        return s;
    }

    @Override
    protected String getStartingActionText(RouteManager.RouteSegment segment) {
        if (AssembleStage.PREP_STAGE.equals(segment.custom)) {
            String s = null;
            if (this.delegate != null) {
                s = this.delegate.getActionPrepText(this.fleet, segment.from);
            }
            if (s == null) {
                s = "preparing to perform action";
            }
            return s;
        }

        if (segment.from == this.route.getMarket().getPrimaryEntity()) {
            return "orbiting " + this.route.getMarket().getName();
        }

        String s = null;
        if (this.delegate != null) {
            s = this.delegate.getActionDefaultText(this.fleet);
        }
        if (s == null) {
            s = "performing action";
        }
        return s;
    }

    @Override
    public String getActionText(CampaignFleetAPI fleet) {
        FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
        if (curr != null && curr.getAssignment() == FleetAssignment.PATROL_SYSTEM && curr.getActionText() == null) {
            String s = null;
            if (this.delegate != null) {
                s = this.delegate.getActionDefaultText(fleet);
            }
            if (s == null) {
                s = "performing action";
            }
            return s;

        }
        return null;
    }

    public interface SEPFleetActionDelegate {
        boolean canDoAction(CampaignFleetAPI fleet, SectorEntityToken target);

        String getActionApproachText(CampaignFleetAPI fleet, SectorEntityToken target);

        String getActionText(CampaignFleetAPI fleet, SectorEntityToken target);

        void performAction(CampaignFleetAPI fleet, SectorEntityToken target);

        String getActionPrepText(CampaignFleetAPI fleet, SectorEntityToken target);

        String getActionInSystemText(CampaignFleetAPI fleet);

        String getActionDefaultText(CampaignFleetAPI fleet);

        String getRecentActedKey();

        String getRecentAffectedKey();

        float getActionDuration();
    }
}
