package sectorexpansionpack.missions.hub;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.FleetEscortMission;

import java.util.ArrayList;
import java.util.List;

public class EscortFleetAssignmentAI implements EveryFrameScript, Script {
    public static final float TRIGGER_DISTANCE = 1000f;
    public static final float FOLLOW_DISTANCE = 2000f;
    public static final float JUMP_FOLLOW_DISTANCE = 2000f;
    public static Logger log = Global.getLogger(EscortFleetAssignmentAI.class);

    protected CampaignFleetAPI fleet;
    protected FleetEscortMission mission;
    protected IntervalUtil timer = new IntervalUtil(0.2f, 0.4f);

    public EscortFleetAssignmentAI(CampaignFleetAPI fleet, FleetEscortMission mission) {
        this.fleet = fleet;
        this.mission = mission;

        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, mission.getPerson().getMarket().getPrimaryEntity(), 999999f);
    }

    @Override
    public boolean isDone() {
        return this.mission.isDone();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    // IDEA: This implementation sucks update it to a better one if possible
    @Override
    public void advance(float amount) {
        if (this.fleet.isInHyperspaceTransition()) {
            return;
        }

        float days = Misc.getDays(amount);
        this.timer.advance(days);
        if (!this.timer.intervalElapsed()) {
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        SectorEntityToken entity = this.mission.getGotoEntity();

        if (!this.fleet.isInHyperspace()) {
            log.info(String.format("The escorted fleet is currently in the %s [%s]", this.fleet.getStarSystem().getName(), this.fleet.getLocation()));
        } else {
            log.info(String.format("The escorted fleet is currently in hyperspace [%s]", this.fleet.getLocationInHyperspace()));
        }

        if (this.mission.getCurrentStage() == FleetEscortMission.Stage.GOTO || this.mission.getCurrentStage() == FleetEscortMission.Stage.RETURN) {
            if (this.fleet.isInCurrentLocation()) {
                if (Misc.getDistance(playerFleet, this.fleet) < FOLLOW_DISTANCE) {
                    this.fleet.clearAssignments();
                    this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, playerFleet, 999999f, "following your fleet");
                } else {
                    this.fleet.clearAssignments();
                    this.fleet.addAssignment(FleetAssignment.HOLD, Utils.getClosestJumpPoint(this.fleet), 999999f, "holding current location");
                }
            } else if (playerFleet.getContainingLocation() != this.fleet.getContainingLocation()) {
                if (Misc.getDistance(playerFleet.getLocationInHyperspace(), this.fleet.getLocationInHyperspace()) < JUMP_FOLLOW_DISTANCE) {
                    if (this.fleet.isInHyperspaceTransition() || playerFleet.isInHyperspaceTransition()) {
                        return;
                    }
                    // Hyperspace jump points are different from system jump points
                    List<SectorEntityToken> jumpPoints = new ArrayList<>();
                    if (this.fleet.isInHyperspace()) {
                        jumpPoints.addAll(Utils.getHyperspaceJumpPoints(playerFleet.getStarSystem()));
                        jumpPoints.addAll(playerFleet.getContainingLocation().getJumpPoints());
                    } else {
                        jumpPoints.addAll(Utils.getHyperspaceJumpPoints(this.fleet.getStarSystem()));
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
                    if (!jumpDestinations.isEmpty()) {
                        float min = Misc.getDistance(playerFleet, jumpDestinations.get(0).getDestination());
                        for (JumpPointAPI.JumpDestination jd : jumpDestinations) {
                            float dist = Misc.getDistance(playerFleet, jd.getDestination());
                            if (min > dist) {
                                min = dist;
                                closestJumpDestination = jd;
                            }
                        }
                    }

                    if (closestJumpDestination != null) {
                        this.fleet.updateFleetView();
                        Global.getSector().doHyperspaceTransition(this.fleet, null, closestJumpDestination);
                    }
                } else {
                    this.fleet.clearAssignments();
                    this.fleet.addAssignment(FleetAssignment.HOLD, Utils.getClosestJumpPoint(this.fleet), 999999f, "holding current location");
                }
            }

            if (this.fleet.getContainingLocation() == entity.getContainingLocation()) {
                float distance = Misc.getDistance(this.fleet, entity);
                if (distance <= TRIGGER_DISTANCE) {
                    if (this.mission.getCurrentStage() == FleetEscortMission.Stage.GOTO) {
                        this.mission.setCurrentStage(FleetEscortMission.Stage.WAIT, null, null);
                        this.fleet.clearAssignments();
                        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, entity, 3f + this.mission.getGenRandom().nextFloat() * 7f, "Standing down at " + entity.getName(), this);
                    } else if (this.mission.getCurrentStage() == FleetEscortMission.Stage.RETURN) {
                        this.mission.setCurrentStage(FleetEscortMission.Stage.COMPLETED, null, null);
                        this.fleet.clearAssignments();
                        this.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, entity, 999999f, "Standing down at " + entity.getName(), this);
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        this.mission.setCurrentStage(FleetEscortMission.Stage.RETURN, null, null);
    }
}
