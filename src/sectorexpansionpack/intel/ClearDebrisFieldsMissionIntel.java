package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import sectorexpansionpack.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClearDebrisFieldsMissionIntel extends BaseMissionIntel {
    public static Logger log = Global.getLogger(ClearDebrisFieldsMissionIntel.class);
    protected MarketAPI market;
    protected StarSystemAPI system;
    protected FactionAPI faction;
    protected int reward;
    protected int numDebris;
    protected int clearedDebris = 0;
    protected List<SectorEntityToken> debrisFields = new ArrayList<>();

    public ClearDebrisFieldsMissionIntel(MarketAPI market) {
        this.market = market;
        if (this.market == null) {
            endImmediately();
            return;
        }

        this.faction = this.market.getFaction();
        if (!this.market.getFaction().isHostileTo(Factions.INDEPENDENT) && (float) Math.random() > 0.67f) {
            this.faction = Global.getSector().getFaction(Factions.INDEPENDENT);
        }

        setDuration(120f);

        this.numDebris = Utils.random.nextInt(2, 8);
        this.reward = (int) Misc.getDistance(new Vector2f(), this.market.getLocationInHyperspace());
        this.reward = 20000 + (this.reward / 10000) * 10000 + (this.numDebris * 2000);
        if (this.reward < 10000) {
            this.reward = 10000;
        }

        log.info("Created ClearDebrisFieldsMissionIntel: faction: " + this.faction.getDisplayName());

        initRandomCancel();
        setPostingLocation(this.market.getPrimaryEntity());

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    public void advanceMission(float amount) {
        if (this.clearedDebris >= this.debrisFields.size()) {
            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            CargoAPI cargo = playerFleet.getCargo();
            cargo.getCredits().add(this.reward);

            CoreReputationPlugin.MissionCompletionRep rep = new CoreReputationPlugin.MissionCompletionRep(CoreReputationPlugin.RepRewards.HIGH, RepLevel.WELCOMING,
                    -CoreReputationPlugin.RepRewards.TINY, RepLevel.INHOSPITABLE);
            ReputationActionResponsePlugin.ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
                    new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.MISSION_SUCCESS, rep,
                            null, null, true, false),
                    this.faction.getId());
            setMissionResult(new MissionResult(this.reward, result));
            setMissionState(MissionState.COMPLETED);
            endMission();
            sendUpdateIfPlayerHasIntel(this.missionResult, false);
        }
    }

    @Override
    public void missionAccepted() {
        for (int i = 0; i < this.numDebris; ) {
            float radius = 150f + (Utils.random.nextFloat() * 300f);
            DebrisFieldTerrainPlugin.DebrisFieldParams params = new DebrisFieldTerrainPlugin.DebrisFieldParams(
                    radius, -1f, 10000000f, 0f);
            params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.PLAYER_SALVAGE;
            SectorEntityToken debris = Misc.addDebrisField(this.market.getContainingLocation(), params, null);
            BaseThemeGenerator.EntityLocation loc = BaseThemeGenerator.pickCommonLocation(
                    Utils.random, this.market.getStarSystem(), radius + 100f, true, null);

            if (loc == null) {
                continue;
            }
            if (loc.orbit != null) {
                debris.setOrbit(loc.orbit);
                loc.orbit.setEntity(debris);
            } else {
                debris.setOrbit(null);
                debris.getLocation().set(loc.location);
            }

            debris.getDropValue().clear();
            debris.getDropRandom().clear();

            SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
            d.chances = 1;
            d.group = "blueprints_low";
            debris.addDropRandom(d);

            d = new SalvageEntityGenDataSpec.DropData();
            d.chances = 1;
            d.group = "rare_tech_low";
            d.valueMult = 0.1f;
            debris.addDropRandom(d);

            d = new SalvageEntityGenDataSpec.DropData();
            d.chances = 1;
            d.group = "ai_cores3";
            debris.addDropRandom(d);

            d = new SalvageEntityGenDataSpec.DropData();
            d.chances = 1;
            d.group = "any_hullmod_low";
            debris.addDropRandom(d);

            d = new SalvageEntityGenDataSpec.DropData();
            d.chances = 5;
            d.group = "weapons2";
            debris.addDropRandom(d);

            d = new SalvageEntityGenDataSpec.DropData();
            d.group = "basic";
            d.value = (int) ((1000 + params.bandWidthInEngine) * Utils.random.nextInt(5, 10));
            debris.addDropValue(d);

            debris.getMemoryWithoutUpdate().set("$sep_cdf_target", true, getDuration());
            debris.getMemoryWithoutUpdate().set("$sep_cdf_eventRef", this, getDuration());
            Misc.setFlagWithReason(debris.getMemoryWithoutUpdate(), MemFlags.ENTITY_MISSION_IMPORTANT, "sep_cdf", true, getDuration());

            this.debrisFields.add(debris);
            i++; // Increment here to ensure the amount of debris spawns
        }

        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public void endMission() {
        // Event calls should remove the debris
        // Use this as backup if the event call fails to clear the debris
        for (SectorEntityToken debrisField : this.debrisFields) {
            this.market.getStarSystem().removeEntity(debrisField);
        }

        Global.getSector().getListenerManager().removeListener(this);
        endAfterDelay();
    }

    @Override
    protected MissionResult createAbandonedResult(boolean withPenalty) {
        if (withPenalty) {
            CoreReputationPlugin.MissionCompletionRep rep = new CoreReputationPlugin.MissionCompletionRep(
                    CoreReputationPlugin.RepRewards.HIGH, RepLevel.WELCOMING,
                    -CoreReputationPlugin.RepRewards.TINY, RepLevel.INHOSPITABLE);
            ReputationActionResponsePlugin.ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
                    new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.MISSION_FAILURE, rep,
                            null, null, true, false),
                    this.faction.getId());
            return new MissionResult(0, result);
        }
        return new MissionResult();
    }

    @Override
    protected MissionResult createTimeRanOutFailedResult() {
        return createAbandonedResult(true);
    }

    @Override
    protected String getName() {
        return "Clear Debris Fields";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return this.faction;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        info.addImage(this.faction.getLogo(), width, 128, opad);

        info.addPara("%s officials have posted a reward for clearing out debris fields in the " + this.market.getStarSystem().getName() + ".",
                opad, this.faction.getBaseUIColor(), Misc.ucFirst(this.faction.getPersonNamePrefix()));

        if (isPosted() || isAccepted()) {
            addBulletPoints(info, ListInfoMode.IN_DESC);

            if (isAccepted()) {
                info.addPara("Their is currently %s out of %s debris fields cleared.",
                        opad, this.faction.getBaseUIColor(), this.clearedDebris + "", this.debrisFields.size() + "");
            }

            addGenericMissionState(info);

            addAcceptOrAbandonButton(info, width, "Accept", "Abandon");
        } else {
            addGenericMissionState(info);

            addBulletPoints(info, ListInfoMode.IN_DESC);
        }
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) {
            initPad = opad;
        }

        Color tc = getBulletColorForMode(mode);

        bullet(info);
        boolean isUpdate = getListInfoParam() != null;

        if (isUpdate) {
            // 3 possible updates: de-posted/expired, failed, completed
            if (isFailed() || isCancelled()) {
                return;
            } else if (isCompleted()) {
                if (this.missionResult.payment > 0) {
                    info.addPara("%s received", initPad, tc, h, Misc.getDGSCredits(this.missionResult.payment));
                }
                CoreReputationPlugin.addAdjustmentMessage(this.missionResult.rep1.delta, this.faction, null,
                        null, null, info, tc, true, 0f);
            }
        } else {
            // either in small description, or in tooltip/intel list
            if (this.missionResult != null) {
                if (this.missionResult.payment > 0) {
                    info.addPara("%s received", initPad, tc, h, Misc.getDGSCredits(this.missionResult.payment));
                    initPad = 0f;
                }

                if (this.missionResult.rep1 != null) {
                    CoreReputationPlugin.addAdjustmentMessage(this.missionResult.rep1.delta, this.faction, null,
                            null, null, info, tc, isUpdate, initPad);
                    initPad = 0f;
                }
            } else {
                float betweenPad = 0f;
                if (mode != ListInfoMode.IN_DESC) {
                    info.addPara("Faction: " + this.faction.getDisplayName(), initPad, tc,
                            this.faction.getBaseUIColor(),
                            this.faction.getDisplayName());
                    initPad = betweenPad;
                } else {
                    betweenPad = 0f;
                }

                info.addPara("%s reward", initPad, tc, h, Misc.getDGSCredits(this.reward));
                addDays(info, "to complete", this.duration - this.elapsedDays, tc, betweenPad);
            }
        }

        unindent(info);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("campaignMissions", "survey_planet");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_SALVAGE);
        tags.add(Tags.INTEL_MISSIONS);
        tags.add(this.faction.getId());
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return this.market.getPrimaryEntity();
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        SectorEntityToken entity = dialog.getInteractionTarget();

        if (action.equals("clearDebris")) {
            for (SectorEntityToken debris : this.debrisFields) {
                // Use entity memory id due to dialog.getInteractionTarget().getId() is showing a different id
                if (entity.getMemoryWithoutUpdate().get("$id") == debris.getMemoryWithoutUpdate().get("$id")) {
                    this.market.getStarSystem().removeEntity(debris);
                }
            }
            this.market.getStarSystem().removeEntity(entity);
            this.clearedDebris++;
            return true;
        }

        return true;
    }

    public MarketAPI getMarket() {
        return this.market;
    }
}
