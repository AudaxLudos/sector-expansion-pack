package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.missions.hub.EscortFleetAssignmentAI;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FleetEscortMission extends HubMissionWithBarEvent {
    public static final float MISSION_DURATION = 120f;
    public static Logger log = Global.getLogger(FleetEscortMission.class);
    protected CampaignFleetAPI fleet;
    protected SectorEntityToken gotoEntity;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            if (rollProbability(0.5f)) {
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

        requireMarketFaction(getPerson().getFaction().getId());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        requireMarketLocationNot(createdAt.getContainingLocation());
        this.gotoEntity = pickMarket().getPrimaryEntity();
        if (this.gotoEntity == null) {
            log.info("Failed to find entity to go to");
            return false;
        }

        makeImportant(this.gotoEntity, "$sep_fem_gotoEntity", Stage.GOTO);
        makeImportant(getPerson(), "$sep_fem_returnPerson", Stage.RETURN);

        beginStageTrigger(Stage.GOTO);
        triggerCreateFleet(FleetSize.LARGE, FleetQuality.DEFAULT,
                getPerson().getMarket().getFactionId(),
                FleetTypes.TRADE, createdAt.getStarSystem());
        triggerPickLocationAroundEntity(createdAt.getPrimaryEntity(), 0f, 0f, 0f);
        triggerSpawnFleetAtPickedLocation();
        triggerMakeFleetIgnoreOtherFleets();
        triggerSetFleetMissionRef("$sep_fem_ref");
        triggerFleetMakeImportant("$sep_fem_fleet", Stage.GOTO, Stage.WAIT, Stage.RETURN);
        triggerFleetNoAutoDespawn();
        triggerFleetSetName("Special Task Force");
        endTrigger();

        List<CampaignFleetAPI> fleets = runStageTriggersReturnFleets(Stage.GOTO);
        if (fleets.isEmpty()) {
            log.info("Failed to create and spawn fleet to escort 1 ");
            return false;
        }
        this.fleet = fleets.get(0);
        if (this.fleet == null || this.fleet.isEmpty() || !this.fleet.isAlive()
                || this.fleet.getMemoryWithoutUpdate().getBoolean("$sep_fem_fleet")) {
            log.info("Failed to create and spawn fleet to escort 2");
            return false;
        }
        this.fleet.addScript(new EscortFleetAssignmentAI(this.fleet, this));

        setStartingStage(Stage.GOTO);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        // If mission ends by returning to contact
        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_fem_completed");
        // If mission ends when arriving at location
        setStageOnMemoryFlag(Stage.COMPLETED, this.gotoEntity, "$sep_fem_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.RETURN, Stage.FAILED_DECIV, createdAt);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        setTimeLimit(Stage.FAILED, MISSION_DURATION, null);
        setCreditReward(CreditReward.HIGH);

        return true;
    }

    @Override
    public String getBaseName() {
        return "Fleet Escort";
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
                    getGotoEntity().getName(), getGotoEntity().getStarSystem().getNameWithLowercaseTypeShort());
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
                    getGotoEntity().getName(), getGotoEntity().getStarSystem().getNameWithLowercaseTypeShort());
        }
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();

        ArrowData arrowFleet = new ArrowData(Global.getSector().getPlayerFleet(), this.fleet);
        arrowFleet.width = 14f;
        arrowFleet.color = Misc.getHighlightColor();
        result.add(arrowFleet);

        ArrowData arrowDestination = new ArrowData(Global.getSector().getPlayerFleet(), getGotoEntity());
        arrowDestination.width = 14f;
        result.add(arrowDestination);

        return result;
    }

    public SectorEntityToken getGotoEntity() {
        if (getCurrentStage() == Stage.GOTO) {
            return this.gotoEntity;
        }
        return getPerson().getMarket().getPrimaryEntity();
    }

    public enum Stage {
        GOTO,
        WAIT,
        RETURN,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }
}
