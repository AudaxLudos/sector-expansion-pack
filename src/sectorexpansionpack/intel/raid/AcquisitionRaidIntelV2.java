package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
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
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AcquisitionRaidIntelV2 extends RaidIntel implements GenericOrganizeStage.ShowStageInfoDelegate, GenericAssembleStage.AssembleStageDelegate {
    public static final String SOURCE_KEY = "$sep_ari_source";
    public static final String TARGET_KEY = "$sep_ari_target";
    public static final String EVENT_KEY = "$sep_ari_eventRef";
    public static final String FLEET_KEY = "$sep_ari_fleet";
    public static final String FLEET_DEFEAT_TRIGGER = "SEPARIFleetDefeated";
    public static final String HAS_ARTIFACT_KEY = "$sep_ari_hasArtifact";
    public static final String HAS_ARTIFACT_REASON = "sep_ari";
    public static final Logger log = Global.getLogger(AcquisitionRaidIntelV2.class);
    public static Object RETURNED_UPDATE = new Object();

    protected Outcome outcome;
    protected MarketAPI source;
    protected MarketAPI target;
    protected FactionAPI targetFaction;
    protected SpecialItemSpecAPI artifact;
    protected Random random;
    protected boolean artifactGiven = false;

    public AcquisitionRaidIntelV2(MarketAPI source, MarketAPI target, SpecialItemSpecAPI artifact) {
        super(target.getStarSystem(), source.getFaction(), null);

        this.source = source;
        this.target = target;
        this.targetFaction = target.getFaction();
        this.artifact = artifact;
        this.random = new Random();

        float desireMult = Utils.getSpecialItemsDesireMult(getFaction().getId());
        float randomMult = 0.6f + this.random.nextFloat() * 0.6f;
        float approxNumFleets = this.defenderStr / getFPLarge();
        float bonusStrengthPerFleet = getBonusStrengthPerFleet();
        float baseFP = (this.defenderStr - (approxNumFleets * bonusStrengthPerFleet)) * desireMult * randomMult;
        float prepDays = getPrepDays(baseFP) / 2f;

        addStage(new GenericOrganizeStage(this, this.source, prepDays));

        AssembleStage assembleStage = new GenericAssembleStage(this, this.source.getPrimaryEntity(), prepDays, this.source);
        assembleStage.setSpawnFP(baseFP);
        assembleStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(assembleStage);

        SectorEntityToken raidJumpPoint = Utils.findNearestHyperspaceJumpPoint(this.target.getPrimaryEntity());
        if (raidJumpPoint == null) {
            endImmediately();
            return;
        }

        TravelStage travelStage = new GenericTravelStage(this, this.source.getPrimaryEntity(), raidJumpPoint, true);
        travelStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(travelStage);

        ActionStage actionStage = new AcquisitionActionStageV2(this, this.target, getRaidDays(baseFP));
        actionStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(actionStage);

        TravelStage returnStage = new GenericReturnStage(this, this.target.getPrimaryEntity(), this.source.getPrimaryEntity(), true);
        returnStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(returnStage);

        this.source.getFaction().getMemoryWithoutUpdate().set(SOURCE_KEY, true);
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);

        Global.getSector().getIntelManager().queueIntel(this);

        log.info(String.format("Starting %s acquisition v2 at %s in the %s, targeting %s in the %s",
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

        if (checkFailureConditions(stage)) {
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

    protected boolean checkFailureConditions(RaidStage stage) {
        if (!this.source.isInEconomy()) {
            setOutcome(Outcome.SOURCE_MARKET_LOST);
        } else if (!this.target.isInEconomy() && getActionStage() != null && getActionStage().getStatus() == RaidStageStatus.ONGOING) {
            setOutcome(Outcome.TARGET_MARKET_LOST);
        } else if (!this.faction.isHostileTo(this.targetFaction) && getActionStage() != null && getActionStage().getStatus() == RaidStageStatus.ONGOING) {
            setOutcome(Outcome.NO_LONGER_HOSTILE);
        } else if (this.artifactGiven && getActionStage().getStatus() == RaidStageStatus.SUCCESS && stage instanceof BaseRaidStage raidStage) {
            boolean artifactLost = true;
            for (RouteManager.RouteData data : raidStage.getRoutes()) {
                CampaignFleetAPI fleet = data.getActiveFleet();
                if (fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(HAS_ARTIFACT_KEY)) {
                    artifactLost = false;
                    break;
                }
            }
            if (artifactLost) {
                setOutcome(Outcome.ARTIFACT_LOST);
            }
        }

        return this.outcome != null && this.outcome.isFailed();
    }

    protected void succeededAtStage(RaidStage stage) {
        setFleetsMemoryAtStage(getStageIndex(stage), true);
        if (stage instanceof TravelStage && !(stage instanceof GenericReturnStage)) {
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(RaidIntel.ENTERED_SYSTEM_UPDATE, false);
            }
        } else if (stage instanceof AcquisitionActionStageV2) {
            SpecialItemData data = new SpecialItemData(this.artifact.getId(), this.artifact.getParams());
            for (Industry ind : this.target.getIndustries()) {
                if (ind.getSpecialItem() != null && Objects.equals(ind.getSpecialItem().getId(), data.getId())) {
                    log.info(String.format("Removing %s from %s at %s in the %s due to an acquisition",
                            this.artifact.getName(), ind.getCurrentName(), this.target.getName(),
                            getSystem().getNameWithLowercaseTypeShort()));
                    ind.setSpecialItem(null);
                    break;
                }
            }
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(RaidIntel.UPDATE_RETURNING, false);
            }
        } else if (stage instanceof GenericReturnStage stage1) {
            SpecialItemData data = new SpecialItemData(this.artifact.getId(), this.artifact.getParams());
            Utils.findMarketToInstallSpecialItem(new EntityFinderMission(), getFaction().getId(), this.source, data, log);
            setOutcome(Outcome.SUCCEEDED);
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes());
            endAfterDelay();
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(RETURNED_UPDATE, false);
            }
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
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes());
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

        getFaction().getMemoryWithoutUpdate().unset(SOURCE_KEY);
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
        } else if (getActionStage().getStatus() == RaidStageStatus.SUCCESS) {
            return base + " - Successful";
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
        info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefixAOrAn()) + " %s operation to take an " +
                        "artifact found in the " + getSystem().getNameWithLowercaseTypeShort() + ".", opad,
                getFaction().getBaseUIColor(), getFaction().getPersonNamePrefix());
    }

    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height) {
        if (this.outcome != null) {
            return;
        }

        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        FactionAPI faction = getFaction();

        int numFleets = (int) getNumFleets();
        String fleetNoun = numFleets == 1 ? "fleet" : "fleets";
        float raidStr = getAssembleStage().getOrigSpawnFP() + (numFleets * getBonusStrengthPerFleet());
        float defenderStr = WarSimScript.getEnemyStrength(getFaction(), getSystem());
        String strDesc = Misc.getStrengthDesc(raidStr);

        String defenderHighlight = "";
        Color defenderHighlightColor = h;
        String outcomeText = "";
        boolean potentialDanger = false;
        if (getActionStage().getStatus() != RaidStageStatus.SUCCESS) {
            float ratio = raidStr / defenderStr;
            if (ratio < 0.75f) {
                defenderHighlight = "superior";
                defenderHighlightColor = Misc.getPositiveHighlightColor();
                outcomeText = "the " + getRaidNoun() + " is unlikely to find success";
            } else if (ratio < 1.25f) {
                defenderHighlight = "evenly matched";
                outcomeText = "the outcome of the " + getRaidNoun() + " is uncertain";
                potentialDanger = true;
            } else {
                defenderHighlight = "outmatched";
                defenderHighlightColor = bad;
                outcomeText = "the " + getRaidNoun() + " is likely to find success";
                potentialDanger = true;
            }
        }

        String assessment = "The " + getForcesNoun() + " are projected to be " + strDesc +
                " and likely comprised of " + numFleets + " " + fleetNoun + ".";
        if (!defenderHighlight.isBlank()) {
            assessment += " The defending fleets are " + defenderHighlight + ", and " + outcomeText + ".";
        }

        info.addSectionHeading("Assessment", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);

        LabelAPI label = info.addPara(assessment, opad, faction.getBaseUIColor());
        label.setHighlight(strDesc, numFleets + "", defenderHighlight);
        label.setHighlightColors(faction.getBaseUIColor(), h, defenderHighlightColor);

        if (getActionStage().getStatus() != RaidStageStatus.SUCCESS) {
            List<MarketAPI> targets = new ArrayList<>();
            List<MarketAPI> safe = new ArrayList<>();
            List<MarketAPI> unsafe = new ArrayList<>();

            for (MarketAPI market : Misc.getMarketsInLocation(getSystem())) {
                boolean hasSpecialItems = false;
                for (Industry industry : market.getIndustries()) {
                    if (industry.getSpecialItem() != null) {
                        hasSpecialItems = true;
                        break;
                    }
                }
                if (hasSpecialItems) {
                    targets.add(market);
                    float stationStr = WarSimScript.getStationStrength(market.getFaction(), getSystem(), market.getPrimaryEntity());
                    float totalDefense = defenderStr + stationStr;
                    if (totalDefense > raidStr * 1.25f) {
                        safe.add(market);
                    } else {
                        unsafe.add(market);
                    }
                }
            }

            if (potentialDanger) {
                if (safe.size() == targets.size()) {
                    info.addPara("However, all colonies should be safe from the " + getRaidNoun() + ", owing to their orbital defenses.", opad);
                } else if (!unsafe.isEmpty()) {
                    String isOrAre = unsafe.size() == 1 ? "is" : "are";
                    String colonyNoun = unsafe.size() == 1 ? "colony " : "colonies ";
                    String riskTxt = isOrAre + " at risk of losing a used special item:";
                    info.addPara("The following " + colonyNoun + riskTxt, opad, bad, "losing a used special item");
                    FactionAPI f = Global.getSector().getPlayerFaction();
                    addMarketTable(info, f.getBaseUIColor(), f.getDarkUIColor(), f.getBrightUIColor(), unsafe, width, opad);
                }
            }
        }
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
            info.addPara(getOutcomeDescription(false), opad);
        }
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float initPad = mode == ListInfoMode.IN_DESC ? 10f : 3f;
        Color tc = getBulletColorForMode(mode);
        Object param = getListInfoParam();

        bullet(info);
        if (param == null) {
            addNonUpdateBulletPoints(info, tc, mode, initPad);
        } else {
            addUpdateBulletPoints(info, tc, param, initPad);
        }
        unindent(info);
    }

    public void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, ListInfoMode mode, float initPad) {
        if (this.outcome != null) {
            return;
        }

        Color h = Misc.getHighlightColor();
        RaidStage stage = this.stages.get(this.currentStage);

        float etaOrganize = getETAAtStage(GenericOrganizeStage.class);
        float etaAssemble = getETAAtStage(GenericAssembleStage.class) + etaOrganize;
        float etaTravel = getETAAtStage(GenericTravelStage.class) + etaAssemble;
        float etaReturn = getETAAtStage(GenericReturnStage.class);

        if (mode != ListInfoMode.IN_DESC) {
            info.addPara("Target: " + this.targetFaction.getDisplayName(), initPad, tc, this.targetFaction.getBaseUIColor(), this.targetFaction.getDisplayName());
            initPad = 0f;
        }
        if (etaAssemble > 0 || stage.getClass() == GenericAssembleStage.class && stage.getStatus() == RaidStageStatus.ONGOING) {
            if ((int) etaAssemble <= 0f) {
                info.addPara("Departure imminent", tc, initPad);
            } else {
                String days = (int) etaAssemble == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until departure", initPad, tc, h, "" + (int) etaAssemble);
            }
            initPad = 0f;
        }
        if (etaTravel > 0 || stage.getClass() == GenericTravelStage.class && stage.getStatus() == RaidStageStatus.ONGOING) {
            if ((int) etaTravel <= 0f) {
                info.addPara("Arrival imminent", tc, initPad);
            } else {
                String days = (int) etaTravel == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until arrival at " + getSystem().getNameWithLowercaseTypeShort(),
                        initPad, tc, h, "" + (int) etaTravel);
            }
            initPad = 0f;
        }
        if (stage instanceof AcquisitionActionStageV2 && (mode == ListInfoMode.MESSAGES || mode == ListInfoMode.INTEL)) {
            info.addPara("Operating in the " + getSystem().getNameWithLowercaseTypeShort(), tc, initPad);
            initPad = 0f;
        }
        if (etaReturn > 0 && stage.getClass() == GenericReturnStage.class && stage.getStatus() == RaidStageStatus.ONGOING) {
            if ((int) etaReturn <= 0f) {
                info.addPara("Return imminent", tc, initPad);
            } else {
                String days = (int) etaReturn == 1 ? "day" : "days";
                info.addPara("Estimated %s " + days + " until return to " + this.source.getStarSystem().getNameWithLowercaseTypeShort(),
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

                if (stage instanceof GenericTravelStage travelStage) {
                    travelDays = RouteLocationCalculator.getTravelDays(travelStage.getFrom(), travelStage.getTo());
                    isTravelStage = true;
                }

                float remaining = isTravelStage ? travelDays - stage.getElapsed() : stage.getMaxDays() - stage.getElapsed();
                return Math.max(0f, remaining);
            }
        }
        return 0f;
    }

    public void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, float initPad) {
        if (ENTERED_SYSTEM_UPDATE.equals(param)) {
            info.addPara("Arrived at " + getSystem().getNameWithLowercaseTypeShort(), tc, initPad);
        } else if (UPDATE_RETURNING.equals(param)) {
            info.addPara("Returning to " + this.source.getStarSystem().getNameWithLowercaseTypeShort() + " with an artifact", tc, initPad);
        } else if (RETURNED_UPDATE.equals(param)) {
            info.addPara("Docking safely " + this.source.getOnOrAt() + " " + this.source.getName() + " with an artifact", tc, initPad);
        } else if (UPDATE_FAILED.equals(param)) {
            info.addPara(getOutcomeDescription(true), tc, initPad);
        }
    }

    protected String getOutcomeDescription(boolean isUpdate) {
        String forces = getForcesNoun();
        String raid = getRaidNoun();
        return switch (this.outcome) {
            case ARTIFACT_LOST -> isUpdate ? "The " + forces + " failed to keep the artifact safe" :
                    "The " + forces + " failed to return with the artifact. Any surviving fleets are retreating in disarray.";
            case ASSEMBLING_DISRUPTED -> isUpdate ? "The " + forces + " failed to fully assemble" :
                    "The " + forces + " failed to fully assemble. Any deployed fleets are retreating in disarray.";
            case NOT_ENOUGH_MADE_IT -> isUpdate ? "The " + forces + " failed to reach their target" :
                    "The " + forces + " failed to reach their objective. Any remaining fleets are retreating in disarray.";
            case FAILED -> isUpdate ? "The " + forces + " failed to achieve their objective" :
                    "The " + forces + " failed to complete their objective. Any surviving fleets are retreating in disarray.";
            case SOURCE_MARKET_LOST -> isUpdate ? "The " + raid + "'s colony no longer exists" :
                    "The " + raid + " was cancelled as the " + this.source.getName() + " colony no longer exists. Any deployed fleets are returning to their port of origin";
            case TARGET_MARKET_LOST -> isUpdate ? "The " + raid + "'s target colony no longer exists" :
                    "The " + raid + " was cancelled as the " + this.target.getName() + " colony no longer exists. Any deployed fleets are returning to their port of origin";
            case NO_LONGER_HOSTILE -> isUpdate ? "The " + raid + "'s faction is no longer hostile with the target" :
                    "The " + raid + " was cancelled as " + this.faction.getDisplayNameWithArticle() + " is no longer hostile with " + this.targetFaction.getDisplayNameWithArticle() + ". Any deployed fleets are returning to their port of origin";
            case ABORTED_IN_PLANNING -> isUpdate ? "The " + raid + " was cancelled during the planning stage." :
                    "The " + raid + " was cancelled during the planning stage and will no longer happen.";
            case SUCCEEDED -> isUpdate ? "The " + forces + " successfully returned with an artifact" :
                    "The " + raid + " was successful and the acquired artifact is now in the hands of " + this.faction.getDisplayNameWithArticle();
        };
    }

    public String getRaidNoun() {
        return "acquisition";
    }

    public String getForcesNoun() {
        return getRaidNoun() + " forces";
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if ("endEvent".equals(action)) {
            setOutcome(Outcome.ARTIFACT_LOST);
            forceFail(true);
            sendUpdateIfPlayerHasIntel(UPDATE_FAILED, dialog.getTextPanel());
            return true;
        } else if ("giveArtifact".equals(action)) {
            SpecialItemData specialItemData = new SpecialItemData(this.artifact.getId(), this.artifact.getParams());
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
                combat,
                freighter,
                tanker,
                transport,
                0f,
                0f,
                0f
        );

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

        params.timestamp = route.getTimestamp();
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);
        fleet.getMemoryWithoutUpdate().set(FLEET_KEY, true);
        fleet.getMemoryWithoutUpdate().set(EVENT_KEY, this);

        if (isPirate) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        RaidStage stage = this.stages.get(this.currentStage);
        setFleetMemoryAtStage(fleet, stage);

        fleet.setName("Grand Acquisitions Fleet");
        fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
        if (extra.fp <= getFPSmall()) {
            fleet.setName("Minor Acquisitions Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);
        } else if (extra.fp <= getFPMedium()) {
            fleet.setName("Major Acquisitions Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_CAPTAIN);
        }
        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);

        return fleet;
    }

    public void setFleetMemoryAtStage(CampaignFleetAPI fleet, RaidStage stage) {
        if (stage instanceof GenericReturnStage) {
            fleet.setNoAutoDespawn(true);
            if (!this.artifactGiven) {
                fleet.getMemoryWithoutUpdate().set(HAS_ARTIFACT_KEY, true);
                Misc.makeImportant(fleet, HAS_ARTIFACT_REASON);
                Misc.addDefeatTrigger(fleet, FLEET_DEFEAT_TRIGGER);
                this.artifactGiven = true;
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
        this.artifactGiven = false;
    }

    public Outcome getOutcome() {
        return this.outcome;
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
            if (raidStage instanceof GenericOrganizeStage || raidStage instanceof GenericAssembleStage) {
                BaseHubMission.addStandardMarketDesc("Making preparations in orbit around", this.source, info, opad);
            } else if (raidStage instanceof GenericTravelStage && !(raidStage instanceof GenericReturnStage)) {
                info.addPara("Travelling to the " + getSystem().getNameWithLowercaseType() + ".", opad);
            } else if (raidStage instanceof AcquisitionActionStageV2) {
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
        if (fp < 300f) {
            fp = 300f;
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

    public float getBonusStrengthPerFleet() {
        boolean isPirate = getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);
        float bonusFP = 120f;
        if (isPirate) {
            bonusFP = 70f;
        }
        return bonusFP;
    }

    public enum Outcome {
        ARTIFACT_LOST,
        ASSEMBLING_DISRUPTED,
        NOT_ENOUGH_MADE_IT,
        FAILED,

        SOURCE_MARKET_LOST,
        TARGET_MARKET_LOST,
        NO_LONGER_HOSTILE,
        ABORTED_IN_PLANNING,

        SUCCEEDED;

        public boolean isFailed() {
            return this == ARTIFACT_LOST || this == ASSEMBLING_DISRUPTED || this == NOT_ENOUGH_MADE_IT || this == FAILED || isCancelled();
        }

        public boolean isCancelled() {
            return this == SOURCE_MARKET_LOST || this == TARGET_MARKET_LOST || this == NO_LONGER_HOSTILE || this == ABORTED_IN_PLANNING;
        }

        public boolean isSucceeded() {
            return this == SUCCEEDED;
        }
    }
}
