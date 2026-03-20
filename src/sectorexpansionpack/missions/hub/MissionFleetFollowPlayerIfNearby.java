package sectorexpansionpack.missions.hub;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.FleetEscortMission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MissionFleetFollowPlayerIfNearby implements EveryFrameScript {
    public static Logger log = Global.getLogger(MissionFleetFollowPlayerIfNearby.class);
    protected final CampaignFleetAPI fleet;
    protected final BaseHubMission mission;
    protected final Set<Object> stages = new HashSet<>();
    protected final float maxRange;
    protected final IntervalUtil timer = new IntervalUtil(0.2f, 0.4f);
    protected boolean done = false;

    public MissionFleetFollowPlayerIfNearby(CampaignFleetAPI fleet, BaseHubMission mission, float maxRange, List<Object> stages) {
        this.fleet = fleet;
        this.mission = mission;
        this.maxRange = maxRange;
        this.stages.addAll(stages);
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (this.done) {
            return;
        }
        if (this.mission.isEnding()) {
            Misc.giveStandardReturnToSourceAssignments(this.fleet);
            this.done = true;
            return;
        } else {
            this.fleet.getStats().getFleetwideMaxBurnMod().modifyFlat("sep_fleet_follow_script", 20);
        }
        if (this.fleet.getBattle() != null) {
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        if (playerFleet.isInHyperspaceTransition() || this.fleet.isInHyperspaceTransition()) {
            return;
        }

        float days = Misc.getDays(amount);
        this.timer.advance(days);
        if (!this.timer.intervalElapsed()) {
            return;
        }

        FleetEscortMission.Stage currStage = (FleetEscortMission.Stage) this.mission.getCurrentStage();

        boolean currStageRelevant = this.stages.contains(currStage);
        boolean sameLocAsPlayer = this.fleet.isInCurrentLocation();
        float distance = sameLocAsPlayer
                ? Misc.getDistance(playerFleet, this.fleet)
                : Misc.getDistance(playerFleet.getLocationInHyperspace(), this.fleet.getLocationInHyperspace());
        boolean canFollowOrJump = distance < this.maxRange;

        FleetAssignmentDataAPI currAssignment = this.fleet.getCurrentAssignment();

        if (currAssignment != null && currStageRelevant) {
            boolean targetIsPlayer = currAssignment.getTarget() == playerFleet;
            boolean willJump = currAssignment.getAssignment() == FleetAssignment.GO_TO_LOCATION;
            boolean shouldClear = ((sameLocAsPlayer && ((canFollowOrJump && !targetIsPlayer) || (!canFollowOrJump && targetIsPlayer) || (willJump && !targetIsPlayer))))
                    || (!sameLocAsPlayer && ((canFollowOrJump && !willJump) || (!canFollowOrJump && targetIsPlayer)));

            if (shouldClear) {
                this.fleet.clearAssignments();
                currAssignment = null;
            }
        }

        if (currAssignment == null && currStageRelevant) {
            FleetAssignment assignmentType;
            SectorEntityToken target;
            String assignmentText;

            if (sameLocAsPlayer) {
                if (canFollowOrJump) {
                    assignmentType = FleetAssignment.ORBIT_PASSIVE;
                    target = playerFleet;
                    assignmentText = "following your fleet";
                } else {
                    assignmentType = FleetAssignment.ORBIT_PASSIVE;
                    target = Utils.getClosestJumpPoint(this.fleet);
                    assignmentText = "holding current location";
                }
            } else {
                if (canFollowOrJump) {
                    assignmentType = FleetAssignment.GO_TO_LOCATION;
                    target = playerFleet.getContainingLocation().createToken(playerFleet.getLocation());
                    assignmentText = "following your fleet";

                    // Hyperspace jump points are different from system jump points
                    List<SectorEntityToken> jumpPoints = new ArrayList<>();
                    if (this.fleet.isInHyperspace()) {
                        jumpPoints.addAll(Utils.getHyperspaceJumpPoints(playerFleet.getStarSystem()));
                    } else {
                        jumpPoints.addAll(this.fleet.getContainingLocation().getJumpPoints());
                    }

                    // Find jump points that will lead to the players current layer e.g. in hyperspace or in star system
                    List<JumpPointAPI.JumpDestination> jumpDestinations = new ArrayList<>();
                    for (SectorEntityToken e : jumpPoints) {
                        JumpPointAPI jp = (JumpPointAPI) e;
                        for (JumpPointAPI.JumpDestination jd : jp.getDestinations()) {
                            if (jd.getDestination() == null) {
                                continue;
                            }
                            if (jd.getDestination().getContainingLocation() == playerFleet.getContainingLocation()) {
                                jumpDestinations.add(jd);
                            }
                        }
                    }

                    // Find the closest jump point of for the escorted fleet to travel through
                    JumpPointAPI.JumpDestination closestJumpDestination = null;
                    float min = Float.MAX_VALUE;
                    for (JumpPointAPI.JumpDestination jd : jumpDestinations) {
                        float dist = Misc.getDistance(playerFleet, jd.getDestination());
                        if (min > dist) {
                            min = dist;
                            closestJumpDestination = jd;
                        }
                    }

                    if (closestJumpDestination != null) {
                        this.fleet.updateFleetView();
                        Global.getSector().doHyperspaceTransition(this.fleet, null, closestJumpDestination);
                    }
                } else {
                    assignmentType = FleetAssignment.ORBIT_PASSIVE;
                    target = Utils.getClosestJumpPoint(this.fleet);
                    assignmentText = "holding current location";
                }
            }

            this.fleet.addAssignmentAtStart(assignmentType, target, 999999f, assignmentText, null);
        }
    }
}
