package sectorexpansionpack.missions.hub;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.shared.PersonBountyEventData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import org.apache.log4j.Logger;
import sectorexpansionpack.MissionScenarioSpec;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.FleetEscortMission;

import java.util.List;

public abstract class SEPHubMissionWithScenario extends SEPHubMissionWithBarEvent {
    // TODO: move common methods from all existing quests with scenarios
    protected MissionScenarioSpec scenario;
    protected Object scenarioType;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        this.scenario = Utils.pickMissionScenario(getMissionId(), getGenRandom());
        if (Utils.isInEnum(this.scenario.getType(), FleetEscortMission.ScenarioType.class)) {
            this.scenarioType = FleetEscortMission.ScenarioType.valueOf(this.scenario.getType());
        } else {
            return false;
        }
        return false;
    }

    public boolean getScenario() {
        this.scenario = Utils.pickMissionScenario(getMissionId(), getGenRandom());
        return this.scenario != null;
    }

    public <E extends Enum<E>> boolean getScenarioType(Class<E> enumClass) {
        if (Utils.isInEnum(this.scenario.getType(), enumClass)) {
            this.scenarioType = Enum.valueOf(enumClass, this.scenario.getType());
        }

        return this.scenarioType != null;
    }

    public <E extends Enum<E>> void setScenarioComplications(Class<E> enumClass, Logger log) {
        for (String complication : this.scenario.getComplications()) {
            List<String> tags = List.of(complication.split(","));

            float chance = 1f;
            if (tags.contains("chanceLOW")) {
                chance = 0.25f;
            } else if (tags.contains("chanceMID")) {
                chance = 0.5f;
            } else if (tags.contains("chanceHIGH")) {
                chance = 0.75f;
            }

            if (!rollProbability(chance)) {
                if (log != null) {
                    log.info("Failed to spawn due to roll check");
                }
                continue;
            }

            if (!Utils.isInEnum(tags.get(0), enumClass)) {
                if (log != null) {
                    log.info("Failed to find stage for complication");
                }
                continue;
            }
            if (Global.getSector().getFaction(tags.get(1)) == null) {
                if (log != null) {
                    log.info("Failed to find faction for complication");
                }
                continue;
            }

            FleetEscortMission.Stage stage = FleetEscortMission.Stage.valueOf(tags.get(0));
            String faction = tags.get(1);
            boolean hyperspaceOnly = tags.contains("hyperspaceOnly");

            SectorEntityToken gotoEntity = getGotoEntity(stage);
            if (gotoEntity == null) {
                continue;
            }

            int difficulty = getDifficulty(tags);

            float rangeLY = 3f;
            if (tags.contains("LY1")) {
                rangeLY = 1f;
            } else if (tags.contains("LY2")) {
                rangeLY = 2f;
            } else if (tags.contains("LY4")) {
                rangeLY = 4f;
            } else if (tags.contains("LY5")) {
                rangeLY = 5f;
            }

            beginWithinHyperspaceRangeTrigger(gotoEntity, rangeLY, hyperspaceOnly, stage);
            triggerCreateStandardFleet(difficulty, faction, gotoEntity.getLocationInHyperspace());
            if (tags.contains("hostile")) {
                triggerSetFleetFlagsWithReason(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
            }
            if (tags.contains("aggressive")) {
                triggerSetFleetFlagsWithReason(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
            }
            if (tags.contains("longPursuit")) {
                triggerSetFleetFlagPermanent(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);
            }
            if (tags.contains("alwaysPursuit")) {
                triggerSetFleetFlag(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE);
            }
            if (tags.contains("lowRep")) {
                triggerMakeLowRepImpact();
            } else {
                triggerMakeNoRepImpact();
            }
            if (tags.contains("nearPlayer")) {
                triggerPickLocationAroundPlayer(1000f);
            } else {
                triggerPickLocationAroundEntity(gotoEntity, 90f);
            }
            setCustomTagTriggers(tags);
            triggerSpawnFleetAtPickedLocation();
            endTrigger();
        }
    }

    public int getDifficulty(List<String> tags) {
        int difficulty = 5;
        SharedData sharedData = SharedData.getData();
        if (sharedData != null) {
            PersonBountyEventData bountyData = sharedData.getPersonBountyEventData();
            if (bountyData != null) {
                difficulty = getGenRandom().nextInt(bountyData.getLevel(), bountyData.getLevel() + getGenRandom().nextInt(1, 3));
            }
        }
        if (tags.contains("strLOW")) {
            difficulty = getGenRandom().nextInt(1, 3);
        } else if (tags.contains("strMID")) {
            difficulty = getGenRandom().nextInt(4, 6);
        } else if (tags.contains("strHIGH")) {
            difficulty = getGenRandom().nextInt(7, 9);
        } else if (tags.contains("strMAX")) {
            difficulty = 10;
        }
        return difficulty;
    }

    public void setCustomTagTriggers(List<String> tags) {
    }

    public SectorEntityToken getGotoEntity(Object stage) {
        return null;
    }
}
