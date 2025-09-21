package sectorexpansionpack.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import sectorexpansionpack.ModPlugin;

import java.awt.*;
import java.util.*;
import java.util.List;

// TODO: Add bonus reward for returning survivor alive
public class SearchAndRescueMission extends HubMissionWithBarEvent {
    public static Logger log = Global.getLogger(SearchAndRescueMission.class);
    // TODO: Make mission days modifiable using the scenario settings
    protected float missionDays = 120f;
    protected JSONObject scenarioData;
    protected ScenarioType scenarioType;
    protected PersonPostType survivorPostType;
    protected PersonAPI survivor;
    protected boolean survivorAlive = true;
    protected EntityType entityType;
    protected SectorEntityToken entity;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        try {
            this.genRandom = new Random(Long.parseLong(Global.getSector().getSeedString().replaceAll("\\D", "")));
            this.scenarioData = ModPlugin.getRandomMissionScenario(getMissionId(), this.genRandom);

            if (barEvent) {
                // TODO: Make this bar contact modifiable using the scenario settings
                setGiverRank(Ranks.CITIZEN);
                setGiverPost(pickOne(Ranks.POST_AGENT, Ranks.POST_SMUGGLER, Ranks.POST_GANGSTER, Ranks.POST_FENCE, Ranks.POST_CRIMINAL));
                setGiverImportance(pickImportance());
                setGiverFaction(Factions.PIRATES);
                setGiverTags(Tags.CONTACT_TRADE);
                findOrCreateGiver(createdAt, true, false);
            }

            if (!setPersonMissionRef(getPerson(), "$sep_sar_ref")) {
                log.info("Failed to find or create contact");
                return false;
            }

            if (barEvent) {
                setGiverIsPotentialContactOnSuccess();
            }

            this.survivor = createdAt.getFaction().createRandomPerson(this.genRandom);
            if (this.survivor == null) {
                log.info("Failed to create survivor to rescue");
                return false;
            }

            this.survivorPostType = PersonPostType.valueOf(this.scenarioData.getString("survivorType"));
            if (this.survivorPostType == PersonPostType.RANDOM) {
                this.survivorPostType = (PersonPostType) pickOneObject(Arrays.asList(PersonPostType.OFFICER, PersonPostType.ADMINISTRATOR, PersonPostType.CIVILIAN));
            }
            if (this.survivorPostType == PersonPostType.OFFICER) {
                this.survivor = OfficerManagerEvent.createOfficer(createdAt.getFaction(), 1, OfficerManagerEvent.SkillPickPreference.ANY, this.genRandom);
                this.survivor.setPostId(Ranks.POST_OFFICER_FOR_HIRE);
            } else if (this.survivorPostType == PersonPostType.ADMINISTRATOR) {
                this.survivor = OfficerManagerEvent.createAdmin(createdAt.getFaction(), 1, this.genRandom);
            } else {
                this.survivor = createdAt.getFaction().createRandomPerson(this.genRandom);
            }

            if (this.survivor == null) {
                log.info("Failed to create survivor");
                return false;
            }

            preferSystemInInnerSector();
            preferSystemInDirectionOfOtherMissions();

            this.entityType = EntityType.valueOf(this.scenarioData.getString("entityType"));
            switch (this.entityType) {
                case WRECK -> {
                    requireEntityTags(ReqMode.ALL, Tags.SALVAGEABLE);
                    requireEntityType(Entities.WRECK);
                    this.entity = pickEntity();
                }
                case FLEET -> {
                    requireSystemHasSafeStars();
                    requireSystemTags(ReqMode.NOT_ALL, Tags.THEME_UNSAFE);
                    preferPlanetNotFullySurveyed();
                    preferPlanetUnpopulated();
                    preferPlanetWithRuins();
                    PlanetAPI planet = pickPlanet();

                    // TODO: Make this fleet modifiable using the scenario settings
                    beginStageTrigger(Stage.FIND);
                    triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, Factions.PIRATES, FleetTypes.PATROL_MEDIUM, planet.getStarSystem());
                    triggerAutoAdjustFleetStrengthModerate();

                    triggerPickLocationAroundEntity(planet, 1000f);
                    triggerSpawnFleetAtPickedLocation();

                    triggerMakeLowRepImpact();
                    triggerMakeFleetIgnoreOtherFleets();
                    triggerMakeFleetIgnoredByOtherFleets();
                    triggerMakeFleetNotIgnorePlayer();
                    triggerOrderFleetPatrol(planet);
                    triggerFleetAddDefeatTrigger("SEPSARFleetDefeated");

                    endTrigger();

                    List<CampaignFleetAPI> fleets = runStageTriggersReturnFleets(Stage.FIND);
                    this.entity = fleets.get(0);
                }
                case PLANET -> {
                    requireSystemInterestingAndNotCore();
                    preferPlanetNotFullySurveyed();
                    preferPlanetUnpopulated();
                    preferPlanetWithRuins();
                    this.entity = pickPlanet();
                }
                default -> {
                    this.entity = null;
                }
            }

