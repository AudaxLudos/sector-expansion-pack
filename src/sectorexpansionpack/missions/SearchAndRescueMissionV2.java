package sectorexpansionpack.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.CryopodOfficerGen;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.hub.SEPHubMissionWithScenario;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SearchAndRescueMissionV2 extends SEPHubMissionWithScenario {
    public static final float MISSION_DURATION = 120f;
    public static float OFFICER_EXCEPTIONAL_CHANCE = 0.05f;
    public static float OFFICER_MENTORED_CHANCE = 0.5f;
    public static float CONTACT_MILITARY_CHANCE = 0.25f;
    public static float BAR_MILITARY_CHANCE = 0.4f;
    public static Logger log = Global.getLogger(SearchAndRescueMissionV2.class);
    protected PersonPostType survivorPostType;
    protected PersonAPI survivor;
    protected boolean survivorAlive = true;
    protected SectorEntityToken hideout;
    protected SectorEntityToken entity;
    protected float ransomAmount;
    protected int marineAmount;
    protected MarketCMD.RaidDangerLevel raidDangerLevel;
    protected String subjectName = null;

    public SearchAndRescueMissionV2() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!getScenario()) {
            log.info("Failed to pick a scenario");
            return false;
        }
        if (!getScenarioType(ScenarioType.class)) {
            log.info("Failed to find scenario type");
            return false;
        }

        if (barEvent) {
            if (rollProbability(BAR_MILITARY_CHANCE)) {
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
        this.subjectName = this.survivor.getNameString();

        this.hideout = pickHideoutForFleet();
        if (this.hideout == null && this.scenarioType == ScenarioType.CAPTURED_IN_FLEET) {
            log.info("Failed to find hideout for fleet");
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

        if (Utils.isInEnum(this.scenario.getCreditReward(), CreditReward.class)) {
            setCreditReward(CreditReward.valueOf(this.scenario.getCreditReward()));
        } else {
            setCreditReward(CreditReward.HIGH);
        }

        int defenderStr = Math.max(50, Math.round(getCreditsReward() / 250f / 100f) * 100);
        if (defenderStr == 50) {
            this.raidDangerLevel = MarketCMD.RaidDangerLevel.MINIMAL;
        } else if (defenderStr <= 100) {
            this.raidDangerLevel = MarketCMD.RaidDangerLevel.LOW;
        } else if (defenderStr <= 300) {
            this.raidDangerLevel = MarketCMD.RaidDangerLevel.MEDIUM;
        } else if (defenderStr <= 500) {
            this.raidDangerLevel = MarketCMD.RaidDangerLevel.HIGH;
        } else {
            this.raidDangerLevel = MarketCMD.RaidDangerLevel.EXTREME;
        }
        this.marineAmount = getMarinesRequiredForCustomDefenderStrength(defenderStr, this.raidDangerLevel);
        int bonus = getRewardBonusForMarines(this.marineAmount);
        setCreditReward(getCreditsReward() + bonus);
        this.ransomAmount = Math.round(getCreditsReward() * (0.8f + 0.4f * getGenRandom().nextFloat()) / 1000f) * 1000f;

        setScenarioComplications(Stage.class, log);

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
                if (rollProbability(OFFICER_EXCEPTIONAL_CHANCE)) {
                    person.getMemoryWithoutUpdate().set(MemFlags.OFFICER_MAX_LEVEL, 7);
                    person.getMemoryWithoutUpdate().set(MemFlags.OFFICER_MAX_ELITE_SKILLS, 5);
                }
                if (rollProbability(OFFICER_MENTORED_CHANCE)) {
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
            if (rollProbability(CONTACT_MILITARY_CHANCE)) {
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

    public SectorEntityToken pickHideoutForFleet() {
        SectorEntityToken hideout;
        if (this.scenarioType == ScenarioType.CAPTURED_IN_FLEET) {
            requireSystemHasSafeStars();
            preferPlanetNotFullySurveyed();
            preferPlanetUnpopulated();
            preferPlanetWithRuins();
            hideout = pickPlanet();
        } else {
            hideout = null;
        }

        return hideout;
    }

    public SectorEntityToken pickSurvivorEntity() {
        SectorEntityToken entity = null;

        preferSystemInInnerSector();
        preferSystemInDirectionOfOtherMissions();

        if (this.scenarioType == ScenarioType.STRANDED_IN_WRECK) {
            requireEntityTags(ReqMode.ALL, Tags.SALVAGEABLE);
            requireEntityType(Entities.WRECK);
            entity = pickEntity();
        } else if (this.scenarioType == ScenarioType.CAPTURED_IN_FLEET) {
            // TODO: Add a way to customize fleet
            beginStageTrigger(Stage.FIND);
            triggerCreateStandardFleet(10, Factions.PIRATES, this.hideout.getLocationInHyperspace());
            triggerMakeLowRepImpact();
            triggerMakeFleetIgnoreOtherFleets();
            triggerMakeFleetIgnoredByOtherFleets();
            triggerMakeLowRepImpact();
            triggerOrderFleetPatrol(this.hideout);
            triggerFleetAddDefeatTrigger("SEPSARV2FleetDefeated");
            triggerFleetSetNoFactionInName();
            triggerFleetSetName("Terrorist Group");
            endTrigger();
            List<CampaignFleetAPI> fleets = runStageTriggersReturnFleets(Stage.FIND);
            if (!fleets.isEmpty()) {
                entity = fleets.get(0);
            }
        } else if (this.scenarioType == ScenarioType.STRANDED_IN_PLANET || this.scenarioType == ScenarioType.CAPTURED_IN_PLANET) {
            requireSystemInterestingAndNotCore();
            requirePlanetNotGasGiant();
            requirePlanetNotStar();
            preferPlanetNotFullySurveyed();
            preferPlanetUnpopulated();
            entity = pickPlanet();
        } else if (this.scenarioType == ScenarioType.CAPTURED_IN_MARKET) {
            requireMarketFaction(Factions.PIRATES);
            requireMarketNotHidden();
            requireMarketStabilityAtLeast(7);
            MarketAPI market = pickMarket();
            if (market != null) {
                entity = market.getPrimaryEntity();
            }
        }

        return entity;
    }

    @Override
    public SectorEntityToken getGotoEntity(Object stage) {
        if (stage == Stage.FIND) {
            return this.entity;
        }
        return getPerson().getMarket().getPrimaryEntity();
    }

    @Override
    public String getBaseName() {
        return "Search and Rescue";
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_sarV2_scenarioId", this.scenario.getScenarioId());
        set("$sep_sarV2_scenarioType", this.scenarioType);
        set("$sep_sarV2_survivorPostType", this.survivorPostType);
        set("$sep_sarV2_survivorAlive", this.survivorAlive);
        set("$sep_sarV2_creditReward", Misc.getDGSCredits(getCreditsReward()));
        set("$sep_sarV2_creditRansom", Misc.getDGSCredits(this.ransomAmount));
        set("$sep_sarV2_raidDangerLevel", this.raidDangerLevel);
        set("$sep_sarV2_marineAmount", Misc.getWithDGS(this.marineAmount));
        if (this.hideout != null) {
            set("$sep_sarV2_possibleLoc", BreadcrumbSpecial.getLocationDescription(this.hideout, false));
        } else {
            set("$sep_sarV2_possibleLoc", BreadcrumbSpecial.getLocationDescription(this.entity, false));
        }
        set("$sep_sarV2_survivorFullName", this.survivor.getNameString());
        set("$sep_sarV2_survivorFirstName", this.survivor.getName().getFirst());
        set("$sep_sarV2_survivorLastName", this.survivor.getName().getLast());
        set("$sep_sarV2_survivorHeOrShe", this.survivor.getHeOrShe());
        set("$sep_sarV2_SurvivorHeOrShe", Misc.ucFirst(this.survivor.getHeOrShe()));
        set("$sep_sarV2_survivorHisOrHer", this.survivor.getHisOrHer());
        set("$sep_sarV2_survivorHimOrHer", this.survivor.getHimOrHer());
        set("$sep_sarV2_survivorManOrWoman", this.survivor.getManOrWoman());
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (this.entity instanceof CampaignFleetAPI fleet) {
            this.hideout.getContainingLocation().addEntity(this.entity);
            fleet.setLocation(this.hideout.getLocation().x, this.hideout.getLocation().y);
            fleet.setFacing(getGenRandom().nextFloat() * 360f);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (this.currentStage == Stage.FIND) {
            String loc;
            if (this.hideout != null) {
                loc = BreadcrumbSpecial.getLocationDescription(this.hideout, false);
            } else {
                loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
            }
            info.addPara("Search for %s in " + loc, 3f, tc, h, this.subjectName);
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
        if (this.currentStage == Stage.FIND) {
            String loc;
            if (this.hideout != null) {
                loc = BreadcrumbSpecial.getLocationDescription(this.hideout, false);
            } else {
                loc = BreadcrumbSpecial.getLocationDescription(this.entity, false);
            }
            info.addPara("Search for %s in " + loc, 10f, tc, h, this.subjectName);
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Bring %s to " + getPerson().getMarket().getName() + " in the " +
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                    + " and talk to " + getPerson().getNameString() + ".", 10f, tc, h, this.subjectName);
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
            case "removeDefeatTrigger" -> {
                if (dialog.getInteractionTarget() instanceof CampaignFleetAPI fleet) {
                    Misc.removeDefeatTrigger(fleet, "SEPSARV2FleetDefeated");
                }
                return true;
            }
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (!this.survivorAlive || this.survivorPostType == PersonPostType.CIVILIAN) {
            return;
        }

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
                } else if (this.survivorPostType == PersonPostType.CONTACT) {
                    ContactIntel.addPotentialContact(this.survivor, getPerson().getMarket(), dialog.getTextPanel());
                }

                officer.person.getMemoryWithoutUpdate().set("$sep_survivor", true);
                break;
            }
        }
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        if (this.entity instanceof CampaignFleetAPI fleet) {
            fleet.clearAssignments();
            if (this.hideout != null) {
                fleet.getAI().addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, this.hideout, 1000000f, null);
            } else {
                fleet.despawn();
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

    public void triggerCreateStandardFleet(int difficulty, String factionId, Vector2f locInHyper) {
        FleetSize size;
        FleetQuality quality;
        String type;
        OfficerQuality oQuality;
        OfficerNum oNum;

        if (difficulty <= 0) {
            size = FleetSize.TINY;
            quality = FleetQuality.VERY_LOW;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 1) {
            size = FleetSize.VERY_SMALL;
            quality = FleetQuality.VERY_LOW;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FC_ONLY;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 2) {
            size = FleetSize.SMALL;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.LOWER;
            oNum = OfficerNum.FEWER;
            type = FleetTypes.PATROL_SMALL;
        } else if (difficulty == 3) {
            size = FleetSize.SMALL;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 4) {
            size = FleetSize.MEDIUM;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_MEDIUM;
        } else if (difficulty == 5) {
            size = FleetSize.LARGE;
            quality = FleetQuality.DEFAULT;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.DEFAULT;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 6) {
            size = FleetSize.LARGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 7) {
            size = FleetSize.LARGER;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 8) {
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.DEFAULT;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else if (difficulty == 9) {
            size = FleetSize.VERY_LARGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            type = FleetTypes.PATROL_LARGE;
        } else { // difficulty >= 10
            size = FleetSize.HUGE;
            quality = FleetQuality.HIGHER;
            oQuality = OfficerQuality.HIGHER;
            oNum = OfficerNum.MORE;
            // oNum = OfficerNum.ALL_SHIPS;
            type = FleetTypes.PATROL_LARGE;
        }

        triggerCreateFleet(size, quality, factionId, type, locInHyper);
        triggerSetFleetOfficers(oNum, oQuality);
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
        CAPTURED_IN_MARKET
    }
}
