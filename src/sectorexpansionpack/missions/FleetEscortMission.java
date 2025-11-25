package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionFleetAutoDespawn;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionTrigger;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.skills.OfficerTraining;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.MissionScenarioSpec;
import sectorexpansionpack.ModPlugin;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.hub.MissionFleetFollowPlayerIfNearby;

import java.awt.*;
import java.util.*;
import java.util.List;

// TODO: Remove fleet loot when passing WAIT stage
public class FleetEscortMission extends HubMissionWithBarEvent {
    public static final float MISSION_DURATION = 120f;
    public static float BAR_MILITARY_CHANCE = 0.4f;
    public static Logger log = Global.getLogger(FleetEscortMission.class);
    protected MissionScenarioSpec scenario;
    protected ScenarioType scenarioType;
    protected boolean fleetSpawned = false; // Might not be needed
    protected CampaignFleetAPI fleet;
    protected SectorEntityToken gotoEntity;
    protected String itemId;

    public FleetEscortMission() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    public static void makeFleetInterceptOther(CampaignFleetAPI fleet, SectorEntityToken other, float interceptDays) {
        if (fleet.getAI() == null) {
            fleet.setAI(Global.getFactory().createFleetAI(fleet));
            fleet.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        }

        fleet.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET, new Vector2f(other.getLocation()), interceptDays);

        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().setTarget(other);
        }

        fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, other, interceptDays, null);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        this.scenario = Utils.pickMissionScenario(getMissionId(), getGenRandom());
        if (ScenarioType.contains(this.scenario.getType())) {
            this.scenarioType = ScenarioType.valueOf(this.scenario.getType());
        } else {
            log.error("Scenario has no type");
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

        if (!setPersonMissionRef(getPerson(), "$sep_fem_ref")) {
            log.info("Failed to find or create contact");
            return false;
        }

        if (barEvent) {
            setGiverIsPotentialContactOnSuccess();
        }

        beginStageTrigger(Stage.GOTO);
        triggerCreateStandardFleet(3, getPerson().getFaction().getId(), createdAt.getLocationInHyperspace());
        switch (this.scenarioType) {
            case COMMODITY_DELIVERY:
                triggerSetFleetCombatFleetPoints(30f);
                triggerFleetSetFreighterData(calculateCombatPoints(getPreviousCreateFleetAction()), 1f, false);
                triggerFleetSetTankerData(calculateCombatPoints(getPreviousCreateFleetAction()), 0.1f, false);
                this.itemId = pickItemId(false);
                triggerAddCommodityFractionDrop(this.itemId, 0.4f + 0.4f * getGenRandom().nextFloat());
                break;
            case DRUG_SMUGGLING:
                triggerSetFleetCombatFleetPoints(30f);
                triggerFleetSetFreighterData(calculateCombatPoints(getPreviousCreateFleetAction()), 1f, false);
                triggerFleetSetTankerData(calculateCombatPoints(getPreviousCreateFleetAction()), 0.1f, false);
                triggerAddCommodityFractionDrop(Commodities.DRUGS, 0.4f + 0.4f * getGenRandom().nextFloat());
                break;
            case REBELLION_SUPPORT:
                triggerSetFleetCombatFleetPoints(30f);
                triggerFleetSetFreighterData(0f, 1f, true);
                triggerFleetSetTankerData(0f, 1f, true);
                triggerAddCommodityFractionDrop(Commodities.MARINES, 0.4f);
                triggerAddCommodityFractionDrop(Commodities.HAND_WEAPONS, 0.1f);
                triggerAddCommodityFractionDrop(Commodities.FUEL, 0.3f);
                break;
            case ARTIFACT_DELIVERY:
                triggerSetFleetCombatFleetPoints(30f);
                triggerFleetSetFreighterData(0f, 0.1f, true);
                triggerFleetSetTankerData(0f, 0.1f, true);
                this.itemId = pickItemId(true);
                triggerAddSpecialItemDrop(this.itemId, null);
                break;
            case VIP_ESCORT:
                triggerSetFleetCombatFleetPoints(30f);
                triggerFleetSetFreighterData(0f, 0.1f, true);
                triggerFleetSetTankerData(0f, 0.1f, true);
                break;
        }
        triggerMakeFleetIgnoreOtherFleets();
        triggerFleetSetName("Special Courier Fleet");
        triggerFleetFollowPlayerWithinRange(2000f, Stage.GOTO, Stage.RETURN);
        triggerFleetNoAutoDespawn();
        endTrigger();
        List<CampaignFleetAPI> fleets = runStageTriggersReturnFleets(Stage.GOTO);
        if (!fleets.isEmpty()) {
            this.fleet = fleets.get(0);
        }
        if (!setEntityMissionRef(this.fleet, "$sep_fem_ref")) {
            log.info("Failed to create fleet to escort");
            return false;
        }

        beginStageTrigger(Stage.WAIT);
        triggerRunScriptAfterDelay(0f, () -> {
            this.fleet.clearAssignments();
            this.fleet.addAssignmentAtStart(FleetAssignment.ORBIT_PASSIVE, this.gotoEntity, 999999f, "Completing objectives at " + this.gotoEntity.getName(), null);
        });
        endTrigger();

        requireMarketFaction(getPerson().getFaction().getId());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        requireMarketLocationNot(createdAt.getContainingLocation());
        MarketAPI market = pickMarket();
        if (market == null) {
            log.info("Failed to find market");
            return false;
        }
        this.gotoEntity = market.getPrimaryEntity();
        if (this.gotoEntity == null) {
            log.info("Failed to find market's entity");
            return false;
        }

        makeImportant(this.gotoEntity, "$sep_fem_gotoEntity", Stage.GOTO);
        makeImportant(this.fleet, "$sep_fem_escortedFleet", Stage.GOTO, Stage.WAIT, Stage.RETURN);
        makeImportant(getPerson(), "$sep_fem_returnPerson", Stage.RETURN);

        setStartingStage(Stage.GOTO);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        connectWithEntityNearbyOther(Stage.GOTO, Stage.WAIT, this.fleet, this.gotoEntity, 1000f, false);
        connectWithDaysElapsed(Stage.WAIT, Stage.RETURN, 7f);
        connectWithEntityNearbyOther(Stage.RETURN, Stage.COMPLETED, this.fleet, getPerson().getMarket().getPrimaryEntity(), 1000f, false);

        setStageOnEntityNotAlive(Stage.FAILED, this.fleet);
        setStageOnFleetWeakened(Stage.FAILED, this.fleet, 0.4f);

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        addNoPenaltyFailureStages(Stage.FAILED_GIVER_HOSTILE);
        setStageOnFactionTurnedHostile(Stage.FAILED_GIVER_HOSTILE, getPerson().getFaction());

        if (this.scenario.getDuration() > -1) {
            setTimeLimit(Stage.FAILED, this.scenario.getDuration(), null);
        } else {
            setTimeLimit(Stage.FAILED, MISSION_DURATION, null);
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

        setRepChanges(0.05f, 0.1f, 0.05f, 0.1f);

        for (String complication : this.scenario.getComplications()) {
            List<String> tags = List.of(complication.split(","));

            if (!Stage.contains(tags.get(0))) {
                log.info("Stage does not exist skipping complication");
                continue;
            }
            if (Global.getSector().getFaction(tags.get(1)) == null) {
                log.info("Faction does not exist skipping complication");
                continue;
            }

            Stage stage = Stage.valueOf(tags.get(0));
            String faction = tags.get(1);
            int difficulty = Integer.parseInt(tags.get(2));
            boolean inHyperspace = tags.contains("inHyperspace");

            beginWithinHyperspaceRangeTrigger(this.gotoEntity, 5f, inHyperspace, stage);
            triggerCreateStandardFleet(difficulty, faction, this.gotoEntity.getLocationInHyperspace());
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
            // Pick spawn location
            if (tags.contains("nearPlayer")) {
                triggerPickLocationAroundPlayer(1000f);
            } else {
                triggerPickLocationAroundEntity(this.gotoEntity, 90f);
            }
            // AI Actions
            if (tags.contains("interceptEscort")) {
                triggerOrderFleetInterceptOther(this.fleet);
            }
            triggerSpawnFleetAtPickedLocation();
            endTrigger();
        }

        return true;
    }

    public float calculateCombatPoints(CreateFleetAction action) {
        return calculateCombatPoints(action.params.factionId, action.fSize, action.fSizeOverride, action.params.doctrineOverride);
    }

    public float calculateCombatPoints(String factionId, FleetSize fSize, Float fSizeOverride, FactionDoctrineAPI doctrineOverride) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        float maxPoints = faction.getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
        float min = fSize.maxFPFraction - (fSize.maxFPFraction - fSize.prev().maxFPFraction) / 2f;
        float max = fSize.maxFPFraction + (fSize.next().maxFPFraction - fSize.maxFPFraction) / 2f;
        float fraction = min + (max - min) * getGenRandom().nextFloat();

        if (fSizeOverride != null) {
            fraction = fSizeOverride * (0.95f + getGenRandom().nextFloat() * 0.1f);
        } else {
            int numShipsDoctrine = 1;
            if (doctrineOverride != null) {
                numShipsDoctrine = doctrineOverride.getNumShips();
            } else {
                numShipsDoctrine = faction.getDoctrine().getNumShips();
            }
            float doctrineMult = FleetFactoryV3.getDoctrineNumShipsMult(numShipsDoctrine);
            fraction *= 0.75f * doctrineMult;
            if (fraction > FleetSize.MAXIMUM.maxFPFraction) {
                fraction = FleetSize.MAXIMUM.maxFPFraction;
            }
        }

        return fraction * maxPoints;
    }

    public String pickItemId(boolean colonyItem) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(getGenRandom());

        if (!colonyItem) {
            for (CommodityOnMarketAPI commodityOnMarket : getPerson().getMarket().getCommoditiesCopy()) {
                picker.add(commodityOnMarket.getCommodity().getId());
            }
        } else {
            for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
                if (!ModPlugin.COLONY_ITEM_WHITELIST.contains(spec.getId())) {
                    continue;
                }
                picker.add(spec.getId());
            }
        }

        return picker.pick();
    }

    public void removeCommodityFraction(CampaignFleetAPI fleet, String commodityId, float fraction) {
        float quantity = fleet.getCargo().getCommodityQuantity(commodityId);
        float toRemove = quantity * fraction;
        if (quantity > 0) {
            fleet.getCargo().removeCommodity(commodityId, toRemove);
        }
    }

    @Override
    public String getBaseName() {
        return "Fleet Escort";
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_fem_scenarioId", this.scenario.getScenarioId());
        set("$sep_fem_mrktNme", this.gotoEntity.getName());
        set("$sep_fem_sysName", this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        set("$sep_fem_duration", Misc.getWithDGS(this.timeLimit.days));
        set("$sep_fem_creditReward", Misc.getDGSCredits(getCreditsReward()));
        switch (this.scenarioType) {
            case COMMODITY_DELIVERY:
                CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(this.itemId);
                if (commoditySpec != null) {
                    set("$sep_fem_itemName", commoditySpec.getName());
                }
                break;
            case ARTIFACT_DELIVERY:
                SpecialItemSpecAPI itemSpec = Global.getSettings().getSpecialItemSpec(this.itemId);
                if (itemSpec != null) {
                    set("$sep_fem_itemName", itemSpec.getName());
                }
                break;
        }
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (this.fleet != null && !this.fleet.isEmpty()) {
            MarketAPI market = getPerson().getMarket();
            SectorEntityToken entity = market.getPrimaryEntity();
            entity.getContainingLocation().addEntity(this.fleet);
            this.fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
            this.fleet.setFacing(getGenRandom().nextFloat() * 360f);
        }
    }

    @Override
    public boolean canAbandonWithoutPenalty() {
        return this.currentStage == Stage.RETURN;
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (this.currentStage == Stage.GOTO) {
            info.addPara("Escort the fleet to %s in the %s.", pad, tc, h, this.gotoEntity.getName(),
                    this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (this.currentStage == Stage.WAIT) {
            info.addPara("Wait for the fleet to complete its objectives in the %s.", pad, tc, h,
                    this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Escort the fleet back to %s in the %s.", pad, tc, h,
                    getGotoEntity().getName(), getGotoEntity().getStarSystem().getNameWithLowercaseTypeShort());
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float oPad = 10f;
        Color tc = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        if (this.currentStage == Stage.GOTO) {
            info.addPara("Escort the fleet to %s in the %s.", oPad, tc, h, this.gotoEntity.getName(),
                    this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (this.currentStage == Stage.WAIT) {
            info.addPara("Wait for the fleet to complete its objectives in the %s.", oPad, tc, h,
                    this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Escort the fleet back to %s in the %s.", oPad, tc, h,
                    getGotoEntity().getName(), getGotoEntity().getStarSystem().getNameWithLowercaseTypeShort());
        }
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();

        ArrowData arrowFleet = new ArrowData(Global.getSector().getPlayerFleet(), this.fleet);
        arrowFleet.width = 14f;
        arrowFleet.color = Misc.getHighlightColor();
        result.add(arrowFleet);

        ArrowData arrowDestination = new ArrowData(Global.getSector().getPlayerFleet(), getGotoEntity());
        arrowDestination.width = 14f;
        result.add(arrowDestination);

        return result;
    }

    public SectorEntityToken getGotoEntity() {
        if (getCurrentStage() == Stage.GOTO) {
            return this.gotoEntity;
        }
        return getPerson().getMarket().getPrimaryEntity();
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

        triggerSEPCreateFleet(size, quality, factionId, type, locInHyper);
        triggerSetFleetOfficers(oNum, oQuality);
    }

    public void triggerSEPCreateFleet(FleetSize size, FleetQuality quality, String factionId, String type, Vector2f locInHyper) {
        triggerCustomAction(new SEPCreateFleetAction(type, locInHyper, size, quality, factionId));
    }

    public void triggerFleetSetFreighterData(float points, float mult, boolean includeCombatPoints) {
        CreateFleetAction cfa = getPreviousCreateFleetAction();
        cfa.freighterMult = mult;
        if (getPreviousCreateFleetAction() instanceof SEPCreateFleetAction cfa1) {
            cfa1.transportIncludeCombatPts = includeCombatPoints;
            cfa1.freighterPts = points;
        }
    }

    public void triggerFleetSetTankerData(float points, float mult, boolean includeCombatPoints) {
        CreateFleetAction cfa = getPreviousCreateFleetAction();
        cfa.tankerMult = mult;
        if (getPreviousCreateFleetAction() instanceof SEPCreateFleetAction cfa1) {
            cfa1.tankerIncludeCombatPts = includeCombatPoints;
            cfa1.tankerPts = points;
        }
    }

    public void triggerFleetFollowPlayerWithinRange(float maxRange, Object... stages) {
        triggerCustomAction(new OrderFleetFollowNearbyPlayerInStage(this, maxRange, stages));
    }

    public void triggerOrderFleetInterceptOther(SectorEntityToken other) {
        triggerCustomAction(new OrderFleetInterceptOtherAction(other));
    }

    public void connectWithOnDaysElapsed(Stage from, Stage to, float days) {
        this.connections.add(new StageConnection(from, to, new DaysElapsedChecker(days, this)));
    }

    public void connectWithEntityNearbyOther(Object from, Object to, SectorEntityToken entity, SectorEntityToken other, float maxRange, boolean checkInHyperspace) {
        this.connections.add(new StageConnection(from, to, new EntityNearbyOtherChecker(entity, other, maxRange, checkInHyperspace)));
    }

    public void setStageOnEntityNearbyOther(Object to, SectorEntityToken entity, SectorEntityToken other, float maxRange, boolean checkInHyperspace) {
        this.connections.add(new StageConnection(null, to, new EntityNearbyOtherChecker(entity, other, maxRange, checkInHyperspace)));
    }

    public void connectWithFactionTurnedHostile(Object from, Object to, FactionAPI faction) {
        this.connections.add(new StageConnection(from, to, new EntityFinderMission.FactionTurnedHostileChecker(faction)));
    }

    public void setStageOnFactionTurnedHostile(Object to, FactionAPI faction) {
        this.connections.add(new StageConnection(null, to, new EntityFinderMission.FactionTurnedHostileChecker(faction)));
    }

    public void setStageOnFleetWeakened(Object to, CampaignFleetAPI fleet, float damageThreshold) {
        this.connections.add(new StageConnection(null, to, new FleetWeakenedChecker(fleet, damageThreshold)));
    }

    public enum Stage {
        GOTO,
        WAIT,
        RETURN,
        COMPLETED,
        FAILED,
        FAILED_DECIV,
        FAILED_GIVER_HOSTILE;

        public static boolean contains(String s) {
            for (Stage stage : values()) {
                if (Objects.equals(stage.name(), s)) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum ScenarioType {
        COMMODITY_DELIVERY,
        DRUG_SMUGGLING,
        REBELLION_SUPPORT,
        ARTIFACT_DELIVERY,
        VIP_ESCORT;

        public static boolean contains(String s) {
            for (ScenarioType type : values()) {
                if (Objects.equals(type.name(), s)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class OrderFleetInterceptOtherAction implements MissionTrigger.TriggerAction {
        protected SectorEntityToken other;

        public OrderFleetInterceptOtherAction(SectorEntityToken other) {
            this.other = other;
        }

        public void doAction(MissionTrigger.TriggerActionContext context) {
            makeFleetInterceptOther(context.fleet, this.other, 1000f);
            if (!context.fleet.hasScriptOfClass(MissionFleetAutoDespawn.class)) {
                context.fleet.addScript(new MissionFleetAutoDespawn(context.mission, context.fleet));
            }
        }
    }

    public static class OrderFleetFollowNearbyPlayerInStage implements MissionTrigger.TriggerAction {
        protected List<Object> stages;
        protected BaseHubMission mission;
        protected float maxRange;

        public OrderFleetFollowNearbyPlayerInStage(BaseHubMission mission, float maxRange, Object... stages) {
            this.mission = mission;
            this.maxRange = maxRange;
            this.stages = Arrays.asList(stages);
        }

        public void doAction(MissionTrigger.TriggerActionContext context) {
            context.fleet.addScript(new MissionFleetFollowPlayerIfNearby(context.fleet, this.mission, this.maxRange, this.stages));
        }
    }

    public static class EntityNearbyOtherChecker implements ConditionChecker {
        protected SectorEntityToken entity;
        protected SectorEntityToken other;
        protected float maxRange;
        protected boolean checkInHyperspace;

        public EntityNearbyOtherChecker(SectorEntityToken entity, SectorEntityToken other, float maxRange, boolean checkInHyperspace) {
            this.entity = entity;
            this.other = other;
            this.maxRange = maxRange;
            this.checkInHyperspace = checkInHyperspace;
        }

        @Override
        public boolean conditionsMet() {
            if (this.checkInHyperspace && (this.entity.isInHyperspace() || this.other.isInHyperspace())) {
                return Misc.getDistance(this.entity.getLocationInHyperspace(), this.other.getLocationInHyperspace()) < this.maxRange;
            }
            return Misc.getDistance(this.entity, this.other) < this.maxRange;
        }
    }

    public static class FleetWeakenedChecker implements ConditionChecker {
        public CampaignFleetAPI fleet;
        public float fleetPoints;
        public float damageThreshold;

        /**
         * @param damageThreshold from 0.1f to 0.8f only
         *
         */
        public FleetWeakenedChecker(CampaignFleetAPI fleet, float damageThreshold) {
            this.fleet = fleet;
            this.fleetPoints = fleet.getFleetPoints();
            this.damageThreshold = Math.max(0.1f, Math.min(damageThreshold, 0.8f));
        }

        public boolean conditionsMet() {
            return this.fleetPoints * this.damageThreshold > this.fleet.getFleetPoints();
        }
    }

    public static class SEPCreateFleetAction extends CreateFleetAction {
        public Float freighterPts = null;
        public boolean freighterIncludeCombatPts = false;
        public Float tankerPts = null;
        public boolean tankerIncludeCombatPts = false;
        public Float linerPts = null;
        public boolean linerIncludeCombatPts = false;
        public Float transportPts = null;
        public boolean transportIncludeCombatPts = false;
        public Float utilityPts = null;
        public boolean utilityIncludeCombatPts = false;

        public SEPCreateFleetAction(String type, Vector2f locInHyper, FleetSize fSize, FleetQuality fQuality, String factionId) {
            super(type, locInHyper, fSize, fQuality, factionId);
        }

        @Override
        public void doAction(MissionTrigger.TriggerActionContext context) {
            Random random;
            if (context.mission != null) {
                random = ((BaseHubMission) context.mission).getGenRandom();
            } else {
                random = Misc.random;
            }
            FactionAPI faction = Global.getSector().getFaction(this.params.factionId);
            float maxPoints = faction.getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
            float min = this.fSize.maxFPFraction - (this.fSize.maxFPFraction - this.fSize.prev().maxFPFraction) / 2f;
            float max = this.fSize.maxFPFraction + (this.fSize.next().maxFPFraction - this.fSize.maxFPFraction) / 2f;
            float fraction = min + (max - min) * random.nextFloat();
            float excess = 0;

            if (this.fSizeOverride != null) {
                fraction = this.fSizeOverride * (0.95f + random.nextFloat() * 0.1f);
            } else {
                int numShipsDoctrine = 1;
                if (this.params.doctrineOverride != null) {
                    numShipsDoctrine = this.params.doctrineOverride.getNumShips();
                } else {
                    numShipsDoctrine = faction.getDoctrine().getNumShips();
                }
                float doctrineMult = FleetFactoryV3.getDoctrineNumShipsMult(numShipsDoctrine);
                fraction *= 0.75f * doctrineMult;
                if (fraction > FleetSize.MAXIMUM.maxFPFraction) {
                    excess = fraction - FleetSize.MAXIMUM.maxFPFraction;
                    fraction = FleetSize.MAXIMUM.maxFPFraction;
                }
            }

            float combatPoints = fraction * maxPoints;
            if (this.combatFleetPointsOverride != null) {
                combatPoints = this.combatFleetPointsOverride;
            }

            FactionDoctrineAPI doctrine = this.params.doctrineOverride;
            if (excess > 0) {
                if (doctrine == null) {
                    doctrine = faction.getDoctrine().clone();
                }
                int added = Math.round(excess / 0.1f);
                if (added > 0) {
                    doctrine.setOfficerQuality(Math.min(5, doctrine.getOfficerQuality() + added));
                    doctrine.setShipQuality(doctrine.getShipQuality() + added);
                }
            }

            if (this.freighterPts == null) {
                this.freighterPts = 0f;
            }
            if (this.tankerPts == null) {
                this.tankerPts = 0f;
            }
            if (this.transportPts == null) {
                this.transportPts = 0f;
            }
            if (this.linerPts == null) {
                this.linerPts = 0f;
            }
            if (this.utilityPts == null) {
                this.utilityPts = 0f;
            }

            if (this.freighterIncludeCombatPts) {
                this.freighterPts += combatPoints;
            }
            if (this.tankerIncludeCombatPts) {
                this.tankerPts += combatPoints;
            }
            if (this.transportIncludeCombatPts) {
                this.transportPts += combatPoints;
            }
            if (this.linerIncludeCombatPts) {
                this.linerPts += combatPoints;
            }
            if (this.utilityIncludeCombatPts) {
                this.utilityPts += combatPoints;
            }

            if (this.freighterMult == null) {
                this.freighterMult = 0f;
            }
            if (this.tankerMult == null) {
                this.tankerMult = 0f;
            }
            if (this.linerMult == null) {
                this.linerMult = 0f;
            }
            if (this.transportMult == null) {
                this.transportMult = 0f;
            }
            if (this.utilityMult == null) {
                this.utilityMult = 0f;
            }
            if (this.qualityMod == null) {
                this.qualityMod = 0f;
            }

            this.params.combatPts = combatPoints;
            this.params.freighterPts = this.freighterPts * this.freighterMult;
            this.params.tankerPts = this.tankerPts * this.tankerMult;
            this.params.transportPts = this.transportPts * this.transportMult;
            this.params.linerPts = this.linerPts * this.linerMult;
            this.params.utilityPts = this.utilityPts * this.utilityMult;
            this.params.qualityMod = this.qualityMod;
            this.params.doctrineOverride = doctrine;
            this.params.random = random;


            if (this.fQuality != null) {
                switch (this.fQuality) {
                    case VERY_LOW:
                        if (this.fQualityMod != null) {
                            this.params.qualityMod += this.fQuality.qualityMod;
                        } else {
                            this.params.qualityOverride = 0f;
                        }
                        break;
                    case LOWER, HIGHER, DEFAULT, VERY_HIGH:
                        this.params.qualityMod += this.fQuality.qualityMod;
                        break;
                    case SMOD_1, SMOD_2, SMOD_3:
                        this.params.qualityMod += this.fQuality.qualityMod;
                        this.params.averageSMods = this.fQuality.numSMods;
                        break;
                }
            }
            if (this.fQualityMod != null) {
                this.params.qualityMod += this.fQualityMod;
            }
            if (this.fQualitySMods != null) {
                this.params.averageSMods = this.fQualitySMods;
            }

            if (this.oNum != null) {
                switch (this.oNum) {
                    case NONE:
                        this.params.withOfficers = false;
                        break;
                    case FC_ONLY:
                        this.params.officerNumberMult = 0f;
                        break;
                    case FEWER:
                        this.params.officerNumberMult = 0.5f;
                        break;
                    case DEFAULT:
                        break;
                    case MORE:
                        this.params.officerNumberMult = 1.5f;
                        break;
                    case ALL_SHIPS:
                        this.params.officerNumberBonus = Global.getSettings().getInt("maxShipsInAIFleet");
                        break;
                }
            }

            if (this.oQuality != null) {
                switch (this.oQuality) {
                    case LOWER:
                        this.params.officerLevelBonus = -3;
                        this.params.officerLevelLimit = Global.getSettings().getInt("officerMaxLevel") - 1;
                        this.params.commanderLevelLimit = Global.getSettings().getInt("maxAIFleetCommanderLevel") - 2;
                        if (this.params.commanderLevelLimit < this.params.officerLevelLimit) {
                            this.params.commanderLevelLimit = this.params.officerLevelLimit;
                        }
                        break;
                    case DEFAULT:
                        break;
                    case HIGHER:
                        this.params.officerLevelBonus = 2;
                        this.params.officerLevelLimit = Global.getSettings().getInt("officerMaxLevel") + (int) OfficerTraining.MAX_LEVEL_BONUS;
                        break;
                    case UNUSUALLY_HIGH:
                        this.params.officerLevelBonus = 4;
                        this.params.officerLevelLimit = SalvageSpecialAssigner.EXCEPTIONAL_PODS_OFFICER_LEVEL;
                        break;
                    case AI_GAMMA:
                    case AI_BETA:
                    case AI_BETA_OR_GAMMA:
                    case AI_ALPHA:
                    case AI_MIXED:
                    case AI_OMEGA:
                        this.params.aiCores = this.oQuality;
                        break;
                }
                if (this.doNotIntegrateAICores != null) {
                    this.params.doNotIntegrateAICores = this.doNotIntegrateAICores;
                }
            }

            if (this.shipPickMode != null) {
                this.params.modeOverride = this.shipPickMode;
            }

            this.params.updateQualityAndProducerFromSourceMarket();
            if (this.qualityOverride != null) {
                this.params.qualityOverride = this.qualityOverride + this.params.qualityMod;
            }
            context.fleet = FleetFactoryV3.createFleet(this.params);
            context.fleet.setFacing(random.nextFloat() * 360f);

            if (this.faction != null) {
                context.fleet.setFaction(this.faction, true);
            }

            if (this.nameOverride != null) {
                context.fleet.setName(this.nameOverride);
            }
            if (this.noFactionInName != null && this.noFactionInName) {
                context.fleet.setNoFactionInName(true);
            }

            if (this.removeInflater != null && this.removeInflater) {
                context.fleet.setInflater(null);
            } else {
                if (context.fleet.getInflater() instanceof DefaultFleetInflater inflater) {
                    if (inflater.getParams() instanceof DefaultFleetInflaterParams p) {
                        if (this.allWeapons != null) {
                            p.allWeapons = this.allWeapons;
                        }
                        if (this.shipPickMode != null) {
                            p.mode = this.shipPickMode;
                        }
                    }
                }
            }

            context.fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true);
            context.allFleets.add(context.fleet);

            if (!context.fleet.hasScriptOfClass(MissionFleetAutoDespawn.class)) {
                context.fleet.addScript(new MissionFleetAutoDespawn(context.mission, context.fleet));
            }

            if (this.damage != null) {
                FleetFactoryV3.applyDamageToFleet(context.fleet, this.damage, false, random);
            }
        }
    }
}
