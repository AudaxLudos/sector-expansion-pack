package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.MissionScenarioSpec;
import sectorexpansionpack.Utils;
import sectorexpansionpack.missions.hub.EscortFleetAssignmentAI;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FleetEscortMission extends HubMissionWithBarEvent {
    public static final float MISSION_DURATION = 120f;
    public static Logger log = Global.getLogger(FleetEscortMission.class);
    protected MissionScenarioSpec scenario;
    protected boolean fleetSpawned = false; // Might not be needed
    protected CampaignFleetAPI fleet;
    protected SectorEntityToken gotoEntity;

    public FleetEscortMission() {
        super();
        setGenRandom(new Random(Utils.random.nextLong()));
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        this.scenario = Utils.pickMissionScenario(getMissionId(), getGenRandom());
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

        // TODO: Customize fleet based on mission scenario
        float fp = 30f;
        FleetParamsV3 params = new FleetParamsV3(
                createdAt,
                FleetTypes.TASK_FORCE,
                fp, // combatPts
                fp, // freighterPts
                fp, // tankerPts
                fp, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod
        );
        this.fleet = FleetFactoryV3.createFleet(params);
        if (!setEntityMissionRef(this.fleet, "$sep_fem_ref")) {
            log.info("Failed to create fleet to escort 1");
            return false;
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
        makeImportant(this.fleet, "$sep_fem_escortedFleet", Stage.GOTO, Stage.WAIT, Stage.RETURN);
        makeImportant(getPerson(), "$sep_fem_returnPerson", Stage.RETURN);

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

        if (this.scenario.getDuration() > -1) {
            setTimeLimit(Stage.FAILED, this.scenario.getDuration(), null);
        } else {
            setTimeLimit(Stage.FAILED, MISSION_DURATION, null);
        }

        if (this.scenario.getMinCreditReward() > -1) {
            if (this.scenario.getMaxCreditReward() < this.scenario.getMinCreditReward()) {
                setCreditReward(this.scenario.getMinCreditReward());
            } else {
                setCreditReward(this.scenario.getMinCreditReward(), this.scenario.getMaxCreditReward());
            }
        } else {
            setCreditReward(CreditReward.HIGH);
        }

        // TODO: Add fleet complications

        return true;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        if (this.fleetSpawned && getCurrentStage() != Stage.COMPLETED) {
            // TODO: Track and include fleet points for failure condition
            if (this.fleet.isExpired() || !this.fleet.isAlive()) {
                setCurrentStage(Stage.FAILED, null, null);
            }
        }
    }

    @Override
    public String getBaseName() {
        return "Fleet Escort";
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_fem_scenarioId", this.scenario.getScenarioId());
        set("$sep_fem_isBarEvent", isBarEvent());
        set("$sep_fem_mrktNme", this.gotoEntity.getName());
        set("$sep_fem_sysName", this.gotoEntity.getStarSystem().getNameWithLowercaseTypeShort());
        set("$sep_fem_duration", this.scenario.getDuration());
        set("$sep_fem_reward", Misc.getDGSCredits(getCreditsReward()));
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (!this.fleetSpawned) {
            this.fleetSpawned = true;
            MarketAPI market = getPerson().getMarket();
            SectorEntityToken entity = market.getPrimaryEntity();
            entity.getContainingLocation().addEntity(this.fleet);
            this.fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
            this.fleet.setFacing(getGenRandom().nextFloat() * 360f);
            this.fleet.addScript(new EscortFleetAssignmentAI(this.fleet, this));
            this.fleet.getMemoryWithoutUpdate().set("$sourceId", entity.getId());
            this.fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            this.fleet.setName("Special Task Force");
        }
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
