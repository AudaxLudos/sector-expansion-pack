package sectorexpansionpack.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SearchAndRescueMission extends HubMissionWithBarEvent {
    public static Logger log = Global.getLogger(SearchAndRescueMission.class);
    public static float MISSION_DAYS = 120f;
    protected PersonPostType survivorPostType;
    protected PersonAPI survivor;
    protected SectorEntityToken survivorEntity;
    protected boolean survivorAlive = true;

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

        this.survivor = createdAt.getFaction().createRandomPerson(this.genRandom);
        if (this.survivor == null) {
            log.info("Failed to create survivor to rescue");
            return false;
        }

        preferSystemInInnerSector();
        preferSystemInDirectionOfOtherMissions();
        requireEntityTags(ReqMode.ALL, Tags.SALVAGEABLE);
        requireEntityType(Entities.WRECK);

        this.survivorPostType = (PersonPostType) pickOneObject(Arrays.asList(PersonPostType.OFFICER, PersonPostType.ADMINISTRATOR, PersonPostType.CIVILIAN));
        switch (this.survivorPostType) {
            case OFFICER -> {
                this.survivor = OfficerManagerEvent.createOfficer(createdAt.getFaction(), 1, OfficerManagerEvent.SkillPickPreference.ANY, this.genRandom);
                this.survivor.setPostId(Ranks.POST_OFFICER_FOR_HIRE);
            }
            case ADMINISTRATOR -> {
                this.survivor = OfficerManagerEvent.createAdmin(createdAt.getFaction(), 1, this.genRandom);
            }
            default -> {
                this.survivor = createdAt.getFaction().createRandomPerson(this.genRandom);
            }
        }

        if (this.survivor == null) {
            log.info("Failed to create survivor");
            return false;
        }

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
        return "Search and Rescue: " + this.survivor.getNameString();
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_sar_survivorAlive", this.survivorAlive);
        set("$sep_sar_survivorPostType", this.survivorPostType);
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (this.currentStage == Stage.FIND) {
            String loc = BreadcrumbSpecial.getLocationDescription(this.survivorEntity, false);
            info.addPara("Search for %s in " + loc, 3f, tc, Misc.getHighlightColor(), this.survivor.getNameString());
            return true;
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Return to " + getPerson().getMarket().getName() + " in the "
                            + getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                            + " and talk to " + getPerson().getNameString() + ".",
                    3f, tc, Misc.getHighlightColor(), this.survivor.getNameString());
            return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (this.currentStage == Stage.FIND) {
            String loc = BreadcrumbSpecial.getLocatedString(this.survivorEntity);
            loc = loc.replaceAll("orbiting", "near");
            loc = loc.replaceAll("located in ", "near ");
            info.addPara("Search for %s " + loc, 10f, Misc.getHighlightColor(), this.survivor.getNameString());
        } else if (this.currentStage == Stage.RETURN) {
            info.addPara("Return with %s to " + getPerson().getMarket().getName() + " in the "
                            + getPerson().getMarket().getStarSystem().getNameWithLowercaseTypeShort()
                            + " and talk to " + getPerson().getNameString() + ".",
                    10f, Misc.getHighlightColor(), this.survivor.getNameString());
        }
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if (action.equals("checkSurvivorStatus")) {
            if (this.timeLimit.days - this.elapsed < 60f) {
                this.survivorAlive = false;
            }
            updateInteractionData(dialog, memoryMap);
            return this.survivorAlive;
        } else if (action.equals("showSurvivorVisual")) {
            dialog.getVisualPanel().showPersonInfo(this.survivor);
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    protected void endSuccessImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (this.survivorAlive && this.survivorPostType != PersonPostType.CIVILIAN) {
            for (EveryFrameScript script : Global.getSector().getScripts()) {
                if (script instanceof OfficerManagerEvent manager) {
                    float salary = (this.survivorPostType == PersonPostType.OFFICER ?
                            Misc.getOfficerSalary(this.survivor) : Misc.getAdminSalary(this.survivor)) * 0.5f;
                    OfficerManagerEvent.AvailableOfficer officer = new OfficerManagerEvent.AvailableOfficer(
                            this.survivor, getPerson().getMarket().getId(), 0, Math.round(salary));

                    if (this.survivorPostType == PersonPostType.OFFICER) {
                        manager.addAvailable(officer);
                    } else if (this.survivorPostType == PersonPostType.ADMINISTRATOR) {
                        manager.addAvailableAdmin(officer);
                    }

                    officer.person.getMemoryWithoutUpdate().set("$sep_survivor", true);

                    break;
                }
            }
        }
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

    public enum PersonPostType {
        OFFICER,
        ADMINISTRATOR,
        CIVILIAN
    }
}
