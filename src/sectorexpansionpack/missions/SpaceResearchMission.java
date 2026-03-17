package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HTPoints;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import com.fs.starfarer.api.impl.campaign.terrain.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.MissionScenarioSpec;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.events.ht.HTAnomalyResearchFactor;
import sectorexpansionpack.missions.hub.SEPHubMissionWithScenario;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SpaceResearchMission extends SEPHubMissionWithScenario {
    public static final String PROGRESS_STEP_UPDATE = "progress_step_update";
    public static final Logger log = Global.getLogger(SpaceResearchMission.class);
    protected float currProgress;
    protected float maxProgress;
    protected int lastUpdateStep = -1;
    protected boolean isBarEvent;

    public SpaceResearchMission() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setScenario()) {
            log.info("Failed to pick a scenario");
            return false;
        }
        if (!setScenarioType(ScenarioType.class)) {
            log.info("Failed to find scenario type");
            return false;
        }

        if (barEvent) {
            setGiverRank(pickOne(Ranks.CITIZEN, Ranks.ARISTOCRAT));
            setGiverPost(pickOne(Ranks.POST_SCIENTIST, Ranks.POST_ACADEMICIAN));
            setGiverImportance(pickImportance());
            findOrCreateGiver(createdAt, true, false);
        }

        if (!setPersonMissionRef(getPerson(), "$sep_srm_ref")) {
            log.info("Failed to find or create mission giver");
            return false;
        }

        this.isBarEvent = barEvent;
        // Number of days needed to get max progress
        this.maxProgress = genRoundNumber(12, 16);
        if (this.scenarioType == ScenarioType.BLACK_HOLE_RESEARCH) {
            this.maxProgress = genRoundNumber(2, 6);
        } else if (this.scenarioType == ScenarioType.ASTEROID_IMPACTS_RESEARCH) {
            this.maxProgress = genRoundNumber(6, 10);
        } else if (this.scenarioType == ScenarioType.NEUTRON_STAR_RESEARCH) {
            this.maxProgress = genRoundNumber(2, 6);
        }

        makeImportant(getPerson(), "$sep_srm_returnPerson", Stage.DELIVER_DATA);

        setStartingStage(Stage.GATHER_DATA);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_srm_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.DELIVER_DATA, Stage.FAILED_DECIV, getPerson().getMarket());
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, getPerson().getMarket());

        setScenarioCreditReward(this.scenario.getCreditReward());
        setScenarioComplications(Stage.class, log);

        return true;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        float days = Misc.getDays(amount);

        if (this.currProgress >= this.maxProgress && this.currentStage == Stage.GATHER_DATA) {
            setCurrentStage(Stage.DELIVER_DATA, null, null);
        }

        // Update the player in increments of 20%
        int currUpdateStep = (int) (this.currProgress / (this.maxProgress * 0.2f));
        if (currUpdateStep > this.lastUpdateStep) {
            this.lastUpdateStep = currUpdateStep;
            // Don't send update at 0% and 100%
            if (currUpdateStep > 0 && currUpdateStep < 5) {
                sendUpdateIfPlayerHasIntel(PROGRESS_STEP_UPDATE, true);
            }
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI playerLocation = playerFleet.getContainingLocation();
        boolean addResearchData = false;
        if (this.scenarioType == ScenarioType.BLACK_HOLE_RESEARCH) {
            if (!playerFleet.isInHyperspace()) {
                for (CampaignTerrainAPI terrain : playerLocation.getTerrainCopy()) {
                    if (terrain.getPlugin() instanceof EventHorizonPlugin plugin) {
                        addResearchData = plugin.containsEntity(playerFleet);
                    }
                }
            }
        } else if (this.scenarioType == ScenarioType.ASTEROID_IMPACTS_RESEARCH) {
            if (!playerFleet.isInHyperspace()) {
                addResearchData = playerFleet.getMemoryWithoutUpdate().contains("$recentImpact");
            }
        } else if (this.scenarioType == ScenarioType.NEUTRON_STAR_RESEARCH) {
            if (!playerFleet.isInHyperspace()) {
                for (CampaignTerrainAPI terrain : playerLocation.getTerrainCopy()) {
                    if (terrain.getPlugin() instanceof PulsarBeamTerrainPlugin plugin) {
                        addResearchData = plugin.containsEntity(playerFleet);
                    }
                }
            }
        } else if (this.scenarioType == ScenarioType.HYPERSPACE_GHOSTS_RESEARCH) {
            if (playerFleet.isInHyperspace()) {
                List<CustomCampaignEntityAPI> ghosts = Utils.getNearbyEntitiesWithType(playerFleet, Entities.SENSOR_GHOST, 2000);
                addResearchData = !ghosts.isEmpty();
            }
        } else if (this.scenarioType == ScenarioType.HYPERSPACE_ABYSS_RESEARCH) {
            if (playerFleet.isInHyperspace()) {
                HyperspaceTerrainPlugin hyperspace = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
                HyperspaceAbyssPlugin abyss = hyperspace.getAbyssPlugin();
                addResearchData = abyss.isInAbyss(playerFleet);
            }
        }
        if (addResearchData) {
            this.currProgress += days;
        }
    }

    @Override
    public String getBaseName() {
        String prefix = "Space";
        if (this.scenarioType == ScenarioType.BLACK_HOLE_RESEARCH) {
            prefix = "Event Horizon";
        } else if (this.scenarioType == ScenarioType.ASTEROID_IMPACTS_RESEARCH) {
            prefix = "Asteroid Impacts";
        } else if (this.scenarioType == ScenarioType.NEUTRON_STAR_RESEARCH) {
            prefix = "Pulsar Beam";
        } else if (this.scenarioType == ScenarioType.HYPERSPACE_GHOSTS_RESEARCH) {
            prefix = "Hyperspace Anomaly";
        } else if (this.scenarioType == ScenarioType.HYPERSPACE_ABYSS_RESEARCH) {
            prefix = "Abyss";
        }
        return prefix + " Research";
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        HyperspaceTopographyEventIntel intel = HyperspaceTopographyEventIntel.get();
        intel.addFactor(new HTAnomalyResearchFactor(genRoundNumber(HTPoints.HIGH_MIN, HTPoints.HIGH_MAX)), dialog);
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_srm_dataInDays", getDays(this.maxProgress));
        set("$sep_srm_reward", Misc.getDGSCredits(getCreditsReward()));
        set("$sep_srm_isBarEvent", this.isBarEvent);
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();

        if (this.currentStage == Stage.GATHER_DATA) {
            if (getListInfoParam() == PROGRESS_STEP_UPDATE) {
                info.addPara("Research Progress is now at %s", 0f, tc, h, getProgressPercent() + "%");
            } else {
                info.addPara("Gather data from %s", pad, tc, h, getResearchFromEntityText());
                info.addPara("Research Progress: %s", 0f, tc, h, getProgressPercent() + "%");
            }
            return true;
        } else if (this.currentStage == Stage.DELIVER_DATA) {
            info.addPara("Deliver the completed research data to %s at %s, in the %s.", pad, tc, h,
                    getPerson().getName().getFullName(), getPerson().getMarket().getName(),
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort());
            return true;
        }

        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float oPad = 10f;
        Color h = Misc.getHighlightColor();

        if (this.currentStage == Stage.GATHER_DATA) {
            info.addPara("Gather data from %s", oPad, h, getResearchFromEntityText());
            bullet(info);
            info.addPara("Research Progress: %s", oPad, h, getProgressPercent() + "%");
            unindent(info);
            String missionTip = "";
            if (this.scenarioType == ScenarioType.BLACK_HOLE_RESEARCH) {
                missionTip = "According to the researcher, its best to prepare extra supplies and to have a fast fleet to offset the effects inflicted by a black hole's event horizon.";
            } else if (this.scenarioType == ScenarioType.ASTEROID_IMPACTS_RESEARCH) {
                missionTip = "According to the researcher, having a fast fleet and moving at max burn levels within asteroid belts is the best way to get hit by asteroids.";
            } else if (this.scenarioType == ScenarioType.NEUTRON_STAR_RESEARCH) {
                missionTip = "According to the researcher, its best to prepare extra supplies to offset the effects inflicted by a neutron star's pulsar beams.";
            } else if (this.scenarioType == ScenarioType.HYPERSPACE_GHOSTS_RESEARCH) {
                missionTip = "According to the researcher, hyperspace anomalies often occur near the fringes of the sector and often appear near high-energy wave, like those emitted by sensor bursts.";
            } else if (this.scenarioType == ScenarioType.HYPERSPACE_ABYSS_RESEARCH) {
                missionTip = "According to the researcher, to reach the abyss you must go beyond the sector.";
            }
            info.addPara(missionTip, oPad, h);
        } else if (this.currentStage == Stage.DELIVER_DATA) {
            info.addPara("Deliver the completed research data to %s at %s, in the %s.", oPad,
                    new Color[]{h, h, h},
                    getPerson().getName().getFullName(), getPerson().getMarket().getName(),
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort());
        }
    }

    public String getResearchFromEntityText() {
        String text = "Space";
        if (this.scenarioType == ScenarioType.BLACK_HOLE_RESEARCH) {
            text = "black holes";
        } else if (this.scenarioType == ScenarioType.ASTEROID_IMPACTS_RESEARCH) {
            text = "asteroid belts";
        } else if (this.scenarioType == ScenarioType.NEUTRON_STAR_RESEARCH) {
            text = "neutron stars";
        } else if (this.scenarioType == ScenarioType.HYPERSPACE_GHOSTS_RESEARCH) {
            text = "hyperspace anomalies";
        } else if (this.scenarioType == ScenarioType.HYPERSPACE_ABYSS_RESEARCH) {
            text = "abyss space";
        }
        return text;
    }

    public int getProgressPercent() {
        return Math.round(getProgress() * 100f);
    }

    public float getProgress() {
        return Math.min(this.currProgress / this.maxProgress, 1f);
    }

    public enum Stage {
        GATHER_DATA,
        DELIVER_DATA,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }

    public enum ScenarioType {
        BLACK_HOLE_RESEARCH,
        ASTEROID_IMPACTS_RESEARCH,
        NEUTRON_STAR_RESEARCH,
        HYPERSPACE_GHOSTS_RESEARCH,
        HYPERSPACE_ABYSS_RESEARCH
    }
}
