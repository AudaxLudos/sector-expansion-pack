package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
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

public class AcquisitionRaidIntel extends RaidIntel {
    public static final String FLEET_KEY = "$sep_ari_fleet";
    public static final String EVENT_KEY = "$sep_ari_eventRef";
    public static final String SOURCE_KEY = "$sep_ari_source";
    public static final String TARGET_KEY = "$sep_ari_target";
    public static final Logger log = Global.getLogger(AcquisitionRaidIntel.class);
    public static Object RETURNED_SAFELY = new Object();
    protected AcquisitionOutcome outcome;
    protected MarketAPI source;
    protected MarketAPI target;
    protected SpecialItemSpecAPI specialItem;
    protected Random random;

    public AcquisitionRaidIntel(MarketAPI source, MarketAPI target, SpecialItemSpecAPI specialItem) {
        super(target.getStarSystem(), source.getFaction(), null);

        this.source = source;
        this.target = target;
        this.specialItem = specialItem;
        this.random = new Random();

        float neededFP = this.defenderStr;
        float desireMult = getSpecialItemsDesireMult(getFaction().getId());
        float randomMult = 0.6f + this.random.nextFloat() * 0.6f;
        float approxNumFleets = neededFP / getLargeFleetSize();
        float bonusStrengthPerFleet = getBonusStrengthPerFleet();
        float baseFP = (neededFP - (approxNumFleets * bonusStrengthPerFleet)) * desireMult * randomMult;

        addStage(new AcquisitionOrganizeStage(this, this.source, getPrepDays(baseFP)));

        AssembleStage assembleStage = new AcquisitionAssembleStage(this, this.source.getPrimaryEntity());
        assembleStage.addSource(this.source);
        assembleStage.setSpawnFP(baseFP);
        assembleStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(assembleStage);

        SectorEntityToken raidJumpPoint = RouteLocationCalculator.findJumpPointToUse(getFaction(), target.getPrimaryEntity());
        if (raidJumpPoint == null) {
            endImmediately();
            return;
        }

        TravelStage travelStage = new AcquisitionTravelStage(this, this.source.getPrimaryEntity(), raidJumpPoint, true);
        travelStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(travelStage);

        ActionStage actionStage = new AcquisitionActionStage(this, this.target, getRaidDays(baseFP));
        actionStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(actionStage);

        TravelStage returnStage = new AcquisitionReturnStage(this, raidJumpPoint, this.source.getPrimaryEntity(), true);
        returnStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(returnStage);

        // Mark source faction and targets to prevent multiple acquisition events
        //  selecting the same targets and/or sources
        this.source.getFaction().getMemoryWithoutUpdate().set(SOURCE_KEY, true);
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this); // May not needs this key

        Global.getSector().getIntelManager().queueIntel(this);

