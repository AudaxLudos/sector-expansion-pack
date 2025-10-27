package sectorexpansionpack.missions.hub;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMission;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.ai.CampaignFleetAI;
import sectorexpansionpack.missions.FleetEscortMission;

public class EscortFleetAssignmentAI implements EveryFrameScript {
    public static final float TRIGGER_DISTANCE = 1000f;
    public static final float FOLLOW_DISTANCE = 2000f;
    public static final float JUMP_FOLLOW_DISTANCE = 2000f;
    protected CampaignFleetAPI fleet;
    protected FleetEscortMission mission;
    protected IntervalUtil timer = new IntervalUtil(0.9f, 1.1f);

    public EscortFleetAssignmentAI(CampaignFleetAPI fleet, FleetEscortMission mission) {
        this.fleet = fleet;
        this.mission = mission;

        this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, mission.getPersonEntity(), 999999f);
    }

    @Override
    public boolean isDone() {
        return this.mission.isDone();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

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

        FleetAssignmentDataAPI fleetAssignmentData = this.fleet.getCurrentAssignment();
        FleetAssignment currAssignment = fleetAssignmentData.getAssignment();
        SectorEntityToken currTarget = fleetAssignmentData.getTarget();
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        SectorEntityToken entity = this.mission.getGotoEntity();

        System.out.println(currAssignment);
        System.out.println(currTarget);

        if (playerFleet.getContainingLocation() == this.fleet.getContainingLocation()) {
            System.out.println(Misc.getDistance(playerFleet, this.fleet));
            if (Misc.getDistance(playerFleet, this.fleet) < FOLLOW_DISTANCE) {
                if (currAssignment != FleetAssignment.ORBIT_PASSIVE || currTarget != playerFleet) {
                    this.fleet.clearAssignments();
                    this.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, playerFleet, 999999f, "following your fleet");
                }
            } else {
                if (currAssignment != FleetAssignment.HOLD && currTarget != null) {
                    this.fleet.clearAssignments();
                    this.fleet.addAssignment(FleetAssignment.HOLD, null, 999999f, "Holding current location");
                }
            }
        } else if (playerFleet.getContainingLocation() != this.fleet.getContainingLocation()) {
            SectorEntityToken closestJumpPoint = null;
            if (playerFleet.isInHyperspace()) {
                StarSystemAPI system = this.fleet.getStarSystem();
                closestJumpPoint = system.getJumpPoints().get(0);
                float minDistance = Misc.getDistance(this.fleet, closestJumpPoint);
                for (SectorEntityToken jumpPoint : system.getJumpPoints()) {
                    JumpPointAPI jp = (JumpPointAPI) jumpPoint;
                    if (jp.isStarAnchor()) {
                        continue;
                    }
                    if (jp.isGasGiantAnchor()) {
                        continue;
                    }
                    float distance = Misc.getDistance(this.fleet, jumpPoint);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestJumpPoint = jumpPoint;
                    }
                }
            } else if (!playerFleet.isInHyperspaceTransition()) {
                StarSystemAPI system = playerFleet.getStarSystem();
                closestJumpPoint = system.getJumpPoints().get(0);
                float minDistance = Misc.getDistance(this.fleet, closestJumpPoint);
                for (SectorEntityToken jumpPoint : system.getJumpPoints()) {
                    JumpPointAPI jp = (JumpPointAPI) jumpPoint;
                    if (jp.isStarAnchor()) {
                        continue;
                    }
                    if (jp.isGasGiantAnchor()) {
                        continue;
                    }
                    float distance = Misc.getDistance(this.fleet, jumpPoint);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestJumpPoint = jumpPoint;
                    }
                }
            }

            if (closestJumpPoint != null) {
                JumpPointAPI.JumpDestination jumpDestination = new JumpPointAPI.JumpDestination(
                        closestJumpPoint,"test"
                );
                if (!this.fleet.isInHyperspace())  {
                    jumpDestination = new JumpPointAPI.JumpDestination(
                            Global.getSector().getHyperspace().createToken(closestJumpPoint.getLocation()),"test"
                    );
                }

                this.fleet.updateFleetView();
                Global.getSector().doHyperspaceTransition(this.fleet, null, jumpDestination);
            }
        }
    }
}
