package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HTPoints;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.events.ht.HTAnomalyResearchFactor;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Random;

// TODO: Add hyperspace topography event progress when mission ends successfully
public class HyperspaceAnomalyResearchMission extends HubMissionWithBarEvent {
    public static final String PROGRESS_STEP_UPDATE = "progress_step_update";
    public static Logger log = Global.getLogger(HyperspaceAnomalyResearchMission.class);
    protected IntervalUtil timer = new IntervalUtil(0.9f, 1.1f);
    protected List<CustomCampaignEntityAPI> ghostsCache;
    protected float currProgress;
    protected float maxProgress;
    protected int lastUpdateStep = -1;
    protected boolean isBarEvent;

    public HyperspaceAnomalyResearchMission() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            setGiverRank(pickOne(Ranks.CITIZEN, Ranks.ARISTOCRAT));
            setGiverPost(pickOne(Ranks.POST_SCIENTIST, Ranks.POST_ACADEMICIAN));
            setGiverImportance(pickImportance());
            findOrCreateGiver(createdAt, true, false);
        }

        if (!setPersonMissionRef(getPerson(), "$sep_harm_ref")) {
            log.info("Failed to find or create mission giver");
            return false;
        }

        this.isBarEvent = barEvent;
        // Number of days needed to get max progress
        this.maxProgress = genRoundNumber(12, 16);

        makeImportant(getPerson(), "$sep_harm_returnPerson", Stage.DELIVER_DATA);

        setStartingStage(Stage.GATHER_DATA);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_harm_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.DELIVER_DATA, Stage.FAILED_DECIV, getPerson().getMarket());
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, getPerson().getMarket());

        setCreditReward(CreditReward.HIGH);

        return true;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        float days = Misc.getDays(amount);

        if (this.currProgress >= this.maxProgress && this.currentStage == Stage.GATHER_DATA) {
            setCurrentStage(Stage.DELIVER_DATA, null, null);
        }

        // Update the player in increments of 20%
        int currUpdateStep = (int) (this.currProgress / (this.maxProgress * 0.2f));
        if (currUpdateStep > this.lastUpdateStep) {
            this.lastUpdateStep = currUpdateStep;
            // Don't send update at 0% and 100%
            if (currUpdateStep > 0 && currUpdateStep < 5) {
                sendUpdateIfPlayerHasIntel(PROGRESS_STEP_UPDATE, true);
            }
        }

        if (!Global.getSector().getPlayerFleet().isInHyperspace()) {
            return;
        }

        this.timer.advance(days);
        if (this.timer.intervalElapsed()) {
            CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
            if (pf == null) {
                return;
            }
            this.ghostsCache = Utils.getNearbyEntitiesWithType(pf, Entities.SENSOR_GHOST, 2000);
        }

        if (this.ghostsCache != null && !this.ghostsCache.isEmpty()) {
            this.currProgress += days;
            this.currProgress = Math.min(this.currProgress, this.maxProgress);
        }
    }

    @Override
    public String getBaseName() {
        return "Hyperspace Anomaly Research";
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        HyperspaceTopographyEventIntel intel = HyperspaceTopographyEventIntel.get();
        intel.addFactor(new HTAnomalyResearchFactor(genRoundNumber(HTPoints.HIGH_MIN, HTPoints.HIGH_MAX)), dialog);
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_harm_dataInDays", getDays(this.maxProgress));
        set("$sep_harm_reward", Misc.getDGSCredits(getCreditsReward()));
        set("$sep_harm_isBarEvent", this.isBarEvent);
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();

        if (this.currentStage == Stage.GATHER_DATA) {
            if (getListInfoParam() == PROGRESS_STEP_UPDATE) {
                info.addPara("Research Progress is now at %s", 0f, tc, h, getProgressPercent() + "%");
            } else {
                info.addPara("Gather data from hyperspace anomalies.", pad, tc, h, getProgressPercent() + "%");
                info.addPara("Research Progress: %s", 0f, tc, h, getProgressPercent() + "%");
            }
            return true;
        } else if (this.currentStage == Stage.DELIVER_DATA) {
            info.addPara("Deliver the completed hyperspace anomaly data to %s at %s, in the %s.", pad, tc, h,
                    getPerson().getName().getFullName(), getPerson().getMarket().getName(),
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort());
            return true;
        }

        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float oPad = 10f;
        Color h = Misc.getHighlightColor();

        if (this.currentStage == Stage.GATHER_DATA) {
            info.addPara("Gather data from hyperspace anomalies.", oPad, h, getProgressPercent() + "%");
            bullet(info);
            info.addPara("Research Progress: %s", oPad, h, getProgressPercent() + "%");
            unindent(info);
            info.addPara("According to the researcher, hyperspace anomalies often occur near the fringes of " +
                    "the sector and often appear near high-energy wave, like those emitted by sensor " +
                    "bursts.", oPad, h, getProgressPercent() + "%");
        } else if (this.currentStage == Stage.DELIVER_DATA) {
            info.addPara("Deliver the completed hyperspace anomaly data to %s at %s, in the %s.", oPad,
                    new Color[]{h, h, h},
                    getPerson().getName().getFullName(), getPerson().getMarket().getName(),
                    getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort());
        }
    }

    public int getProgressPercent() {
        return Math.round(getProgress() * 100f);
    }

    public float getProgress() {
        return Math.min(this.currProgress / this.maxProgress, 1f);
    }

    public enum Stage {
        GATHER_DATA,
        DELIVER_DATA,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }
}
