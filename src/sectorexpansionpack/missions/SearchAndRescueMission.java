package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.ui.SectorMapAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SearchAndRescueMission extends HubMissionWithBarEvent {
    public static Logger log = Global.getLogger(SearchAndRescueMission.class);
    public static float MISSION_DAYS = 120f;
    protected SectorEntityToken survivorEntity;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            setGiverRank(Ranks.CITIZEN);
            setGiverPost(pickOne(Ranks.POST_AGENT, Ranks.POST_SMUGGLER, Ranks.POST_GANGSTER, Ranks.POST_FENCE, Ranks.POST_CRIMINAL));
            setGiverImportance(pickImportance());
            setGiverFaction(Factions.PIRATES);
            setGiverTags(Tags.CONTACT_TRADE);
            findOrCreateGiver(createdAt, true, false);
        }

        if (!setPersonMissionRef(getPerson(), "$sep_sar_ref")) {
            log.info("Failed to find or create contact");
            return false;
        }

        if (barEvent) {
            setGiverIsPotentialContactOnSuccess();
        }

        preferSystemInInnerSector();
        preferSystemInDirectionOfOtherMissions();
        requireEntityTags(ReqMode.ALL, Tags.SALVAGEABLE);
        requireEntityType(Entities.WRECK);

        this.survivorEntity = pickEntity();
        if (!setEntityMissionRef(this.survivorEntity, "$sep_sar_ref")) {
            log.info("Failed to find entity containing survivor");
            return false;
        }

        makeImportant(this.survivorEntity, "$sep_sar_survivorEntity", Stage.FIND);
        makeImportant(getPerson(), "$sep_sar_contactPerson", Stage.RETURN);

        setStartingStage(Stage.FIND);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        connectWithMemoryFlag(Stage.FIND, Stage.RETURN, this.survivorEntity, "$sep_sar_returnToContact");
        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$sep_sar_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.RETURN, Stage.FAILED_DECIV, createdAt);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        setTimeLimit(Stage.FAILED, MISSION_DAYS, null, Stage.RETURN);

        setCreditReward(CreditReward.HIGH);

        return true;
    }

    @Override
    public String getBaseName() {
        return "Search and Rescue";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (this.currentStage == Stage.FIND) {
            Constellation constellation = this.survivorEntity.getConstellation();
            SectorEntityToken entity = null;
            if (constellation != null && map != null) {
                entity = map.getConstellationLabelEntity(constellation);
            }
            if (entity == null) entity = this.survivorEntity;
            return entity;
        }
        return super.getMapLocation(map);
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();

        ArrowData arrow = new ArrowData(Global.getSector().getPlayerFleet(), getMapLocation(map));
        arrow.width = 14f;
        result.add(arrow);

        return result;
    }

    public enum Stage {
        FIND,
        RETURN,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }
}
