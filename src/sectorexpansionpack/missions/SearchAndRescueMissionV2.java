package sectorexpansionpack.missions;

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
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.CryopodOfficerGen;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.MissionScenarioSpec;
import sectorexpansionpack.Utils;

import java.util.*;

public class SearchAndRescueMissionV2 extends HubMissionWithBarEvent {
    public static final float MISSION_DURATION = 120f;
    public static Logger log = Global.getLogger(SearchAndRescueMissionV2.class);
    protected MissionScenarioSpec scenario;
    protected PersonPostType survivorPostType;
    protected PersonAPI survivor;
    protected boolean survivorAlive = true;
    protected ScenarioType scenarioType;
    protected SectorEntityToken entity;
    protected float ransomAmount;

    public SearchAndRescueMissionV2() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        this.scenario = Utils.pickMissionScenario(getMissionId(), getGenRandom());

        if (barEvent) {
            if (rollProbability(0.5f)) { // TODO: Make this a constant (chance for contact to be military)
                List<String> posts = new ArrayList<>();
                posts.add(Ranks.POST_AGENT);
                if (Misc.isMilitary(createdAt)) {
                    posts.add(Ranks.POST_BASE_COMMANDER);
                }
                if (Misc.hasOrbitalStation(createdAt)) {
                    posts.add(Ranks.POST_STATION_COMMANDER);
                }
                setGiverRank(pickOne(Ranks.GROUND_CAPTAIN, Ranks.GROUND_COLONEL, Ranks.GROUND_MAJOR,
                        Ranks.SPACE_COMMANDER, Ranks.SPACE_CAPTAIN, Ranks.SPACE_ADMIRAL));
                setGiverPost(pickOne(posts));
                setGiverImportance(pickHighImportance());
                setGiverTags(Tags.CONTACT_MILITARY);
            } else {
                setGiverRank(Ranks.CITIZEN);
                String post = pickOne(Ranks.POST_TRADER, Ranks.POST_COMMODITIES_AGENT, Ranks.POST_PORTMASTER,
                        Ranks.POST_MERCHANT, Ranks.POST_INVESTOR, Ranks.POST_EXECUTIVE,
                        Ranks.POST_SENIOR_EXECUTIVE);
                setGiverPost(post);
                if (post.equals(Ranks.POST_SENIOR_EXECUTIVE)) {
                    setGiverImportance(pickHighImportance());
                } else {
                    setGiverImportance(pickImportance());
                }
                setGiverTags(Tags.CONTACT_TRADE);
            }
            findOrCreateGiver(createdAt, true, false);
        }

        if (!setPersonMissionRef(getPerson(), "$sep_sarV2_ref")) {
            log.info("Failed to find or create contact");
            return false;
        }

        if (barEvent) {
            setGiverIsPotentialContactOnSuccess();
        }

        this.survivorPostType = pickSurvivorPostType();
        this.survivor = createSurvivor();
        if (this.survivor == null) {
            log.info("Failed to create survivor");
            return false;
        }

        this.entity = pickSurvivorEntity();
        if (!setEntityMissionRef(this.entity, "$sep_sarV2_ref")) {
            log.info("Failed to find entity containing survivor");
            return false;
        }

        makeImportant(this.entity, "$sep_sarV2_survivorEntity", Stage.FIND);
        makeImportant(getPerson(), "$sep_sarV2_contactPerson", Stage.RETURN);

