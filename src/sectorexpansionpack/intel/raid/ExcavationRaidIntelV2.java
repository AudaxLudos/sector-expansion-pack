package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SEPHiddenItemSpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class ExcavationRaidIntelV2 extends GenericExpeditionIntel {
    public static final float WRECK_CHANCE = 0.5f;
    public static final String SOURCE_KEY = "$sep_eri_source";
    public static final String TARGET_KEY = "$sep_eri_target";
    public static final String EVENT_KEY = "$sep_eri_eventRef";
    public static final String FLEET_KEY = "$sep_eri_fleet";
    public static final String FLEET_DEFEAT_TRIGGER = "SEPERIFleetDefeated";
    public static final String HAS_ARTIFACT_KEY = "$sep_eri_hasArtifact";
    public static final String HAS_ARTIFACT_REASON = "sep_eri";
    public static final Logger log = Global.getLogger(ExcavationRaidIntelV2.class);
    public static final Object RETURNED_UPDATE = new Object();

    protected final MarketAPI source;
    protected final SectorEntityToken target;
    protected final SpecialItemSpecAPI artifact;
    protected final Random random;
    protected float leakChance = 0.3f;
    protected boolean leaked = false;
    protected boolean artifactGiven = false;

    public ExcavationRaidIntelV2(MarketAPI source, SectorEntityToken target, SpecialItemSpecAPI artifact) {
        super(target.getStarSystem(), source.getFaction(), null);

        this.defenderStr = getLargeSize(false);
        this.source = source;
        this.target = target;
        this.artifact = artifact;
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
        Misc.setSalvageSpecial(this.target, new SEPHiddenItemSpecial.HiddenSpecialItemSpecialData(this.artifact.getId()));

        Global.getSector().getIntelManager().queueIntel(this);

        log.info(String.format("Starting %s excavation at %s in the %s, targeting %s in the %s",
                getFaction().getDisplayName(),
                this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getName(), getSystem().getNameWithLowercaseTypeShort()));
    }

    @Override
    protected boolean checkFailureConditions(RaidStage stage) {
        if (!this.source.isInEconomy()) {
            setOutcome(Outcome.SOURCE_MARKET_LOST);
        } else {
            if (this.artifactGiven && getActionStage().getStatus() == RaidStageStatus.SUCCESS && stage instanceof BaseRaidStage raidStage) {
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
        }

        return this.outcome != null && this.outcome.isFailed();
    }

    @Override
    protected void succeededAtStage(RaidStage stage) {
        setFleetsMemoryAtStage(getStageIndex(stage), true);
        if (stage instanceof ActionStage) {
            Misc.makeUnimportant(this.target, HAS_ARTIFACT_REASON);
            this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
            this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
            this.target.getMemoryWithoutUpdate().unset(MemFlags.SALVAGE_SPECIAL_DATA);
        } else if (stage instanceof GenericReturnStage stage1) {
            SpecialItemData data = new SpecialItemData(this.artifact.getId(), null);
            Utils.findMarketToInstallSpecialItem(new EntityFinderMission(), getFaction().getId(), this.source, data, log);
            setOutcome(Outcome.SUCCEEDED);
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes()); // Order fleets to return home and despawn
            endAfterDelay();
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(RETURNED_UPDATE, false);
            }
        }

        if (!this.leaked && !(stage instanceof GenericReturnStage)) {
            if (this.random.nextFloat() < this.leakChance) {
                this.leaked = true;

                if (this.source.getStarSystem() != null) {
                    new ExcavationLeakedIntel(this.source, this.target, this.artifact, stage, this);
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
    protected void addBasicDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addImage(getFaction().getLogo(), width, 128, opad);

        info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefixAOrAn()) + " %s operation to recover an " +
                        "artifact found in the " + getSystem().getNameWithLowercaseTypeShort() + ".", opad,
                getFaction().getBaseUIColor(), getFaction().getPersonNamePrefix());
    }

    @Override
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
        if (this.outcome == Outcome.FAILED_OTHER1) {
            return isUpdate ? "The " + forces + " failed to keep the artifact safe" :
                    "The " + forces + " failed to return with the artifact. Any surviving fleets are retreating in disarray.";
        }

        return super.getOutcomeDescription(isUpdate);
    }

    @Override
    public String getRaidNoun() {
        return "excavation";
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if ("endEvent".equals(action)) {
            setOutcome(Outcome.FAILED_OTHER1);
            forceFail(false);
            sendUpdateIfPlayerHasIntel(UPDATE_FAILED, dialog.getTextPanel());
            return true;
        } else if ("giveArtifact".equals(action)) {
            SpecialItemData specialItemData = new SpecialItemData(this.artifact.getId(), null);
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
}
