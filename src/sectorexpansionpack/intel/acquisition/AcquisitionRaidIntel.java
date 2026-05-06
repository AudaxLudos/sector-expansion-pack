package sectorexpansionpack.intel.acquisition;

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

import java.awt.*;
import java.util.*;
import java.util.List;

public class AcquisitionRaidIntel extends RaidIntel {
    public static final String FLEET_KEY = "$sep_ari_fleet";
    public static final String EVENT_KEY = "$sep_ari_eventRef";
    public static final String SOURCE_KEY = "$sep_ari_source";
    public static final String TARGET_KEY = "$sep_ari_target";
    public static final Logger log = Global.getLogger(AcquisitionRaidIntel.class);
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

        boolean isSourcePirate = this.faction.getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR);

        float removeFPPerFleet = 120f; // Major factions have around 120 additional fp per fleet
        if (isSourcePirate) {
            removeFPPerFleet = 70f; // Pirate factions have around 70 additional fp per fleet
        }

        float neededFP = this.defenderStr;
        float desireMult = getSpecialItemsDesireMult(getFaction().getId());
        float randomMult = 0.6f + (this.random.nextFloat() * 0.6f);
        float approxNumFleets = neededFP / getLargeFleetSize();
        float baseFP = neededFP - (approxNumFleets * removeFPPerFleet) * desireMult * randomMult;

        addStage(new OrganizeStage(this, this.source, getPrepDays(baseFP)));

        AssembleStage assembleStage = new AssembleStage(this, this.source.getPrimaryEntity());
        assembleStage.setSpawnFP(baseFP);
        assembleStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(assembleStage);

        SectorEntityToken raidJumpPoint = RouteLocationCalculator.findJumpPointToUse(getFaction(), target.getPrimaryEntity());
        if (raidJumpPoint == null) {
            endImmediately();
            return;
        }

        TravelStage travelStage = new TravelStage(this, this.source.getPrimaryEntity(), raidJumpPoint, true);
        travelStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(travelStage);

        ActionStage actionStage = new PirateRaidActionStage(this, this.target.getStarSystem());
        actionStage.setAbortFP(baseFP * getFleetFPAbortFraction());
        addStage(actionStage);

        TravelStage returnStage = new TravelStage(this, raidJumpPoint, this.source.getPrimaryEntity(), true);
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
                this.target.getName(), this.target.getStarSystem().getNameWithLowercaseTypeShort()));
        log.info(String.format("%s acquisition at %s has %s attack strength, enemies at %s has %s defense strength",
                this.faction.getDisplayName(),
                this.source.getStarSystem().getNameWithLowercaseTypeShort(), baseFP,
                this.target.getStarSystem().getNameWithLowercaseTypeShort(), neededFP));
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
        return 7f + (fp / getLargeFleetSize());
    }

    protected float getFleetFPAbortFraction() {
        return 0.33f;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        addBasicDescription(info, width, height);
        addAssessmentSection(info, width, height);
        addStatusSection(info, width, height);
        addBulletPoints(info, ListInfoMode.IN_DESC);
    }

    protected void addBasicDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addImage(getFaction().getLogo(), width, 128, opad);

        info.addPara(Misc.ucFirst(getFaction().getPersonNamePrefixAOrAn()) + " %s operation to take a " +
                        "special item found in the " + getSystem().getNameWithLowercaseTypeShort() + ".", opad,
                getFaction().getBaseUIColor(), getFaction().getPersonNamePrefix());
    }

    protected void addAssessmentSection(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        FactionAPI faction = getFaction();
        String raidNoun = getRaidNoun();
        String forcesNoun = getForcesNoun();

        AssembleStage assembleStage = getAssembleStage();
        ActionStage actionStage = getActionStage();
        MarketAPI source = getFirstSource();

        float raidStr = assembleStage.getOrigSpawnFP();
        String strDesc = Misc.getStrengthDesc(raidStr);
        float numFleets = getNumFleets();
        String fleetNoun = "fleet";
        if (numFleets > 1) {
            fleetNoun = "fleets";
        }

        String defenderHighlight = "";
        Color defenderHighlightColor = h;
        String outcomeText = "";
        boolean potentialDanger = false;

        if (this.outcome == null && actionStage.getStatus() == RaidStageStatus.SUCCESS) {
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
        if (actionStage.getStatus() == RaidStageStatus.SUCCESS) {
            assessment += " The " + forcesNoun + " is likely carrying a special item.";
        }

        info.addSectionHeading("Assessment", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);

        LabelAPI label = info.addPara(assessment, opad, faction.getBaseUIColor());
        label.setHighlight(strDesc, numFleets + "", defenderHighlight);
        label.setHighlightColors(faction.getBaseUIColor(), h, defenderHighlightColor);

        if (this.outcome != null && actionStage.getStatus() == RaidStageStatus.SUCCESS) {
            List<MarketAPI> targets = new ArrayList<>();
            List<MarketAPI> safe = new ArrayList<>();
            List<MarketAPI> unsafe = new ArrayList<>();

            for (MarketAPI market : Misc.getMarketsInLocation(this.target.getStarSystem())) {
                boolean hasSpecialItems = false;
                for (Industry industry : market.getIndustries()) {
                    if (industry.getSpecialItem() != null) {
                        hasSpecialItems = true;
                        break;
                    }
                }
                if (hasSpecialItems) {
                    targets.add(market);
                    float stationStr = WarSimScript.getStationStrength(market.getFaction(), this.target.getStarSystem(), market.getPrimaryEntity());
                    float totalDefense = this.defenderStr + stationStr;
                    if (totalDefense > raidStr * 1.25f) {
                        safe.add(market);
                    } else {
                        unsafe.add(market);
                    }
                }
            }

            if (safe.size() == targets.size()) {
                info.addPara("However, all colonies should be safe from the " + raidNoun + ", owing to their orbital defenses.", opad);
            } else if (potentialDanger && !unsafe.isEmpty()) {
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
                    desc = "The" + forces + " was mostly defeated. Any surviving fleets are retreating in disarray.";
            case NOT_ENOUGH_MADE_IT ->
                    desc = "The" + forces + " retreated before they could conduct any military operations.";
            case FAILED ->
                    desc = "The" + forces + " failed to complete there objective. Any remaining fleets are retreating";
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
            return this == TASK_FORCE_DEFEATED || this == NOT_ENOUGH_MADE_IT || this == FAILED;
        }

        public boolean isCancelled() {
            return this == SOURCE_MARKET_LOST || this == TARGET_MARKET_LOST || this == NO_LONGER_HOSTILE || this == ABORTED_IN_PLANNING;
        }

        public boolean isSucceeded() {
            return this == SUCCEEDED;
        }
    }
}
