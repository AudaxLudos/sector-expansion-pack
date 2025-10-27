package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.missions.hub.EscortFleetAssignmentAI;

import java.util.ArrayList;
import java.util.List;

public class FleetEscortMission extends HubMissionWithBarEvent {
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

        return true;
    }

    @Override
    public String getBaseName() {
        return "Fleet Escort";
    }

    public SectorEntityToken getGotoEntity() {
        return this.gotoEntity;
    }

    public SectorEntityToken getPersonEntity() {
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
