package sectorexpansionpack.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.GroundRaidObjectivesListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.graid.SpecialItemRaidObjectivePluginImpl;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.List;
import java.util.Map;

public class ArtifactIncursionMission extends HubMissionWithBarEvent implements GroundRaidObjectivesListener {
    public static float MISSION_DURATION = 120f;

    public enum Stage {
        RAID_ARTIFACT,
        DELIVER_ARTIFACT,
        COMPLETED,
        FAILED,
        FAILED_DECIV
    }

    protected PersonAPI person;
    protected MarketAPI market;
    protected Industry industry;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected SpecialItemRaidObjectivePluginImpl objectivePlugin;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        this.person = getPerson();
        if (!setPersonMissionRef(this.person, "$sep_aim_ref")) {
            return false;
        }

        requireMarketFactionNot(this.person.getFaction().getId());
        requireMarketFactionHostileTo(this.person.getFaction().getId());
        requireMarketNotHidden();
        requireMarketNotInHyperspace();
        requireMarketFactionNotPlayer();
        requireMarketHasItemsInstalled();
        this.market = pickMarket();

        if (!setMarketMissionRef(this.market, "$sep_aim_ref")) {
            return false;
        }

        pickIndustryWithItem();
        if (this.industry == null) {
            return false;
        }

        pickItemFromIndustry();
        if (this.specialItemSpec == null || this.specialItemData == null) {
            return false;
        }

        makeImportant(this.market, "$sep_aim_raidHere", Stage.RAID_ARTIFACT);
        makeImportant(this.person, "$sep_aim_deliverHere", Stage.DELIVER_ARTIFACT);

        setStartingStage(Stage.RAID_ARTIFACT);
        setSuccessStage(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        connectWithMemoryFlag(Stage.RAID_ARTIFACT, Stage.DELIVER_ARTIFACT, this.market, "$sep_aim_deliverArtifact");
        setStageOnMemoryFlag(Stage.COMPLETED, this.person, "$sep_aim_completed");

        addNoPenaltyFailureStages(Stage.FAILED_DECIV);
        connectWithMarketDecivilized(Stage.RAID_ARTIFACT, Stage.FAILED_DECIV, this.market);
        connectWithMarketDecivilized(Stage.DELIVER_ARTIFACT, Stage.FAILED_DECIV, this.person.getMarket());
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, this.market);
        setStageOnMarketDecivilized(Stage.FAILED_DECIV, createdAt);

        setTimeLimit(Stage.FAILED, MISSION_DURATION, null);

        return true;
    }

    public void pickIndustryWithItem() {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
        for (Industry industry : this.market.getIndustries()) {
            for (SpecialItemData ignored : industry.getVisibleInstalledItems()) {
                industryPicker.add(industry);
            }
        }

        this.industry = industryPicker.pick();
    }

    public void pickItemFromIndustry() {
        WeightedRandomPicker<SpecialItemData> specialItemPicker = new WeightedRandomPicker<>();
        for (Industry industry : this.market.getIndustries()) {
            for (SpecialItemData data : industry.getVisibleInstalledItems()) {
                specialItemPicker.add(data);
            }
        }

        SpecialItemData picked = specialItemPicker.pick();
        if (picked == null) {
            return;
        }
        this.specialItemData = picked;
        this.specialItemSpec = Global.getSettings().getSpecialItemSpec(picked.getId());
    }

    @Override
    public String getBaseName() {
        return "Artifact Incursion";
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$sep_aim_artifactId", this.specialItemSpec.getId());
        set("$sep_aim_artifactName", this.specialItemSpec.getName());
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public void modifyRaidObjectives(MarketAPI market, SectorEntityToken entity, List<GroundRaidObjectivePlugin> objectives, MarketCMD.RaidType type, int marineTokens, int priority) {
        if (priority != 1) {
            return;
        }

        if (market == this.market && entity == this.market.getPrimaryEntity()) {
            for (GroundRaidObjectivePlugin obj : objectives) {
                if (obj instanceof SpecialItemRaidObjectivePluginImpl) {
                    SpecialItemRaidObjectivePluginImpl plugin = (SpecialItemRaidObjectivePluginImpl) obj;
                    if (plugin.getItemSpec() == this.specialItemSpec && obj.getSource() == this.industry) {
                        this.objectivePlugin = plugin;
                        obj.setNameOverride(plugin.getItemSpec().getName() + " (Mission Target)");
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void reportRaidObjectivesAchieved(RaidResultData data, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        boolean found = false;
        for (GroundRaidObjectivePlugin obj : data.objectives) {
            if (obj instanceof SpecialItemRaidObjectivePluginImpl) {
                if (obj == this.objectivePlugin) {
                    found = true;
                    break;
                }
            }
        }
        if (found) {
            advance(0.1f); // triggers removal of objective
            dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$raidMarinesLost", data.marinesLost, 0);
            FireAll.fire(null, dialog, memoryMap, "SEPAIMRaidFinished");
            Global.getSector().getListenerManager().removeListener(this);
        }
    }

    public void requireMarketHasItemsInstalled() {
        this.search.marketReqs.add(new MarketHasItemsInstalledReq());
    }

    public static class MarketHasItemsInstalledReq implements MarketRequirement {
        @Override
        public boolean marketMatchesRequirement(MarketAPI market) {
            for (Industry industry : market.getIndustries()) {
                if (!industry.getVisibleInstalledItems().isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }
}
