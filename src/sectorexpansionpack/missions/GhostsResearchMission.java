package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import java.awt.*;

public class GhostsResearchMission extends HubMissionWithBarEvent {
    public static Logger log = Global.getLogger(GhostsResearchMission.class);
    protected float progress = 0f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            setGiverRank(pickOne(Ranks.CITIZEN, Ranks.ARISTOCRAT));
            setGiverPost(pickOne(Ranks.POST_SCIENTIST, Ranks.POST_ACADEMICIAN));
            setGiverImportance(pickImportance());
            setGiverTags(Tags.CONTACT_SCIENCE);
            findOrCreateGiver(createdAt, true, false);
        }

        if (setPersonMissionRef(getPerson(), "$sep_grm_ref")) {
            log.info("Failed to find or create mission giver");
            return false;
        }

        makeImportant(getPerson(), "$sep_grm_returnPerson", Stage.DELIVER_DATA);

        setStartingStage(Stage.GATHER_DATA);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.DELIVER_DATA, Stage.FAILED_DECIV, getPerson().getMarket());
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, getPerson().getMarket());

        setCreditReward(CreditReward.HIGH);

        return true;
    }

    @Override
    public String getBaseName() {
        return "Ghosts Research";
    }

    @Override
    protected void updateInteractionDataImpl() {
        super.updateInteractionDataImpl();
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (this.currentStage == Stage.GATHER_DATA) {

        } else if (this.currentStage == Stage.DELIVER_DATA) {

        }
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float oPad = 10f;

        if (this.currentStage == Stage.GATHER_DATA) {

        } else if (this.currentStage == Stage.DELIVER_DATA) {

        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    public enum Stage {
        GATHER_DATA,
        DELIVER_DATA,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }
}
