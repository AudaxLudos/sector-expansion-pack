package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Random;
import java.util.Set;

public abstract class GenericExpeditionIntel extends RaidIntel implements GenericOrganizeStage.ShowStageInfoDelegate, GenericAssembleStage.AssembleStageDelegate {
    protected Outcome outcome;
    protected MarketAPI source;

    public GenericExpeditionIntel(StarSystemAPI system, FactionAPI faction, RaidDelegate delegate) {
        super(system, faction, delegate);
    }

    protected float getPrepDays(float fp) {
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
        return false;
    }

    protected void succeededAtStage(RaidStage stage) {
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
        if (stage instanceof BaseRaidStage raidStage) {
            raidStage.giveReturnOrdersToStragglers(raidStage.getRoutes());
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
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Leaks");
        return tags;
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
        info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefixAOrAn()) + " %s expedition against the " +
                        getSystem().getNameWithLowercaseTypeShort() + ".", opad,
                getFaction().getBaseUIColor(), getFaction().getPersonNamePrefix());
    }

    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height) {
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

        initPad = addBulletPointsBeforeUpdate(info, tc, param, mode, initPad);

        if (!isUpdate) {
            addNonUpdateBulletPoints(info, tc, null, mode, initPad);
        } else {
            addUpdateBulletPoints(info, tc, param, mode, initPad);
        }
        unindent(info);
    }

    public float addBulletPointsBeforeUpdate(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        return initPad;
    }

    public void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
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

    public void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
        if (ENTERED_SYSTEM_UPDATE.equals(param)) {
            info.addPara("Arrived at " + getSystem().getNameWithLowercaseTypeShort(), tc, initPad);
        } else if (UPDATE_RETURNING.equals(param)) {
            info.addPara("Returning to " + this.source.getStarSystem().getNameWithLowercaseTypeShort(), tc, initPad);
        } else if (UPDATE_FAILED.equals(param)) {
            info.addPara(getOutcomeDescription(true), tc, initPad);
        }
    }

    protected String getOutcomeDescription(boolean isUpdate) {
        String forces = getForcesNoun();
        String raid = getRaidNoun();
        return switch (this.outcome) {
            // Failed outcomes
            case ASSEMBLING_DISRUPTED -> isUpdate ? "The " + forces + " failed to fully assemble" :
                    "The " + forces + " failed to fully assemble. Any deployed fleets are retreating in disarray.";
            case NOT_ENOUGH_MADE_IT -> isUpdate ? "The " + forces + " failed to reach their target" :
                    "The " + forces + " failed to reach their objective. Any remaining fleets are retreating in disarray.";
            case FAILED -> isUpdate ? "The " + forces + " failed to achieve their objective" :
                    "The " + forces + " failed to complete their objective. Any surviving fleets are retreating in disarray.";
            // Cancelled outcomes
            case SOURCE_MARKET_LOST -> isUpdate ? "The " + raid + "'s colony no longer exists" :
                    "The " + raid + " was cancelled as the " + this.source.getName() + " colony no longer exists. Any deployed fleets are returning to their port of origin";
            case ABORTED_IN_PLANNING -> isUpdate ? "The " + raid + " was cancelled during the planning stage." :
                    "The " + raid + " was cancelled during the planning stage and will no longer happen.";
            case CANCELLED -> isUpdate ? "The " + raid + " was cancelled for unknown reasons." :
                    "The " + raid + " was cancelled for unknown reasons and will no longer happen.";
            // Succeeded outcomes
            case SUCCEEDED -> isUpdate ? "The " + forces + " successfully returned with an artifact" :
                    "The " + raid + " was successful and the acquired artifact is now in the hands of " + this.faction.getDisplayNameWithArticle();
            case FAILED_OTHER1, FAILED_OTHER2, CANCELLED_OTHER1, CANCELLED_OTHER2 -> null;
        };
    }

    public String getRaidNoun() {
        return "expedition";
    }

    public String getForcesNoun() {
        return getRaidNoun() + " forces";
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
        FleetParamsV3 params = new FleetParamsV3();
        params.locInHyper = locInHyper;
        params.factionId = factionId;
        params.random = random;
        preConfigureFleet(params, route);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        configureFleet(fleet, route.getExtra().fp);

        return fleet;
    }

    public void preConfigureFleet(FleetParamsV3 params, RouteManager.RouteData route) {
        RouteManager.OptionalFleetData extra = route.getExtra();

        float combatPts = extra.fp;
        float tankerPts = extra.fp * (0.1f + params.random.nextFloat() * 0.05f);
        float transportPts = extra.fp * (0.1f + params.random.nextFloat() * 0.05f);
        combatPts -= tankerPts;
        combatPts -= transportPts;

        params.fleetType = extra.fleetType;
        params.combatPts = combatPts;
        params.freighterPts = 0f;
        params.tankerPts = tankerPts;
        params.transportPts = transportPts;
        params.linerPts = 0f;
        params.utilityPts = 0f;
        params.qualityMod = 0f;

        params.ignoreMarketFleetSizeMult = true;
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;
        params.qualityOverride = 1f;
        FactionDoctrineAPI doctrineOverride = this.faction.getDoctrine().clone();
        if (!getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            doctrineOverride.setOfficerQuality(3);
        }
        doctrineOverride.setShipQuality(3);
        doctrineOverride.setNumShips(3);
        params.doctrineOverride = doctrineOverride;

        params.timestamp = route.getTimestamp();
    }

    public void configureFleet(CampaignFleetAPI fleet, float fp) {
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);

        if (getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);

        RaidStage stage = this.stages.get(this.currentStage);
        setFleetMemoryAtStage(fleet, stage);

        String raid = getRaidNoun();

        fleet.setName("Grand " + raid + " Fleet");
        fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
        if (fp <= getFPSmall()) {
            fleet.setName("Minor " + raid + " Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);
        } else if (fp <= getFPMedium()) {
            fleet.setName("Major " + raid + " Fleet");
            fleet.getCommander().setRankId(Ranks.SPACE_CAPTAIN);
        }
        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
    }

    public void setFleetMemoryAtStage(CampaignFleetAPI fleet, RaidStage stage) {
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
                    clearFleetMemory(fleet);
                    continue;
                }
                currStage = this.stages.get(index);
            }

            setFleetMemoryAtStage(fleet, currStage);
        }
    }

    protected void clearFleetMemory(CampaignFleetAPI fleet) {
    }

    public Outcome getOutcome() {
        return this.outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info, RaidStage raidStage, RaidStageStatus status) {
    }

    @Override
    public float getAdjustedFleetStrength(float fp) {
        return fp + getBonusStrengthPerFleet();
    }

    public float getBonusStrengthPerFleet() {
        boolean isPirate = getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);
        float bonusFP = 120f;
        if (isPirate) {
            bonusFP = 70f;
        }
        return bonusFP;
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
        ASSEMBLING_DISRUPTED,
        NOT_ENOUGH_MADE_IT,
        FAILED,
        FAILED_OTHER1,
        FAILED_OTHER2,

        SOURCE_MARKET_LOST,
        ABORTED_IN_PLANNING,
        CANCELLED,
        CANCELLED_OTHER1,
        CANCELLED_OTHER2,

        SUCCEEDED;

        public boolean isFailed() {
            return this == ASSEMBLING_DISRUPTED || this == NOT_ENOUGH_MADE_IT || this == FAILED || this == FAILED_OTHER1 || this == FAILED_OTHER2 || isCancelled();
        }

        public boolean isCancelled() {
            return this == SOURCE_MARKET_LOST || this == ABORTED_IN_PLANNING || this == CANCELLED || this == CANCELLED_OTHER1 || this == CANCELLED_OTHER2;
        }

        public boolean isSucceeded() {
            return this == SUCCEEDED;
        }
    }
}