        log.info(String.format("Starting %s acquisition at %s in the %s, targeting %s in the %s",
                getFaction().getDisplayName(),
                this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort(),
                this.target.getName(), getSystem().getNameWithLowercaseTypeShort()));
    }

    /**
     * Calculates how much a source faction wants a colony item
     *
     * @return a float number between 0 and 1
     */
    public static float getSpecialItemsDesireMult(String factionId) {
        int totalColonyItemsUsed = 0;
        Map<String, Integer> itemsUsedPerFaction = new HashMap<String, Integer>(); // will include hidden or dead factions
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            int itemsUsedByFaction = 0;
            for (MarketAPI market : Misc.getFactionMarkets(faction)) {
                for (Industry industry : market.getIndustries()) {
                    if (industry.getSpecialItem() != null) {
                        totalColonyItemsUsed += 1;
                        itemsUsedByFaction += 1;
                    }
                }
            }
            itemsUsedPerFaction.put(faction.getId(), itemsUsedByFaction);
        }

        float meanAllFactions = (float) totalColonyItemsUsed / itemsUsedPerFaction.size();
        int sourceItemsUsed = itemsUsedPerFaction.get(factionId);
        // float itemUseFraction = (float) sourceItemsUsed / totalColonyItemsUsed;
        float mult = 1f;
        if (sourceItemsUsed > 0) {
            mult = meanAllFactions / sourceItemsUsed;
        }
        // mult += itemUseFraction;

        if (mult < 0.5f) {
            mult = 0.5f;
        } else if (mult < 0.75f) {
            mult = 0.75f;
        }

        return mult;
    }

    protected float getPrepDays(float fp) {
        if (Global.getSettings().isDevMode()) {
            return 7f;
        }
        return 7f + (fp / getLargeFleetSize());
    }

    protected float getRaidDays(float fp) {
        if (Global.getSettings().isDevMode()) {
            return 7f;
        }
        return 14f + (14f * (fp / this.defenderStr));
    }

    protected float getFleetFPAbortFraction() {
        return 0.33f;
    }

    @Override
    protected void advanceImpl(float amount) {
        if (this.outcome != null) {
            return;
        }

        if (!this.source.isInEconomy()) {
            setOutcome(AcquisitionOutcome.SOURCE_MARKET_LOST);
        } else if (!this.target.isInEconomy() && getActionStage() != null && getActionStage().getStatus() == RaidStageStatus.ONGOING) {
            setOutcome(AcquisitionOutcome.TARGET_MARKET_LOST);
        } else if (!this.faction.isHostileTo(this.target.getFaction()) && getActionStage() != null && getActionStage().getStatus() == RaidStageStatus.ONGOING) {
            setOutcome(AcquisitionOutcome.NO_LONGER_HOSTILE);
        }

        if (this.outcome != null) {
            forceFail(true);
            return;
        }

        RaidStage stage = this.stages.get(this.currentStage);

        stage.advance(amount);

        RaidStageStatus status = stage.getStatus();
        if (status == RaidStageStatus.SUCCESS) {
            successAtStage(stage);
            this.currentStage++;
            setExtraDays(Math.max(0, getExtraDays() - stage.getExtraDaysUsed()));
            if (this.currentStage < this.stages.size()) {
                this.stages.get(this.currentStage).notifyStarted();
            }
        } else if (status == RaidStageStatus.FAILURE) {
            failedAtStage(stage);
            this.failStage = this.currentStage;
            endAfterDelay();
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(UPDATE_FAILED, false);
            }
        }
    }

    protected void successAtStage(RaidStage stage) {
        if (stage instanceof AcquisitionActionStage) {
            SpecialItemData data = new SpecialItemData(this.specialItem.getId(), this.specialItem.getParams());
            for (Industry ind : this.target.getIndustries()) {
                if (ind.getSpecialItem() != null && Objects.equals(ind.getSpecialItem().getId(), data.getId())) {
                    log.info(String.format("Removing %s from %s at %s in the %s due to an incursion",
                            this.specialItem.getName(), ind.getCurrentName(), this.target.getName(),
                            this.target.getStarSystem().getNameWithLowercaseTypeShort()));
                    ind.setSpecialItem(null);
                    break;
                }
            }
            log.info(String.format("The %s incursion is successful at %s in the %s",
                    this.faction.getDisplayName(), this.target.getName(),
                    this.target.getStarSystem().getNameWithLowercaseTypeShort()));
        } else if (stage instanceof AcquisitionReturnStage stage1) {
            SpecialItemData data = new SpecialItemData(this.specialItem.getId(), this.specialItem.getParams());
            Utils.findMarketToInstallSpecialItem(new EntityFinderMission(), getFaction().getId(), this.source, data, log);
            setOutcome(AcquisitionOutcome.SUCCEEDED);
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes()); // Order fleets to return home and despawn
            endAfterDelay();
            if (shouldSendUpdate()) {
                sendUpdateIfPlayerHasIntel(RETURNED_SAFELY, false);
            }
        }
    }

    @Override
    protected void failedAtStage(RaidStage stage) {
        if (this.outcome == null) {
            if (stage instanceof AcquisitionOrganizeStage) {
                setOutcome(AcquisitionOutcome.ABORTED_IN_PLANNING);
            } else if (stage instanceof AcquisitionAssembleStage) {
                setOutcome(AcquisitionOutcome.TASK_FORCE_DEFEATED);
            } else if (stage instanceof AcquisitionTravelStage) {
                setOutcome(AcquisitionOutcome.NOT_ENOUGH_MADE_IT);
            } else if (stage instanceof AcquisitionActionStage) {
                setOutcome(AcquisitionOutcome.FAILED);
            } else if (stage instanceof AcquisitionReturnStage) {
                setOutcome(AcquisitionOutcome.NOT_ENOUGH_MADE_IT);
            }
        }
        if (stage instanceof BaseRaidStage stage1) {
            stage1.giveReturnOrdersToStragglers(stage1.getRoutes()); // Order fleets to return home and despawn
        }
    }

    public void addMemoryFlagsToFleets(List<RouteManager.RouteData> routes, Map<String, Object> memories) {
        for (RouteManager.RouteData route : routes) {
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet != null) {
                for (Map.Entry<String, Object> entry : memories.entrySet()) {
                    fleet.getMemoryWithoutUpdate().set(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @Override
    public String getName() {
        String base = Misc.ucFirst(getFaction().getPersonNamePrefix()) + " " + Misc.ucFirst(getRaidNoun());
        if ((this.outcome != null && this.outcome.isSucceeded()) || (this.getActionStage().getStatus() == RaidStageStatus.SUCCESS)) {
            return base + " - Successful";
        } else if (this.outcome != null && this.outcome.isCancelled()) {
            return base + " - Cancelled";
        } else if (this.outcome != null && this.outcome.isFailed()) {
            return base + " - Failed";
        }
        return base;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        logDebugInformation();
        addBasicDescription(info, width, height);
        addAssessmentSection(info, width, height);
        addStatusSection(info, width, height);
        // addBulletPoints(info, ListInfoMode.IN_DESC);
        info.addSpacer(10f);
    }

    protected void logDebugInformation() {
        float raidStr = (float) RouteManager.getInstance().getRoutesForSource(getRouteSourceId()).stream()
                .mapToDouble(r -> {
                    if (r.getActiveFleet() != null) {
                        return r.getActiveFleet().getEffectiveStrength();
                    } else {
                        return r.getExtra().getStrengthModifiedByDamage();
                    }
                }).sum();
        float defenderStr = WarSimScript.getEnemyStrength(getFaction(), getSystem()) +
                WarSimScript.getStationStrength(this.target.getFaction(), getSystem(), this.target.getPrimaryEntity());
        float ratio = raidStr / defenderStr;

        log.info(Misc.ucFirst(this.faction.getDisplayName()) + " raid info: ");
        log.info("  Final raid outcome      : " + this.outcome);
        log.info("  Initial enemy strength  : " + this.defenderStr);
        log.info("  Active raid strength    : " + raidStr);
        log.info("  Active enemy strength   : " + defenderStr);
        log.info("  Attacker strength ratio : " + ratio);
        log.info("  Defenders are superior  : " + (ratio < 0.75f));
        log.info("  Defenders are equal     : " + (ratio < 1.25f));
        log.info("  Defenders are outmatched: " + (ratio > 1f));
    }

    protected void addBasicDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addImage(getFaction().getLogo(), width, 128, opad);

        info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefixAOrAn()) + " %s operation to take a " +
                        "special item found in the " + getSystem().getNameWithLowercaseTypeShort() + ".", opad,
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
        String raidNoun = getRaidNoun();
        String forcesNoun = getForcesNoun();

        AssembleStage assembleStage = getAssembleStage();
        ActionStage actionStage = getActionStage();

        int numFleets = (int) getNumFleets();
        String fleetNoun = "fleet";
        if (numFleets > 1) {
            fleetNoun = "fleets";
        }
        float raidStr = assembleStage.getOrigSpawnFP();
        raidStr += (numFleets * getBonusStrengthPerFleet());
        String strDesc = Misc.getStrengthDesc(raidStr);

        String defenderHighlight = "";
        Color defenderHighlightColor = h;
        String outcomeText = "";
        boolean potentialDanger = false;

        if (this.outcome == null && actionStage.getStatus() != RaidStageStatus.SUCCESS) {
            float ratio = raidStr / this.defenderStr;
            if (ratio < 0.75f) {
                defenderHighlight = "superior";
                defenderHighlightColor = Misc.getPositiveHighlightColor();
                outcomeText = "the " + raidNoun + " is unlikely to find success";
            } else if (ratio < 1.25f) {
                defenderHighlight = "evenly matched";
                outcomeText = "the outcome of the " + raidNoun + " is uncertain";
                potentialDanger = true;
            } else if (ratio > 1f) {
                defenderHighlight = "outmatched";
                defenderHighlightColor = bad;
                outcomeText = "the " + raidNoun + " is likely to find success";
                potentialDanger = true;
            }
        }

        String assessment = "The " + forcesNoun + " are projected to be " + strDesc + " and likely comprised of " + numFleets + " " + fleetNoun + ".";
        if (!defenderHighlight.isBlank()) {
            assessment += " The defending fleets are " + defenderHighlight + ", and " + outcomeText + ".";
        }

        info.addSectionHeading("Assessment", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);

        LabelAPI label = info.addPara(assessment, opad, faction.getBaseUIColor());
        label.setHighlight(strDesc, numFleets + "", defenderHighlight);
        label.setHighlightColors(faction.getBaseUIColor(), h, defenderHighlightColor);

        if (this.outcome == null && actionStage.getStatus() != RaidStageStatus.SUCCESS) {
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
                    float totalDefense = this.defenderStr + stationStr;
                    if (totalDefense > raidStr * 1.25f) {
                        safe.add(market);
                    } else {
                        unsafe.add(market);
                    }
                }
            }

            if (potentialDanger) {
                if (safe.size() == targets.size()) {
                    info.addPara("However, all colonies should be safe from the " + raidNoun + ", owing to their orbital defenses.", opad);
                } else if (!unsafe.isEmpty()) {
                    String isOrAre = "are";
                    String colonyNoun = "colonies ";
                    if (unsafe.size() == 1) {
                        isOrAre = "is";
                        colonyNoun = "colony ";
                    }
                    String riskTxt = isOrAre + " at risk of losing a used special item:";
                    info.addPara("The following " + colonyNoun + riskTxt, opad, bad, "losing a used special item");
                    FactionAPI f = Global.getSector().getPlayerFaction();
                    addMarketTable(info, f.getBaseUIColor(), f.getDarkUIColor(), f.getBrightUIColor(), unsafe, width, opad);
                }
            }
        }
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
            String status = getOutcomeDescription();
            info.addPara(status, opad);
        }
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        super.addBulletPoints(info, mode); // Replace this with my own implementation
    }

    protected String getOutcomeDescription() {
        String forces = getForcesNoun();
        String raid = getRaidNoun();
        String desc = "The " + raid + " was successful and the command fleet carrying a special item has safely returned";
        switch (this.outcome) {
            // Failure outcomes
            case TASK_FORCE_DEFEATED ->
                    desc = "The" + forces + " were defeated, and any surviving fleets are retreating in disarray.";
            case NOT_ENOUGH_MADE_IT ->
                    desc = "The " + forces + " failed to reach their objective, and any remaining fleets are retreating in disarray.";
            case FAILED ->
                    desc = "The " + forces + " failed to complete their objective, and any remaining fleets are retreating in disarray.";
            // Cancelled outcomes but still failure
            case SOURCE_MARKET_LOST ->
                    desc = "The " + raid + " was cancelled as the " + this.source.getName() + " colony no longer exists.";
            case TARGET_MARKET_LOST ->
                    desc = "The " + raid + " was cancelled as the " + this.target.getName() + " colony no longer exists.";
            case NO_LONGER_HOSTILE ->
                    desc = "The " + raid + " was cancelled as " + this.faction.getDisplayNameWithArticle() +
                            " is no longer hostile with " + this.target.getFaction().getDisplayNameWithArticle() + ".";
            case ABORTED_IN_PLANNING ->
                    desc = "The " + raid + " was cancelled during the planning stage and will not happen.";
            // Succeeded outcomes
            case SUCCEEDED ->
                    desc = "The " + raid + " was successful and the acquired special item is now in the hands of " +
                            this.faction.getDisplayNameWithArticle();
        }
        return desc;
    }

    public String getRaidNoun() {
        return "acquisition";
    }

    public String getForcesNoun() {
        return getRaidNoun() + " forces";
    }

    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
        RouteManager.OptionalFleetData extra = route.getExtra();

        // Command fleet type is set during assemble stage
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
                route == null ? null : route.getQualityOverride(),
                extra.fleetType,
                combat, // combatPts
                freighter, // freighterPts
                tanker, // tankerPts
                transport, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod, won't get used since routes mostly have quality override set
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

        if (route != null) {
            params.timestamp = route.getTimestamp();
        }

        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);

        if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        String postId = Ranks.POST_PATROL_COMMANDER;
        String rankId = Ranks.SPACE_COMMANDER;

        fleet.getCommander().setPostId(postId);
        fleet.getCommander().setRankId(rankId);

        fleet.setName("Grand Acquisitions Fleet");
        fleet.getMemoryWithoutUpdate().set(FLEET_KEY, true);
        fleet.getMemoryWithoutUpdate().set(EVENT_KEY, this);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        // TODO: Add special item to 1 fleet when raid action is successful.

        return fleet;
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        if (this.outcome != null && this.outcome == AcquisitionOutcome.SUCCEEDED) {
            return 14f;
        }
        return 7f;
    }

    public AcquisitionOutcome getOutcome() {
        return this.outcome;
    }

    public void setOutcome(AcquisitionOutcome outcome) {
        this.outcome = outcome;
    }

    public float getLargeFleetSize() {
        float fp = getFaction().getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
        if (fp < 300f) {
            fp = 300f;
        }
        return fp;
    }

    public float getMediumFleetSize() {
        return getLargeFleetSize() / 2f;
    }

    public float getSmallFleetSize() {
        return getMediumFleetSize() / 2f;
    }

    public enum AcquisitionOutcome {
        TASK_FORCE_DEFEATED,
        NOT_ENOUGH_MADE_IT,
        FAILED,

        SOURCE_MARKET_LOST,
        TARGET_MARKET_LOST,
        NO_LONGER_HOSTILE,
        ABORTED_IN_PLANNING,

        SUCCEEDED;

        public boolean isFailed() {
            return this == TASK_FORCE_DEFEATED || this == NOT_ENOUGH_MADE_IT || this == FAILED || isCancelled();
        }

        public boolean isCancelled() {
            return this == SOURCE_MARKET_LOST || this == TARGET_MARKET_LOST || this == NO_LONGER_HOSTILE || this == ABORTED_IN_PLANNING;
        }

        public boolean isSucceeded() {
            return this == SUCCEEDED;
        }
    }
}
