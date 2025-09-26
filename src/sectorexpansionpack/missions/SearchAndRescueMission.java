package sectorexpansionpack.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sectorexpansionpack.ModPlugin;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SearchAndRescueMission extends HubMissionWithBarEvent {
    public static Logger log = Global.getLogger(SearchAndRescueMission.class);
    protected JSONObject scenarioData;
    protected PersonPostType survivorPostType;
    protected PersonAPI survivor;
    protected boolean survivorAlive = true;
    protected EntityType entityType;
    protected SectorEntityToken entity;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        try {
            this.genRandom = new Random(Long.parseLong(Global.getSector().getSeedString().replaceAll("\\D", "")));
            this.scenarioData = ModPlugin.getRandomMissionScenario(getMissionId(), this.genRandom, barEvent);

            if (this.scenarioData == null) {
                log.info("Failed to choose a mission scenario");
                return false;
            }

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

            // TODO: Make survivor modifiable using the scenario settings
            this.survivorPostType = PersonPostType.valueOf((String) getScenarioData("survivorType"));
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

            this.entityType = EntityType.valueOf((String) getScenarioData("entityType"));
            switch (this.entityType) {
                case WRECK:
                    requireEntityTags(ReqMode.ALL, Tags.SALVAGEABLE);
                    requireEntityType(Entities.WRECK);
                    this.entity = pickEntity();
                    break;
                case FLEET:
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
                    break;
                case PLANET_RAID:
                case PLANET:
                    requireSystemInterestingAndNotCore();
                    requirePlanetNotGasGiant();
                    requirePlanetNotStar();
                    preferPlanetNotFullySurveyed();
                    preferPlanetUnpopulated();
                    this.entity = pickPlanet();
                    break;
                case MARKET:
                    requireMarketNotHidden();
                    preferMarketFactionHostileTo(getPerson().getFaction().getId());
                    this.entity = pickMarket().getPrimaryEntity();
                    break;
                default:
                    this.entity = null;
                    break;
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

            setTimeLimit(Stage.FAILED, ((Number) getScenarioData("missionDuration")).floatValue(), null, Stage.RETURN);

            setCreditReward(getScenarioCreditReward(false));

            JSONArray complications = (JSONArray) getScenarioData("complications");
            for (int i = 0; i < complications.length(); i++) {
                JSONObject complication = complications.getJSONObject(i);
                if (complication.has("probability") && rollProbability((float) complication.getDouble("probability"))) {
                    continue;
                }

                beginWithinHyperspaceRangeTrigger(
                        this.entity.getStarSystem(),
                        2000f,
                        false,
                        Stage.valueOf(complication.getString("stageTrigger")));

                triggerCreateFleet(
                        FleetSize.valueOf(complication.getString("fleetSize")),
                        FleetQuality.valueOf(complication.getString("fleetQuality")),
                        complication.getString("factionId"),
                        complication.getString("fleetTypes"),
                        this.entity.getStarSystem());

                if (complication.has("autoAdjust")) {
                    switch (complication.getString("autoAdjust")) {
                        case "MODERATE" -> triggerAutoAdjustFleetStrengthModerate();
                        case "MAJOR" -> triggerAutoAdjustFleetStrengthMajor();
                        case "EXTREME" -> triggerAutoAdjustFleetStrengthExtreme();
                    }
                }

                triggerPickLocationAroundEntity(this.entity, 1000f);
                triggerSpawnFleetAtPickedLocation();

                if (complication.has("lowRepImpact") && complication.getBoolean("lowRepImpact")) {
                    triggerMakeLowRepImpact();
                }
                if (complication.has("hostileAndAggressive") && complication.getBoolean("hostileAndAggressive")) {
                    triggerMakeHostileAndAggressive();
                }
                if (complication.has("ignoreOtherFleets") && complication.getBoolean("ignoreOtherFleets")) {
                    triggerMakeFleetIgnoreOtherFleets();
                }
                if (complication.has("ignoredByOtherFleets") && complication.getBoolean("ignoredByOtherFleets")) {
                    triggerMakeFleetIgnoredByOtherFleets();
                }
                if (complication.has("notIgnorePlayer") && complication.getBoolean("notIgnorePlayer")) {
                    triggerMakeFleetNotIgnorePlayer();
                }
                if (complication.has("noFactionInName") && complication.getBoolean("noFactionInName")) {
                    triggerFleetSetNoFactionInName();
                }
                if (complication.has("fleetName") && !complication.getString("fleetName").isEmpty()) {
                    triggerFleetSetName(complication.getString("fleetName"));
                }

                triggerOrderFleetPatrol(this.entity);
                endTrigger();
            }

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
        set("$sep_sar_survivorAlive", this.survivorAlive);
        set("$sep_sar_survivorPostType", this.survivorPostType);
        set("$sep_sar_entityType", this.entityType);
        set("$sep_sar_creditReward", Misc.getDGSCredits(getCreditsReward()));
        set("$sep_sar_creditRansom", Misc.getDGSCredits(getCreditsReward() * 0.75f));
        set("$sep_sar_danger", MarketCMD.RaidDangerLevel.MEDIUM);
        set("$sep_sar_possibleLoc", BreadcrumbSpecial.getLocationDescription(this.entity, false));

        set("$sep_sar_survivorFullName", this.survivor.getNameString());
        set("$sep_sar_survivorFirstName", this.survivor.getName().getFirst());
        set("$sep_sar_survivorLastName", this.survivor.getName().getLast());
        set("$sep_sar_survivorHeOrShe", this.survivor.getHeOrShe());
        set("$sep_sar_survivorHisOrHer", this.survivor.getHisOrHer());
        set("$sep_sar_survivorHimOrHer", this.survivor.getHimOrHer());
        set("$sep_sar_survivorManOrWoman", this.survivor.getManOrWoman());

        if (this.currentStage == null) {
            if (!isBarEvent()) {
                set("$sep_sar_contactMissionBlurb", getDialogText("contactMissionBlurb"));
                set("$sep_sar_contactMissionOption", getDialogText("contactMissionOption"));
                set("$sep_sar_contactMissionOfferText", getDialogText("contactMissionOfferText"));
            } else {
                set("$sep_sar_barMissionBlurb", getDialogText("barMissionBlurb"));
                set("$sep_sar_barMissionOption", getDialogText("barMissionOption"));
                set("$sep_sar_barMissionOfferText", getDialogText("barMissionOfferText"));
            }
        }

        if (this.currentStage == Stage.FIND) {
            set("$sep_sar_entityDialogText", getDialogText("entityDialogText"));
            if (this.entityType == EntityType.FLEET) {
                set("$sep_sar_entityPayRansomText", getDialogText("entityPayRansomText"));
                set("$sep_sar_entityFightText", getDialogText("entityFightText"));
                set("$sep_sar_entityDeclineText", getDialogText("entityDeclineText"));
                set("$sep_sar_entityDefeatedText", getDialogText("entityDefeatedText"));
            } else if (this.entityType == EntityType.PLANET_RAID) {
                set("$sep_sar_entityRaidFinishedText", getDialogText("entityRaidFinishedText"));
            }
            set("$sep_sar_survivorAliveText", getDialogText("survivorAliveText"));
            set("$sep_sar_survivorDeadText", getDialogText("survivorDeadText"));
        }

        if (this.currentStage == Stage.RETURN) {
            set("$sep_sar_returnSurvivorAliveText", getDialogText("returnSurvivorAliveText"));
            set("$sep_sar_survivorDialogText", getDialogText("survivorDialogText"));
            set("$sep_sar_returnSurvivorDeadText", getDialogText("returnSurvivorDeadText"));
        }
    }

    public String getDialogText(String id) {
        String result;
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

        switch (action) {
            case "checkSurvivorStatus" -> {
                if (this.timeLimit.days - this.elapsed < 60f) {
                    this.survivorAlive = false;
                }
                updateInteractionData(dialog, memoryMap);
                return this.survivorAlive;
            }
            case "showSurvivorVisual" -> {
                dialog.getVisualPanel().showPersonInfo(this.survivor);
                return true;
            }
            case "addBonusCreditReward" -> {
                int creditRewardBonus;
                try {
                    creditRewardBonus = getScenarioCreditReward(true);
                } catch (JSONException e) {
                    log.error(e);
                    creditRewardBonus = getCreditRewardValue(CreditReward.LOW.min, CreditReward.LOW.max);
                }
                setCreditReward(getCreditsReward() + creditRewardBonus);
                return true;
            }
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
            if (entity == null) {
                entity = this.entity;
            }
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

    public Object getScenarioData(String id) throws JSONException {
        Object result;
        if (this.scenarioData.has(id)) {
            result = this.scenarioData.get(id);
        } else {
            result = ModPlugin.getMissionScenarioDefaults(getMissionId()).get(id);
        }
        return result;
    }

    public int getScenarioCreditReward(boolean isBonus) throws JSONException {
        Object data = getScenarioData(!isBonus ? "creditReward" : "creditRewardBonus");
        int result = 0;
        if (data instanceof String value) {
            CreditReward creditRewardType = CreditReward.valueOf(value);
            result = getCreditRewardValue(creditRewardType.min, creditRewardType.max);
        } else if (data instanceof Integer value) {
            result = value;
        }
        return result;
    }

    public int getCreditRewardValue(int min, int max) {
        int reward = min + this.genRandom.nextInt(max - min + 1);
        reward = reward / 1000 * 1000;
        if (reward > 100000) {
            reward = reward / 10000 * 10000;
        }
        return reward;
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
        CONTACT,
        CIVILIAN,
        RANDOM
    }

    public enum EntityType {
        WRECK,
        FLEET,
        PLANET,
        MARKET,
        PLANET_RAID
    }
}
