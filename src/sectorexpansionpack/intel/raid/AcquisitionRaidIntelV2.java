package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AcquisitionRaidIntelV2 extends GenericExpeditionIntel {
    public static final String SOURCE_KEY = "$sep_ari_source";
    public static final String TARGET_KEY = "$sep_ari_target";
    public static final String EVENT_KEY = "$sep_ari_eventRef";
    public static final String FLEET_KEY = "$sep_ari_fleet";
    public static final String FLEET_DEFEAT_TRIGGER = "SEPARIFleetDefeated";
    public static final String HAS_ARTIFACT_KEY = "$sep_ari_hasArtifact";
    public static final String HAS_ARTIFACT_REASON = "sep_ari";
    public static final Logger log = Global.getLogger(AcquisitionRaidIntelV2.class);
    public static final Object RETURNED_UPDATE = new Object();

    protected final MarketAPI target;
    protected final FactionAPI targetFaction;
    protected final SpecialItemSpecAPI artifact;
    protected final Random random;
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

    @Override
    protected boolean checkFailureConditions(RaidStage stage) {
        if (!this.source.isInEconomy()) {
            setOutcome(Outcome.SOURCE_MARKET_LOST);
        } else if (!this.target.isInEconomy() && getActionStage() != null && getActionStage().getStatus() == RaidStageStatus.ONGOING) {
            setOutcome(Outcome.CANCELLED_OTHER1);
        } else if (!this.faction.isHostileTo(this.targetFaction) && getActionStage() != null && getActionStage().getStatus() == RaidStageStatus.ONGOING) {
            setOutcome(Outcome.CANCELLED_OTHER2);
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
                setOutcome(Outcome.FAILED_OTHER1);
            }
        }

        return this.outcome != null && this.outcome.isFailed();
    }

    @Override
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
    protected void notifyEnding() {
        super.notifyEnding();

        getFaction().getMemoryWithoutUpdate().unset(SOURCE_KEY);
        this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
        this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
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

    @Override
    public void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
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

    @Override
    public void addBulletPointsBeforeUpdate(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        if (this.outcome == null && mode != ListInfoMode.IN_DESC) {
            info.addPara("Target: " + this.targetFaction.getDisplayName(), initPad, tc, this.targetFaction.getBaseUIColor(), this.targetFaction.getDisplayName());
            initPad = 0f;
        }
    }

    @Override
    public void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        if (RETURNED_UPDATE.equals(param)) {
            info.addPara("Docking safely " + this.source.getOnOrAt() + " " + this.source.getName() + " with an artifact", tc, initPad);
        } else {
            super.addUpdateBulletPoints(info, tc, param, mode, initPad);
        }
    }

    @Override
    protected String getOutcomeDescription(boolean isUpdate) {
        String forces = getForcesNoun();
        String raid = getRaidNoun();
        return switch (this.outcome) {
            case FAILED_OTHER1 -> isUpdate ? "The " + forces + " failed to keep the artifact safe" :
                    "The " + forces + " failed to return with the artifact. Any surviving fleets are retreating in disarray.";
            case CANCELLED_OTHER1 -> isUpdate ? "The " + raid + "'s target colony no longer exists" :
                    "The " + raid + " was cancelled as the " + this.target.getName() + " colony no longer exists. Any deployed fleets are returning to their port of origin";
            case CANCELLED_OTHER2 -> isUpdate ? "The " + raid + "'s faction is no longer hostile with the target" :
                    "The " + raid + " was cancelled as " + this.faction.getDisplayNameWithArticle() + " is no longer hostile with " + this.targetFaction.getDisplayNameWithArticle() + ". Any deployed fleets are returning to their port of origin";
            default -> super.getOutcomeDescription(isUpdate);
        };
    }

    @Override
    public String getRaidNoun() {
        return "acquisition";
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if ("endEvent".equals(action)) {
            setOutcome(Outcome.FAILED_OTHER1);
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
    public void configureFleet(CampaignFleetAPI fleet, float fp) {
        super.configureFleet(fleet, fp);
        fleet.getMemoryWithoutUpdate().set(FLEET_KEY, true);
        fleet.getMemoryWithoutUpdate().set(EVENT_KEY, this);
    }

    @Override
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

    @Override
    protected void clearFleetMemory(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().unset(FLEET_KEY);
        fleet.getMemoryWithoutUpdate().unset(EVENT_KEY);
        fleet.getMemoryWithoutUpdate().unset(HAS_ARTIFACT_KEY);
        Misc.makeUnimportant(fleet, HAS_ARTIFACT_REASON);
        Misc.removeDefeatTrigger(fleet, FLEET_DEFEAT_TRIGGER);
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {
        if (!Objects.equals(route.getSource(), getRouteSourceId())) {
            return;
        }
        this.artifactGiven = false;
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
}