        setStartingStage(Stage.FIND);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        connectWithMemoryFlag(Stage.FIND, Stage.RETURN, this.entity, "$sep_sarV2_returnToContact");
        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_sarV2_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.RETURN, Stage.FAILED_DECIV, createdAt);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        if (this.scenario.getDuration() > -1) {
            setTimeLimit(FleetEscortMission.Stage.FAILED, this.scenario.getDuration(), null);
        } else {
            setTimeLimit(FleetEscortMission.Stage.FAILED, MISSION_DURATION, null);
        }

        if (this.scenario.getMinCreditReward() > -1) {
            if (this.scenario.getMaxCreditReward() < this.scenario.getMinCreditReward()) {
                setCreditReward(this.scenario.getMinCreditReward());
            } else {
                setCreditReward(this.scenario.getMinCreditReward(), this.scenario.getMaxCreditReward());
            }
        } else {
            setCreditReward(CreditReward.HIGH);
        }

        this.ransomAmount = getCreditsReward() * (0.4f + 0.2f * getGenRandom().nextFloat());

        // TODO: Add fleet complications

        return true;
    }

    public PersonPostType pickSurvivorPostType() {
        WeightedRandomPicker<PersonPostType> picker = new WeightedRandomPicker<>(getGenRandom());
        picker.add(PersonPostType.OFFICER, 5f);
        picker.add(PersonPostType.ADMINISTRATOR, 5f);
        picker.add(PersonPostType.CONTACT, 5f);
        picker.add(PersonPostType.CIVILIAN, 10f);
        return picker.pick();
    }

    public PersonAPI createSurvivor() {
        PersonAPI person = getPerson().getFaction().createRandomPerson(getGenRandom());
        if (this.survivorPostType == PersonPostType.OFFICER) {
            WeightedRandomPicker<Integer> levelPicker = new WeightedRandomPicker<>(getGenRandom());
            levelPicker.add(1, 10f);
            levelPicker.add(1, 10f);
            levelPicker.add(1, 10f);
            levelPicker.add(2, 7f);
            levelPicker.add(2, 7f);
            levelPicker.add(5, 3f);
            levelPicker.add(7, 1f);
            int level = levelPicker.pick();
            boolean isExceptional = level == 7;
            boolean isNormal = level == 5;
            if (isExceptional) {
                CryopodOfficerGen.CryopodOfficerTemplate template = CryopodOfficerGen.TEMPLATES_EXCEPTIONAL.pick(getGenRandom());
                if (template != null) {
                    person = template.create(getPerson().getFaction(), getGenRandom());
                }
            } else if (isNormal) {
                CryopodOfficerGen.CryopodOfficerTemplate template = CryopodOfficerGen.TEMPLATES_NORMAL.pick(getGenRandom());
                if (template != null) {
                    person = template.create(getPerson().getFaction(), getGenRandom());
                }
            } else {
                person = OfficerManagerEvent.createOfficer(getPerson().getFaction(), level, OfficerManagerEvent.SkillPickPreference.ANY, getGenRandom());
                if (rollProbability(0.05f)) { // TODO: Make this a constant (chance for officer to become exceptional)
                    person.getMemoryWithoutUpdate().set(MemFlags.OFFICER_MAX_LEVEL, 7);
                    person.getMemoryWithoutUpdate().set(MemFlags.OFFICER_MAX_ELITE_SKILLS, 5);
                }
                if (rollProbability(0.5f)) { // TODO: Make this a constant (chance for officer to be mentored)
                    person.getMemoryWithoutUpdate().set("$mentored", true);
                }
            }
        } else if (this.survivorPostType == PersonPostType.ADMINISTRATOR) {
            WeightedRandomPicker<Integer> tierPicker = new WeightedRandomPicker<>(getGenRandom());
            tierPicker.add(1);
            tierPicker.add(2);
            tierPicker.add(3);
            person = OfficerManagerEvent.createAdmin(getPerson().getFaction(), tierPicker.pick(), getGenRandom());
        } else if (this.survivorPostType == PersonPostType.CONTACT) {
            person.setImportance(pickHighImportance());
            if (rollProbability(0.25f)) { // TODO: Make this a constant (chance for contact to be military)
                if (person.getImportance().ordinal() > 2) {
                    person.setRankId(pickOne(Ranks.SPACE_COMMANDER, Ranks.SPACE_CAPTAIN, Ranks.SPACE_ADMIRAL));
                    person.setPostId(pickOne(Ranks.POST_AGENT, Ranks.POST_FLEET_COMMANDER, Ranks.POST_PATROL_COMMANDER));
                } else {
                    person.setRankId(pickOne(Ranks.SPACE_LIEUTENANT, Ranks.POST_FLEET_COMMANDER, Ranks.SPACE_CAPTAIN));
                    person.setPostId(pickOne(Ranks.POST_AGENT, Ranks.POST_MERCENARY, Ranks.POST_SPACER));
                }
                person.addTag(Tags.CONTACT_MILITARY);
            } else { // Contact will be trader
                if (person.getImportance().ordinal() > 2) {
                    person.setRankId(pickOne(Ranks.CITIZEN, Ranks.ARISTOCRAT, Ranks.SPECIAL_AGENT));
                    person.setPostId(pickOne(Ranks.POST_EXECUTIVE, Ranks.POST_SENIOR_EXECUTIVE));
                } else {
                    person.setRankId(pickOne(Ranks.CITIZEN, Ranks.ARISTOCRAT, Ranks.SPECIAL_AGENT));
                    person.setPostId(pickOne(Ranks.POST_TRADER, Ranks.POST_MERCHANT, Ranks.POST_COMMODITIES_AGENT));
                }
                person.addTag(Tags.CONTACT_TRADE);
            }
        }

        return person;
    }

    public SectorEntityToken pickSurvivorEntity() {
        SectorEntityToken entity = null;

        preferSystemInInnerSector();
        preferSystemInDirectionOfOtherMissions();

        this.scenarioType = ScenarioType.STRANDED_IN_WRECK;
        if (ScenarioType.contains(this.scenario.getData1())) {
            this.scenarioType = ScenarioType.valueOf(this.scenario.getData1());
        }
        switch (this.scenarioType) {
            case STRANDED_IN_WRECK:
                requireEntityTags(ReqMode.ALL, Tags.SALVAGEABLE);
                requireEntityType(Entities.WRECK);
                entity = pickEntity();
                break;
            case CAPTURED_IN_FLEET:
                requireSystemHasSafeStars();
                requireSystemTags(ReqMode.NOT_ALL, Tags.THEME_UNSAFE);
                preferPlanetNotFullySurveyed();
                preferPlanetUnpopulated();
                preferPlanetWithRuins();
                PlanetAPI planet = pickPlanet();

                beginStageTrigger(SearchAndRescueMission.Stage.FIND);
                triggerCreateFleet(
                        FleetSize.LARGE,
                        FleetQuality.DEFAULT,
                        Factions.PIRATES,
                        FleetTypes.PATROL_LARGE,
                        planet.getStarSystem());

                // TODO: Scale fleet based on player fleet

                triggerPickLocationAroundEntity(planet, 1000f);
                triggerSpawnFleetAtPickedLocation();

                triggerMakeLowRepImpact();
                triggerMakeFleetIgnoreOtherFleets();
                triggerMakeFleetIgnoredByOtherFleets();
                triggerMakeFleetNotIgnorePlayer();
                triggerOrderFleetPatrol(planet);
                triggerFleetAddDefeatTrigger("SEPSARV2FleetDefeated");
                triggerFleetSetName("Kidnapper's Fleet");

                endTrigger();

                List<CampaignFleetAPI> fleets = runStageTriggersReturnFleets(SearchAndRescueMission.Stage.FIND);
                entity = fleets.get(0);
                break;
            case CAPTURED_IN_PLANET:
            case STRANDED_IN_PLANET:
                requireSystemInterestingAndNotCore();
                requirePlanetNotGasGiant();
                requirePlanetNotStar();
                preferPlanetNotFullySurveyed();
                preferPlanetUnpopulated();
                entity = pickPlanet();
                break;
            case CAPTURED_IN_MARKET:
                requireMarketFaction(Factions.PIRATES);
                requireMarketNotHidden();
                requireMarketStabilityAtLeast(7);
                MarketAPI market = pickMarket();
                if (market != null) {
                    entity = market.getPrimaryEntity();
                }
            default:
                break;
        }

        return entity;
    }

    @Override
    public String getBaseName() {
        return "Search and Rescue";
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_sarV2_scenarioType", this.scenarioType);
        set("$sep_sarV2_survivorPostType", this.survivorPostType);
        set("$sep_sarV2_survivorAlive", this.survivorAlive);
        set("$sep_sarV2_creditRansom", Misc.getDGSCredits(this.ransomAmount));
        set("$sep_sarV2_raidDangerLevel", MarketCMD.RaidDangerLevel.MEDIUM); // TODO: Randomize or customize this value
        set("$sep_sarV2_marineAmount", 300f); // TODO: Randomize or customize this value
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        String prefix = "";
        if (this.subjectName != null && !this.subjectName.isEmpty() && !this.subjectName.isBlank()) {
            prefix = "the ";
        }
        if (this.currentStage == Stage.FIND) {
            String loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
            info.addPara("Search for " + prefix + " %s in " + loc, 3f, tc, h, this.subjectName);
            return true;
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Return to " + getPerson().getMarket().getName() + " in the " +
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                    + " and talk to " + getPerson().getNameString() + ".", 3f, tc, h);
            return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color tc = Misc.getTextColor();
        String prefix = "";
        if (this.subjectName != null && !this.subjectName.isEmpty() && !this.subjectName.isBlank()) {
            prefix = "the";
        }
        if (this.currentStage == Stage.FIND) {
            String loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
            info.addPara("Search for " + prefix + " %s in " + loc, 3f, tc, h, this.subjectName);
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Return with " + prefix + "%s to " + getPerson().getMarket().getName() + " in the " +
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                    + " and talk to " + getPerson().getNameString() + ".", 3f, tc, h);
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
                int bonusCreditReward = (int) (getCreditsReward() * 0.2f);
                setCreditReward(getCreditsReward() + bonusCreditReward);
                return true;
            }
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
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
        CIVILIAN
    }

    public enum ScenarioType {
        STRANDED_IN_WRECK,
        STRANDED_IN_PLANET,
        CAPTURED_IN_FLEET,
        CAPTURED_IN_PLANET,
        CAPTURED_IN_MARKET;

        public static boolean contains(String s) {
            for (ScenarioType type : values()) {
                if (Objects.equals(type.name(), s)) {
                    return true;
                }
            }
            return false;
        }
    }
}
