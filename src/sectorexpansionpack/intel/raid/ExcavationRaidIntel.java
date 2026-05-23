package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SEPHiddenItemSpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class ExcavationRaidIntel extends RaidIntel implements GenericOrganizeStage.ShowStageInfoDelegate, GenericAssembleStage.AssembleStageDelegate {
    public static final float WRECK_CHANCE = 0.5f;
    public static final String SOURCE_KEY = "$sep_eri_source";
    public static final String TARGET_KEY = "$sep_eri_target";
    public static final String EVENT_KEY = "$sep_eri_eventRef";
    public static final String FLEET_KEY = "$sep_eri_fleet";
    public static final String FLEET_DEFEAT_TRIGGER = "SEPERIFleetDefeated";
    public static final String HAS_ARTIFACT_KEY = "$sep_eri_hasArtifact";
    public static final String HAS_ARTIFACT_REASON = "sep_eri";
    public static final Logger log = Global.getLogger(ExcavationRaidIntel.class);
    public static Object RETURNED_UPDATE = new Object();
    protected Outcome outcome;
    protected MarketAPI source;
    protected SectorEntityToken target;
    protected SpecialItemSpecAPI specialItem;
    protected Random random;
    protected float leakChance = 0.3f;
    protected boolean leaked = false;
    protected boolean specialItemGiven = false;

    public ExcavationRaidIntel(MarketAPI source, SectorEntityToken target, SpecialItemSpecAPI specialItem) {
        super(target.getStarSystem(), source.getFaction(), null);

        this.defenderStr = getLargeSize(false);
        this.source = source;
        this.target = target;
        this.specialItem = specialItem;
        this.random = new Random();

        float desireMult = Utils.getSpecialItemsDesireMult(getFaction().getId());
        float randomMult = 0.6f + this.random.nextFloat() * 0.6f;
        float approxNumFleets = this.defenderStr / getFPLarge();
        float bonusStrengthPerFleet = getBonusStrengthPerFleet();
        float baseFP = (this.defenderStr - (approxNumFleets * bonusStrengthPerFleet)) * desireMult * randomMult * 3f;
        float prepDays = getPrepDays(baseFP) / 2f;

        addStage(new GenericOrganizeStage(this, this.source, prepDays));

        AssembleStage assembleStage = new GenericAssembleStage(this, this.source.getPrimaryEntity(), prepDays, this.source);
        assembleStage.setSpawnFP(baseFP);
        assembleStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(assembleStage);

        SectorEntityToken rallyJumpPoint = Utils.findNearestHyperspaceJumpPoint(this.target);
        if (rallyJumpPoint == null) {
            endImmediately();
            return;
        }

        TravelStage travelStage = new GenericTravelStage(this, this.source.getPrimaryEntity(), rallyJumpPoint, true);
        travelStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(travelStage);

        ActionStage actionStage = new ExcavationActionStage(this, this.target, getRaidDays(baseFP));
        actionStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(actionStage);

        TravelStage returnStage = new GenericReturnStage(this, this.target, this.source.getPrimaryEntity(), true);
        returnStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(returnStage);

        // Flag source faction and targets to prevent multiple excavations on the same targets or factions
        this.source.getFaction().getMemoryWithoutUpdate().set(SOURCE_KEY, true);
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);

        // Mark the target for player and give it the special item
        Misc.makeImportant(this.target, HAS_ARTIFACT_REASON);
        Misc.setSalvageSpecial(this.target, new SEPHiddenItemSpecial.HiddenSpecialItemSpecialData(this.specialItem.getId()));

        log.info(String.format("Starting %s excavation at %s in the %s, targeting %s in the %s",
                getFaction().getDisplayName(),
                this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getName(), getSystem().getNameWithLowercaseTypeShort()));
    }

    protected float getPrepDays(float fp) {
        if (Global.getSettings().isDevMode()) {
            return 7f;
        }
        return 7f + (fp / getFPLarge());
    }

    protected float getRaidDays(float fp) {
        return 28f + (14f * (fp / this.defenderStr));
    }

    protected float getFleetFPAbortFraction() {
        return 0.33f;
    }

    @Override
    protected void advanceImpl(float amount) {
        if (this.outcome != null) {
            return;
        }

        RaidStage stage = this.stages.get(this.currentStage);

        if (!this.source.isInEconomy()) {
            setOutcome(Outcome.SOURCE_MARKET_LOST);
        } else {
            if (this.specialItemGiven && getActionStage().getStatus() == RaidStageStatus.SUCCESS && stage instanceof BaseRaidStage raidStage) {
                boolean specialItemLost = true;
                for (RouteManager.RouteData data : raidStage.getRoutes()) {
                    CampaignFleetAPI fleet = data.getActiveFleet();
                    if (fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(HAS_ARTIFACT_KEY)) {
                        specialItemLost = false;
                        break;
                    }
                }
                if (specialItemLost) {
                    setOutcome(Outcome.SPECIAL_ITEM_LOST);
                }
            }
        }

        if (this.outcome != null) {
            forceFail(true);
            return;
        }

        stage.advance(amount);

        RaidStageStatus status = stage.getStatus();
        if (status == RaidStageStatus.SUCCESS) {
            succeededAtStage(stage);
            this.currentStage++;
            setExtraDays(Math.max(0, getExtraDays() - stage.getExtraDaysUsed()));
            if (this.currentStage < this.stages.size()) {
                this.stages.get(this.currentStage).notifyStarted();
            }
        } else if (status == RaidStageStatus.FAILURE) {
            failedAtStage(stage);
            this.failStage = this.currentStage;
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(UPDATE_FAILED, false);
            }
        }
    }

    protected void succeededAtStage(RaidStage stage) {
        setFleetsMemoryAtStage(getStageIndex(stage), true);
        if (stage instanceof ActionStage) {
            Misc.makeUnimportant(this.target, HAS_ARTIFACT_REASON);
            this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
            this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
            this.target.getMemoryWithoutUpdate().unset(MemFlags.SALVAGE_SPECIAL_DATA);
        } else if (stage instanceof GenericReturnStage stage1) {
            SpecialItemData data = new SpecialItemData(this.specialItem.getId(), this.specialItem.getParams());
            Utils.findMarketToInstallSpecialItem(new EntityFinderMission(), getFaction().getId(), this.source, data, log);
            setOutcome(Outcome.SUCCEEDED);
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes()); // Order fleets to return home and despawn
            endAfterDelay();
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(RETURNED_UPDATE, false);
            }
        }

        if (!this.leaked && !(stage instanceof GenericReturnStage)) {
            if (Utils.rollProbability(this.leakChance)) {
                this.leaked = true;

                if (this.source.getStarSystem() != null) {
                    new ExcavationLeakedIntel(this.source, this.target, this.specialItem, stage, this);
                    log.info(String.format("Leaking artifact location at %s by unknown contact %s %s in the %s",
                            this.target.getStarSystem().getNameWithLowercaseType(), this.source.getOnOrAt(),
                            this.source.getName(), this.source.getStarSystem().getNameWithLowercaseType()));
                }
                return;
            }

            this.leakChance += 0.2f;
        }
    }

    @Override
    protected void failedAtStage(RaidStage stage) {
        if (this.outcome == null) {
            if (stage instanceof OrganizeStage) {
                setOutcome(Outcome.ABORTED_IN_PLANNING);
            } else if (stage instanceof AssembleStage) {
                setOutcome(Outcome.ASSEMBLING_DISRUPTED);
            } else if (stage instanceof TravelStage && !(stage instanceof GenericReturnStage)) {
                setOutcome(Outcome.NOT_ENOUGH_MADE_IT);
            } else if (stage instanceof ActionStage) {
                setOutcome(Outcome.FAILED);
            } else if (stage instanceof GenericReturnStage) {
                setOutcome(Outcome.NOT_ENOUGH_MADE_IT);
            }
        }
        if (stage instanceof BaseRaidStage stage1) {
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes()); // Order fleets to return home and despawn
        }
        endAfterDelay();
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        if (this.outcome != null && this.outcome == Outcome.SUCCEEDED) {
            return 14f;
        }
        return 7f;
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        // Don't remove the special item from the target if the
        // event fails before the excavation group takes it
        getFaction().getMemoryWithoutUpdate().unset(SOURCE_KEY);
        Misc.makeUnimportant(this.target, HAS_ARTIFACT_REASON);
        this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
        this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
    }

    @Override
    public String getName() {
        String base = Misc.ucFirst(getFaction().getPersonNamePrefix()) + " " + Misc.ucFirst(getRaidNoun());
        if (isEnding()) {
            if (this.outcome != null && this.outcome.isSucceeded()) {
                return base + " - Completed";
            } else if (this.outcome != null && this.outcome.isCancelled()) {
                return base + " - Cancelled";
            } else if (this.outcome != null && this.outcome.isFailed()) {
                return base + " - Failed";
            }
        } else {
            if (getActionStage().getStatus() == RaidStageStatus.SUCCESS) {
                return base + " - Successful";
            }
        }
        return base;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        addBasicDescription(info, width, height);
        addAssessmentSection(info, width, height);
        addStatusSection(info, width, height);
        addBulletPoints(info, ListInfoMode.IN_DESC);
        info.addSpacer(10f);
    }

    protected void addBasicDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addImage(getFaction().getLogo(), width, 128, opad);

        info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefixAOrAn()) + " %s operation to recover an " +
                        "artifact found in the " + getSystem().getNameWithLowercaseTypeShort() + ".", opad,
                getFaction().getBaseUIColor(), getFaction().getPersonNamePrefix());
    }

    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height) {
        if (this.outcome != null) {
            return;
        }

        float opad = 10f;

        Color h = Misc.getHighlightColor();

        FactionAPI faction = getFaction();
        String forcesNoun = getForcesNoun();

        AssembleStage assembleStage = getAssembleStage();

        int numFleets = (int) getNumFleets();
        String fleetNoun = "fleet";
        if (numFleets > 1) {
            fleetNoun = "fleets";
        }
        float raidStr = assembleStage.getOrigSpawnFP();
        raidStr += (numFleets * getBonusStrengthPerFleet());
        String strDesc = Misc.getStrengthDesc(raidStr);

        String assessment = "The " + forcesNoun + " are projected to be " + strDesc + " and likely comprised of " + numFleets + " " + fleetNoun + ".";

        info.addSectionHeading("Assessment", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);

        LabelAPI label = info.addPara(assessment, opad, faction.getBaseUIColor());
        label.setHighlight(strDesc, numFleets + "");
        label.setHighlightColors(faction.getBaseUIColor(), h);
    }

    public float getBonusStrengthPerFleet() {
        boolean isPirate = getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);
        float bonusFP = 120f; // Fleets from major factions have around 120 additional fp per fleet
        if (isPirate) {
            bonusFP = 70f; // Fleets from pirate factions have around 70 additional fp per fleet
        }
        return bonusFP;
    }

    protected void addStatusSection(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addSectionHeading("Status", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);

        if (this.outcome == null) {
            for (RaidStage stage : this.stages) {
                stage.showStageInfo(info);
                if (getStageIndex(stage) == this.failStage) {
                    break;
                }
            }
        } else {
            String status = getOutcomeDescription(false);
            info.addPara(status, opad);
        }
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) {
            initPad = opad;
        }

        Color tc = getBulletColorForMode(mode);

        bullet(info);
        Object param = getListInfoParam();
        boolean isUpdate = param != null;

        if (!isUpdate) {
            addNonUpdateBulletPoints(info, tc, param, mode, initPad);
        } else {
            addUpdateBulletPoints(info, tc, param, mode, initPad);
        }
        unindent(info);
    }

    public void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        if (this.outcome != null) {
            return;
        }

        Color h = Misc.getHighlightColor();
        RaidStage stage = this.stages.get(this.currentStage);

        float etaOrganize = getETAAtStage(GenericOrganizeStage.class);
        float etaAssemble = getETAAtStage(GenericAssembleStage.class) + etaOrganize;
        float etaTravel = getETAAtStage(GenericTravelStage.class) + etaAssemble;
        float etaAction = getETAAtStage(ExcavationActionStage.class);
        float etaReturn = getETAAtStage(GenericReturnStage.class);

        if (etaAssemble > 0 || stage.getClass() == GenericAssembleStage.class && stage.getStatus() == RaidStageStatus.ONGOING) {
            if ((int) etaAssemble <= 0f) {
                info.addPara("Departure imminent", tc, initPad);
            } else {
                String days = (int) etaAssemble == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until departure",
                        initPad, tc, h, "" + (int) etaAssemble);
            }
            initPad = 0f;
        }
        if (etaTravel > 0 || stage.getClass() == GenericTravelStage.class && stage.getStatus() == RaidStageStatus.ONGOING) {
            if ((int) etaTravel <= 0f) {
                info.addPara("Arrival imminent", tc, initPad);
            } else {
                String days = (int) etaTravel == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until arrival" +
                                " at " + getSystem().getNameWithLowercaseTypeShort(),
                        initPad, tc, h, "" + (int) etaTravel);
            }
            initPad = 0f;
        }
        if (stage instanceof ExcavationActionStage && (mode == ListInfoMode.MESSAGES || mode == ListInfoMode.INTEL)) {
            info.addPara("Operating in the " + getSystem().getNameWithLowercaseTypeShort(), tc, initPad);
            System.out.println(etaAction);
        }
        if (etaReturn > 0 && stage.getClass() == GenericReturnStage.class && stage.getStatus() == RaidStageStatus.ONGOING) {
            if ((int) etaReturn <= 0f) {
                info.addPara("Return imminent", tc, initPad);
            } else {
                String days = (int) etaReturn == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until return" + " to " +
                                this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                        initPad, tc, h, "" + (int) etaReturn);
            }
        }
    }

    public float getETAAtStage(Class<?> stageClass) {
        for (RaidStage stage : this.stages) {
            if (stage.getStatus() != RaidStageStatus.ONGOING) {
                continue;
            }
            if (stage.getClass() == stageClass) {
                float travelDays = 0f;
                boolean isTravelStage = false;

                if (stage instanceof GenericTravelStage ats) {
                    travelDays = RouteLocationCalculator.getTravelDays(ats.getFrom(), ats.getTo());
                    isTravelStage = true;
                }

                float remaining = isTravelStage ? travelDays - stage.getElapsed() : stage.getMaxDays() - stage.getElapsed();
                return Math.max(0f, remaining);
            }
        }
        return 0f;
    }

    public void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        if (ENTERED_SYSTEM_UPDATE.equals(param)) {
            info.addPara("Arrived at " + getSystem().getNameWithLowercaseTypeShort(), tc, initPad);
        } else if (UPDATE_RETURNING.equals(param)) {
            info.addPara("Returning to " + this.source.getStarSystem().getNameWithLowercaseTypeShort() + " with an artifact", tc, initPad);
        } else if (RETURNED_UPDATE.equals(param)) {
            info.addPara("Docking safely " + this.source.getOnOrAt() + " " + this.source.getName() + " with an artifact", tc, initPad);
        } else if (UPDATE_FAILED.equals(param)) {
            String desc = getOutcomeDescription(true);
            info.addPara(desc, tc, initPad);
        }
    }

    protected String getOutcomeDescription(boolean isUpdate) {
        String forces = getForcesNoun();
        String raid = getRaidNoun();
        String desc = "";
        switch (this.outcome) {
            // Failure outcomes
            case SPECIAL_ITEM_LOST -> {
                if (!isUpdate) {
                    desc = "The " + forces + " failed to return with the artifact. Any surviving fleets are retreating in disarray.";
                } else {
                    desc = "The " + forces + " failed to return with the artifact";
                }
            }
            case ASSEMBLING_DISRUPTED -> {
                if (!isUpdate) {
                    desc = "The " + forces + " failed to fully assemble. Any deployed fleets are retreating in disarray.";
                } else {
                    desc = "The " + forces + " failed to fully assemble";
                }
            }
            case NOT_ENOUGH_MADE_IT -> {
                if (!isUpdate) {
                    desc = "The " + forces + " failed to reach their objective. Any remaining fleets are retreating in disarray.";
                } else {
                    desc = "The " + forces + " failed to reach their objective";
                }
            }
            case FAILED -> {
                if (!isUpdate) {
                    desc = "The " + forces + " failed to complete their objective. Any surviving fleets are retreating in disarray.";
                } else {
                    desc = "The " + forces + " failed to complete their objective";
                }
            }
            // Cancelled outcomes but still failure
            case SOURCE_MARKET_LOST -> {
                if (!isUpdate) {
                    desc = "The " + raid + " was cancelled as the " + this.source.getName() + " colony no longer exists. " +
                            "Any deployed fleets are returning to their port of origin";
                } else {
                    desc = "The " + raid + "'s colony no longer exists";
                }
            }
            case ABORTED_IN_PLANNING -> {
                if (!isUpdate) {
                    desc = "The " + raid + " was cancelled during the planning stage and will no longer happen.";
                } else {
                    desc = "The " + raid + " was cancelled during the planning stage.";
                }
            }
            // Succeeded outcomes
            case SUCCEEDED -> {
                if (!isUpdate) {
                    desc = "The " + raid + " was successful and the acquired artifact is now in the hands of " +
                            this.faction.getDisplayNameWithArticle();
                } else {
                    desc = "The " + forces + " successfully returned with an artifact";
                }
            }
        }
        return desc;
    }

    public String getRaidNoun() {
        return "excavation";
    }

    public String getForcesNoun() {
        return getRaidNoun() + " forces";
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if ("endEvent".equals(action)) {
            setOutcome(Outcome.SPECIAL_ITEM_LOST);
            forceFail(false);
            sendUpdateIfPlayerHasIntel(UPDATE_FAILED, dialog.getTextPanel());
            return true;
        } else if ("giveArtifact".equals(action)) {
            SpecialItemData specialItemData = new SpecialItemData(this.specialItem.getId(), this.specialItem.getParams());
            Global.getSector().getPlayerFleet().getCargo().addSpecial(specialItemData, 1f);
            AddRemoveCommodity.addItemGainText(specialItemData, 1, dialog.getTextPanel());
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
        ActionStage action = getActionStage();
        ActionAssignmentAI.SEPFleetActionDelegate delegate = null;
        if (action instanceof ActionAssignmentAI.SEPFleetActionDelegate) {
            delegate = (ActionAssignmentAI.SEPFleetActionDelegate) action;
        }
        return new ActionAssignmentAI(fleet, route, delegate, false);
    }

    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
        RouteManager.OptionalFleetData extra = route.getExtra();

        boolean isPirate = getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);
        float combat = extra.fp;
        float tanker = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float transport = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float freighter = 0f;
        combat -= tanker;
        combat -= transport;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                locInHyper,
                factionId,
                null,
                extra.fleetType,
                combat, // combatPts
                freighter, // freighterPts
                tanker, // tankerPts
                transport, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod, won't get used since quality override is set
        );

        // Remove spawning strength variability
        params.ignoreMarketFleetSizeMult = true;
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;
        params.qualityOverride = 1f;
        FactionDoctrineAPI doctrineOverride = this.faction.getDoctrine().clone();
        if (!isPirate) {
            doctrineOverride.setOfficerQuality(3);
        }
        doctrineOverride.setShipQuality(3);
        doctrineOverride.setNumShips(3);
        params.doctrineOverride = doctrineOverride;
        // params.doNotAddShipsBeforePruning = true;

        params.timestamp = route.getTimestamp();
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);

        if (isPirate) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);
        fleet.getMemoryWithoutUpdate().set(FLEET_KEY, true);
        fleet.getMemoryWithoutUpdate().set(EVENT_KEY, this);

        RaidStage stage = this.stages.get(this.currentStage);
        setFleetMemoryAtStage(fleet, stage);

        fleet.setName("Grand Excavation Fleet");
        fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
        if (extra.fp <= getFPSmall()) {
            fleet.setName("Minor Excavation Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);
        } else if (extra.fp <= getFPMedium()) {
            fleet.setName("Major Excavation Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_CAPTAIN);
        }
        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);

        return fleet;
    }

    public void setFleetMemoryAtStage(CampaignFleetAPI fleet, RaidStage stage) {
        if (stage instanceof GenericReturnStage) {
            fleet.setNoAutoDespawn(true);
            if (!this.specialItemGiven) {
                fleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT_KEY, true);
                Misc.makeImportant(fleet, HAS_ARTIFACT_REASON);
                Misc.addDefeatTrigger(fleet, FLEET_DEFEAT_TRIGGER);
                this.specialItemGiven = true;
            }
        }
    }

    public void setFleetsMemoryAtStage(int index, boolean useNextStage) {
        RaidStage currStage = this.stages.get(index);
        List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(getRouteSourceId());

        boolean isNextStage = false;
        for (RouteManager.RouteData route : routes) {
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null) {
                continue;
            }

            if (useNextStage && !isNextStage) {
                isNextStage = true;
                index++;
                if (index + 1 > this.stages.size()) {
                    fleet.getMemoryWithoutUpdate().unset(FLEET_KEY);
                    fleet.getMemoryWithoutUpdate().unset(EVENT_KEY);
                    fleet.getMemoryWithoutUpdate().unset(HAS_ARTIFACT_KEY);
                    Misc.makeUnimportant(fleet, HAS_ARTIFACT_REASON);
                    Misc.removeDefeatTrigger(fleet, FLEET_DEFEAT_TRIGGER);
                    continue;
                }
                currStage = this.stages.get(index);
            }

            setFleetMemoryAtStage(fleet, currStage);
        }
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {
        if (!Objects.equals(route.getSource(), getRouteSourceId())) {
            return;
        }
        this.specialItemGiven = false;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info, RaidStage raidStage, RaidStageStatus status) {
        float opad = 10f;
        int curr = getCurrentStage();
        int index = getStageIndex(raidStage);

        if (curr == index) {
            if (raidStage instanceof GenericOrganizeStage) {
                BaseHubMission.addStandardMarketDesc("Making preparations in orbit around", this.source, info, opad);
            } else if (raidStage instanceof GenericAssembleStage) {
                BaseHubMission.addStandardMarketDesc("Making preparations in orbit around", this.source, info, opad);
            } else if (raidStage instanceof GenericTravelStage && !(raidStage instanceof GenericReturnStage)) {
                info.addPara("Travelling to the " + getSystem().getNameWithLowercaseType() + ".", opad);
            } else if (raidStage instanceof ExcavationActionStage) {
                info.addPara("Conducting operations in the " + getSystem().getNameWithLowercaseType() + ".", opad);
            } else if (raidStage instanceof GenericReturnStage) {
                info.addPara("Returning to " + this.source.getName() + " in the " +
                        this.source.getStarSystem().getNameWithLowercaseTypeShort() +
                        " and is currently carrying an artifact.", opad);
            }
        }
    }

    @Override
    public float getAdjustedFleetStrength(float fp) {
        return fp + getBonusStrengthPerFleet();
    }

    @Override
    public String pickFleetType(float remainingFP) {
        if (remainingFP >= getFPLarge()) {
            return FleetTypes.PATROL_LARGE;
        } else if (remainingFP >= getFPMedium()) {
            return FleetTypes.PATROL_MEDIUM;
        }
        return FleetTypes.PATROL_SMALL;
    }

    @Override
    public float getFP(String fleetType, float remainingFP) {
        float base = getFPSmall();
        if (FleetTypes.PATROL_LARGE.equals(fleetType)) {
            base = getFPLarge();
        } else if (FleetTypes.PATROL_MEDIUM.equals(fleetType)) {
            base = getFPMedium();
        }

        if (base > remainingFP) {
            base = remainingFP;
        }

        remainingFP -= base;

        if (remainingFP < getFPSmall() * 0.5f) {
            base += remainingFP;
            remainingFP = 0f;
        }

        return base;
    }

    @Override
    public float getLargeSize(boolean limitToSpawnFP) {
        return getFPLarge();
    }

    @Override
    public float getFPLarge() {
        float fp = getFaction().getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
        if (fp < 250f) {
            fp = 250f;
        }
        return fp;
    }

    @Override
    public float getFPMedium() {
        return getFPLarge() / 2f;
    }

    @Override
    public float getFPSmall() {
        return getFPMedium() / 2f;
    }

    public enum Outcome {
        SPECIAL_ITEM_LOST,
        ASSEMBLING_DISRUPTED,
        NOT_ENOUGH_MADE_IT,
        FAILED,

        SOURCE_MARKET_LOST,
        ABORTED_IN_PLANNING,

        SUCCEEDED;

        public boolean isFailed() {
            return this == SPECIAL_ITEM_LOST || this == ASSEMBLING_DISRUPTED || this == NOT_ENOUGH_MADE_IT || this == FAILED || isCancelled();
        }

        public boolean isCancelled() {
            return this == SOURCE_MARKET_LOST || this == ABORTED_IN_PLANNING;
        }

        public boolean isSucceeded() {
            return this == SUCCEEDED;
        }
    }
}
