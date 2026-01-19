package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.hub.SEPHubMissionWithScenario;

import java.awt.*;
import java.util.*;
import java.util.List;

public class FleetEscortMission extends SEPHubMissionWithScenario {
    public static final float MISSION_DURATION = 120f;
    public static float BAR_MILITARY_CHANCE = 0.4f;
    public static Logger log = Global.getLogger(FleetEscortMission.class);
    protected CampaignFleetAPI fleet;
    protected SectorEntityToken gotoEntity;
    protected String itemId;

    public FleetEscortMission() {
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

        requireMarketLocationNot(createdAt.getContainingLocation());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        if (this.scenarioType == ScenarioType.COMMODITY_DELIVERY) {
            preferMarketHasCommodityDemands();
        } else if (this.scenarioType == ScenarioType.DRUG_SMUGGLING) {
            preferMarketFaction(Factions.LUDDIC_PATH, Factions.PIRATES);
        } else if (this.scenarioType == ScenarioType.REBELLION_SUPPORT) {
            requireMarketFactionNot(getPerson().getFaction().getId());
        } else if (this.scenarioType == ScenarioType.VIP_ESCORT) {
            requireMarketFactionNotHostileTo(getPerson().getFaction().getId());
        }
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

        beginStageTrigger(Stage.GOTO);
        triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, getPerson().getFaction().getId(), FleetTypes.PATROL_MEDIUM, createdAt.getLocationInHyperspace());
        triggerSetFleetToStandardFleet(3);
        if (this.scenarioType == ScenarioType.COMMODITY_DELIVERY) {
            triggerSetFleetCombatFleetPoints(30f);
            triggerFleetSetFreighterData(calculateCombatPoints(getPreviousCreateFleetAction()), 1f, false);
            triggerFleetSetTankerData(calculateCombatPoints(getPreviousCreateFleetAction()), 0.1f, false);
            this.itemId = pickCommodityIdMarketDemand(market);
            triggerAddCommodityFractionDrop(this.itemId, 0.4f + 0.4f * getGenRandom().nextFloat());
        } else if (this.scenarioType == ScenarioType.DRUG_SMUGGLING) {
            triggerSetFleetCombatFleetPoints(30f);
            triggerFleetSetFreighterData(calculateCombatPoints(getPreviousCreateFleetAction()), 1f, false);
            triggerFleetSetTankerData(calculateCombatPoints(getPreviousCreateFleetAction()), 0.1f, false);
            triggerAddCommodityFractionDrop(Commodities.DRUGS, 0.4f + 0.4f * getGenRandom().nextFloat());
        } else if (this.scenarioType == ScenarioType.REBELLION_SUPPORT) {
            triggerFleetSetFreighterData(0f, 2f, true);
            triggerFleetSetTankerData(0f, 2f, true);
            triggerAddCommodityFractionDrop(Commodities.MARINES, 0.4f);
            triggerAddCommodityFractionDrop(Commodities.HAND_WEAPONS, 0.1f);
            triggerAddCommodityFractionDrop(Commodities.FUEL, 0.3f);
        } else if (this.scenarioType == ScenarioType.VIP_ESCORT) {
            triggerSetFleetCombatFleetPoints(30f);
            triggerFleetSetFreighterData(0f, 0.1f, true);
            triggerFleetSetTankerData(0f, 0.1f, true);
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

        beginStageTrigger(Stage.RETURN, Stage.COMPLETED);
        triggerRunScriptAfterDelay(0f, () -> {
            if (this.scenarioType == ScenarioType.COMMODITY_DELIVERY) {
                removeCommodityFraction(this.fleet, this.itemId, 0.75f + getGenRandom().nextFloat() * 0.2f);
            } else if (this.scenarioType == ScenarioType.DRUG_SMUGGLING) {
                removeCommodityFraction(this.fleet, Commodities.DRUGS, 0.75f + getGenRandom().nextFloat() * 0.2f);
            } else if (this.scenarioType == ScenarioType.REBELLION_SUPPORT) {
                removeCommodityFraction(this.fleet, Commodities.MARINES, 0.75f + getGenRandom().nextFloat() * 0.2f);
                removeCommodityFraction(this.fleet, Commodities.HAND_WEAPONS, 0.75f + getGenRandom().nextFloat() * 0.2f);
                removeCommodityFraction(this.fleet, Commodities.FUEL, 0.75f + getGenRandom().nextFloat() * 0.1f);
            }
        });
        endTrigger();

        makeImportant(this.gotoEntity, "$sep_fem_gotoEntity", Stage.GOTO);
        makeImportant(this.fleet, "$sep_fem_escortedFleet", Stage.GOTO, Stage.WAIT, Stage.RETURN);
        makeImportant(getPerson(), "$sep_fem_returnPerson", Stage.RETURN);

        setStartingStage(Stage.GOTO);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        if (this.scenario.getTags().contains("oneWay")) {
            connectWithEntityNearbyOther(Stage.GOTO, Stage.WAIT, this.fleet, this.gotoEntity, 1000f);
            connectWithDaysElapsed(Stage.WAIT, Stage.COMPLETED, 7f);
        } else {
            connectWithEntityNearbyOther(Stage.GOTO, Stage.WAIT, this.fleet, this.gotoEntity, 1000f);
            connectWithDaysElapsed(Stage.WAIT, Stage.RETURN, 7f);
            connectWithEntityNearbyOther(Stage.RETURN, Stage.COMPLETED, this.fleet, getPerson().getMarket().getPrimaryEntity(), 1000f);
        }

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

        setScenarioCreditReward(this.scenario.getCreditReward());
        setRepChanges(0.05f, 0.1f, 0.05f, 0.1f);
        setScenarioComplications(Stage.class, log);

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

    public String pickCommodityIdMarketDemand(MarketAPI market) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(getGenRandom());
        for (MarketDemandAPI demand : market.getDemandData().getDemandList()) {
            if (demand.getBaseCommodity() == null || demand.getBaseCommodity().hasTag("nonecon") || demand.getBaseCommodity().hasTag("ai_core")) {
                continue;
            }
            picker.add(demand.getBaseCommodity().getId());
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
    public void setCustomTagTriggers(List<String> tags) {
        if (tags.contains("interceptEscort")) {
            triggerOrderFleetInterceptOther(this.fleet);
        }
    }

    @Override
    public SectorEntityToken getGotoEntity(Object stage) {
        if (stage == Stage.GOTO) {
            return this.gotoEntity;
        } else if (stage == Stage.WAIT) {
            return this.gotoEntity;
        }
        return getPerson().getMarket().getPrimaryEntity();
    }

    @Override
    public String getBaseName() {
        return "Fleet Escort";
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_fem_scenarioId", this.scenario.getScenarioId());
        set("$sep_fem_mrktName", this.gotoEntity.getName());
        set("$sep_fem_sysName", this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        set("$sep_fem_duration", Misc.getWithDGS(this.timeLimit.days));
        set("$sep_fem_creditReward", Misc.getDGSCredits(getCreditsReward()));
        if (this.scenarioType == ScenarioType.COMMODITY_DELIVERY) {
            CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(this.itemId);
            if (commoditySpec != null) {
                set("$sep_fem_itemName", commoditySpec.getName());
            }
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
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = this.gotoEntity.getMarket();
        if (market == null) {
            return;
        }
        if (this.scenarioType == ScenarioType.COMMODITY_DELIVERY) {
            int cargoCap = (int) this.fleet.getCargo().getMaxCapacity();
            CommodityOnMarketAPI commodityOnMarket = market.getCommodityData(this.itemId);
            CommoditySpecAPI commoditySpec = Global.getSector().getEconomy().getCommoditySpec(commodityOnMarket.getId());
            int commodityAmount = (int) (cargoCap * 0.75f + getGenRandom().nextFloat() * 0.2f);
            Misc.affectAvailabilityWithinReason(commodityOnMarket, commodityAmount);
            log.info(String.format("Affecting availability of %s on %s in the %s by %s", commoditySpec.getName(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort(), commodityAmount));
        } else if (this.scenarioType == ScenarioType.DRUG_SMUGGLING) {
            int cargoCap = (int) this.fleet.getCargo().getMaxCapacity();
            CommodityOnMarketAPI commodityOnMarket = market.getCommodityData(Commodities.DRUGS);
            CommoditySpecAPI commoditySpec = Global.getSector().getEconomy().getCommoditySpec(commodityOnMarket.getId());
            int commodityAmount = (int) (cargoCap * 0.75f + getGenRandom().nextFloat() * 0.2f);
            Misc.affectAvailabilityWithinReason(commodityOnMarket, commodityAmount);
            log.info(String.format("Affecting availability of %s on %s in the %s by %s", commoditySpec.getName(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort(), commodityAmount));
        } else if (this.scenarioType == ScenarioType.REBELLION_SUPPORT) {
            WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
            for (Industry ind : market.getIndustries()) {
                if (!ind.canBeDisrupted()) {
                    continue;
                }
                picker.add(ind);
            }

            Industry industryToDisrupt = picker.pickAndRemove();
            float disruptDays = MarketCMD.getDisruptDaysPerToken(market, industryToDisrupt) * 3;
            industryToDisrupt.setDisrupted(disruptDays);
            log.info(String.format("Disrupting %s on %s in the %s for %s", industryToDisrupt.getCurrentName(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort(), disruptDays));

            industryToDisrupt = picker.pickAndRemove();
            disruptDays = MarketCMD.getDisruptDaysPerToken(market, industryToDisrupt) * 3;
            industryToDisrupt.setDisrupted(MarketCMD.getDisruptDaysPerToken(market, industryToDisrupt) * 3);
            log.info(String.format("Disrupting %s on %s in the %s for %s", industryToDisrupt.getCurrentName(),
                    market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort(), disruptDays));

            market.getStability().addTemporaryModFlat(90f, "mod_" + Misc.genUID(), "Recent armed rebellion", -3f);
            log.info(String.format("Destabilising %s in the %s for -3f Stability", market.getName(),
                    market.getStarSystem().getNameWithLowercaseTypeShort()));
        } else if (this.scenarioType == ScenarioType.VIP_ESCORT) {
            if (Objects.equals(market.getFaction().getId(), getPerson().getFaction().getId())) {
                return;
            }
            float repAmount = 0.1f;
            RepLevel repLevellimit = RepLevel.FRIENDLY;
            if (rollProbability(0.5f)) {
                repAmount = -0.1f;
                repLevellimit = RepLevel.HOSTILE;
            }
            market.getFaction().adjustRelationship(getPerson().getFaction().getId(), repAmount, repLevellimit);
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
                    getPerson().getMarket().getName(), getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort());
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
                    getPerson().getMarket().getName(), getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort());
        }
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();

        ArrowData arrowFleet = new ArrowData(Global.getSector().getPlayerFleet(), this.fleet);
        arrowFleet.width = 14f;
        arrowFleet.color = Misc.getHighlightColor();
        result.add(arrowFleet);

        if (this.getCurrentStage() == Stage.GOTO) {
            ArrowData arrowDestination = new ArrowData(Global.getSector().getPlayerFleet(), this.gotoEntity);
            arrowDestination.width = 14f;
            result.add(arrowDestination);
        } else if (this.getCurrentStage() == Stage.RETURN) {
            ArrowData arrowDestination = new ArrowData(Global.getSector().getPlayerFleet(), getPerson().getMarket().getPrimaryEntity());
            arrowDestination.width = 14f;
            result.add(arrowDestination);
        }

        return result;
    }

    public enum Stage {
        GOTO,
        WAIT,
        RETURN,
        COMPLETED,
        FAILED,
        FAILED_DECIV,
        FAILED_GIVER_HOSTILE
    }

    public enum ScenarioType {
        COMMODITY_DELIVERY,
        DRUG_SMUGGLING,
        REBELLION_SUPPORT,
        VIP_ESCORT
    }
}