            if (!setEntityMissionRef(this.entity, "$sep_sar_ref")) {
                log.info("Failed to find entity containing survivor");
                return false;
            }

            makeImportant(this.entity, "$sep_sar_survivorEntity", Stage.FIND);
            makeImportant(getPerson(), "$sep_sar_contactPerson", Stage.RETURN);

            setStartingStage(Stage.FIND);
            setSuccessStage(Stage.COMPLETED);
            addFailureStages(Stage.FAILED);

            connectWithMemoryFlag(Stage.FIND, Stage.RETURN, this.entity, "$sep_sar_returnToContact");
            setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_sar_completed");

            addNoPenaltyFailureStages(Stage.FAILED_DECIV);
            connectWithMarketDecivilized(Stage.RETURN, Stage.FAILED_DECIV, createdAt);
            setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

            setTimeLimit(Stage.FAILED, this.missionDays, null, Stage.RETURN);

            // TODO: Make this reward modifiable using the scenario settings
            setCreditReward(CreditReward.HIGH);

            // TODO: Add mission complications

            return true;
        } catch (JSONException e) {
            log.error(e);
            return false;
        }
    }

    @Override
    public String getBaseName() {
        return "Search and Rescue: " + this.survivor.getNameString();
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_sar_scenarioType", this.scenarioType);
        set("$sep_sar_survivorAlive", this.survivorAlive);
        set("$sep_sar_survivorPostType", this.survivorPostType);
        set("$sep_sar_entityType", this.entityType);
        set("$sep_sar_creditReward", Misc.getDGSCredits(getCreditsReward()));

        String loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
        set("$sep_sar_possibleLoc", loc);

        set("$sep_sar_survivorFullName", this.survivor.getNameString());
        set("$sep_sar_survivorFirstName", this.survivor.getName().getFirst());
        set("$sep_sar_survivorLastName", this.survivor.getName().getLast());
        set("$sep_sar_survivorHeOrShe", this.survivor.getHeOrShe());
        set("$sep_sar_survivorHisOrHer", this.survivor.getHisOrHer());
        set("$sep_sar_survivorHimOrHer", this.survivor.getHimOrHer());
        set("$sep_sar_survivorManOrWoman", this.survivor.getManOrWoman());

        set("$sep_sar_contactMissionBlurb", getDialogText("contactMissionBlurb"));
        set("$sep_sar_contactMissionOfferText", getDialogText("contactMissionOfferText"));
        set("$sep_sar_barMissionBlurb", getDialogText("barMissionBlurb"));
        set("$sep_sar_barMissionOfferText", getDialogText("barMissionOfferText"));

        set("$sep_sar_entityDialogText", getDialogText("entityDialogText"));
        set("$sep_sar_entityPayRansomText", getDialogText("entityPayRansomText"));
        set("$sep_sar_entityFightText", getDialogText("entityFightText"));
        set("$sep_sar_entityDeclineText", getDialogText("entityDeclineText"));
        set("$sep_sar_entityDefeatedText", getDialogText("entityDefeatedText"));
        set("$sep_sar_survivorAliveText", getDialogText("survivorAliveText"));
        set("$sep_sar_survivorDeadText", getDialogText("survivorDeadText"));

        set("$sep_sar_returnSurvivorAliveText", getDialogText("returnSurvivorAliveText"));
        set("$sep_sar_survivorDialogText", getDialogText("survivorDialogText"));
        set("$sep_sar_returnSurvivorDeadText", getDialogText("returnSurvivorDeadText"));
    }

    public String getDialogText(String id) {
        String result = "";
        try {
            if (this.scenarioData.has(id)) {
                result = this.scenarioData.getString(id);
            } else {
                result = ModPlugin.getMissionScenarioDefaults(getMissionId()).getString(id);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (this.currentStage == Stage.FIND) {
            String loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
            info.addPara("Search for %s in " + loc, 3f, tc, Misc.getHighlightColor(), this.survivor.getNameString());
            return true;
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Return to " + getPerson().getMarket().getName() + " in the "
                            + getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                            + " and talk to " + getPerson().getNameString() + ".",
                    3f, tc, Misc.getHighlightColor(), this.survivor.getNameString());
            return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (this.currentStage == Stage.FIND) {
            String loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
            info.addPara("Search for %s in " + loc, 10f, Misc.getHighlightColor(), this.survivor.getNameString());
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Return with %s to " + getPerson().getMarket().getName() + " in the "
                            + getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                            + " and talk to " + getPerson().getNameString() + ".",
                    10f, Misc.getHighlightColor(), this.survivor.getNameString());
        }
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if (action.equals("checkSurvivorStatus")) {
            if (this.timeLimit.days - this.elapsed < 60f) {
                this.survivorAlive = false;
            }
            updateInteractionData(dialog, memoryMap);
            return this.survivorAlive;
        } else if (action.equals("showSurvivorVisual")) {
            dialog.getVisualPanel().showPersonInfo(this.survivor);
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (this.survivorAlive && this.survivorPostType != PersonPostType.CIVILIAN) {
            for (EveryFrameScript script : Global.getSector().getScripts()) {
                if (script instanceof OfficerManagerEvent manager) {
                    float salary = (this.survivorPostType == PersonPostType.OFFICER ?
                            Misc.getOfficerSalary(this.survivor) : Misc.getAdminSalary(this.survivor)) * 0.5f;
                    OfficerManagerEvent.AvailableOfficer officer = new OfficerManagerEvent.AvailableOfficer(
                            this.survivor, getPerson().getMarket().getId(), 0, Math.round(salary));

                    if (this.survivorPostType == PersonPostType.OFFICER) {
                        manager.addAvailable(officer);
                    } else if (this.survivorPostType == PersonPostType.ADMINISTRATOR) {
                        manager.addAvailableAdmin(officer);
                    }

                    officer.person.getMemoryWithoutUpdate().set("$sep_survivor", true);

                    break;
                }
            }
        }
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (this.currentStage == Stage.FIND) {
            Constellation constellation = this.entity.getConstellation();
            SectorEntityToken entity = null;
            if (constellation != null && map != null) {
                entity = map.getConstellationLabelEntity(constellation);
            }
            if (entity == null) entity = this.entity;
            return entity;
        }
        return super.getMapLocation(map);
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();

        ArrowData arrow = new ArrowData(Global.getSector().getPlayerFleet(), getMapLocation(map));
        arrow.width = 14f;
        result.add(arrow);

        return result;
    }

    public enum Stage {
        FIND,
        RETURN,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }

    public enum PersonPostType {
        OFFICER,
        ADMINISTRATOR,
        CIVILIAN,
        RANDOM
    }

    public enum EntityType {
        WRECK,
        FLEET,
        PLANET,
        MARKET
    }

    public enum ScenarioType {
        KIDNAPPING,
        STRANDED
    }
}
