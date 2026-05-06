package sectorexpansionpack.intel.acquisition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AcquisitionRaidIntel extends RaidIntel {
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

        float neededFP = WarSimScript.getEnemyStrength(getFaction().getId(), this.target.getStarSystem());
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
        super.createSmallDescription(info, width, height);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        super.createIntelInfo(info, mode);
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
