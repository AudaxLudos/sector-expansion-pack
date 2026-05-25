package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class AcquisitionActionStageV2 extends ActionStage implements ActionAssignmentAI.SEPFleetActionDelegate {
    protected AcquisitionRaidIntelV2 acquisitionIntel;
    protected List<MilitaryResponseScript> scripts = new ArrayList<>();
    protected MarketAPI target;
    protected boolean sentOrders = false;

    public AcquisitionActionStageV2(RaidIntel raid, MarketAPI target, float durDays) {
        super(raid);
        this.acquisitionIntel = (AcquisitionRaidIntelV2) raid;
        this.target = target;
        this.maxDays = durDays;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!this.sentOrders) {
            removeMilScripts();

            float duration = 100f;
            String raidNoun = this.acquisitionIntel.getRaidNoun();

            MilitaryResponseScript.MilitaryResponseParams params = new MilitaryResponseScript.MilitaryResponseParams(
                    CampaignFleetAIAPI.ActionType.HOSTILE,
                    raidNoun + "_" + this.target.getId() + "_" + Misc.genUID(),
                    this.intel.getFaction(),
                    this.target.getPrimaryEntity(),
                    1f,
                    duration);
            MilitaryResponseScript script = new MilitaryResponseScript(params);
            this.target.getContainingLocation().addScript(script);
            this.scripts.add(script);

            MilitaryResponseScript.MilitaryResponseParams defParams = new MilitaryResponseScript.MilitaryResponseParams(
                    CampaignFleetAIAPI.ActionType.HOSTILE,
                    raidNoun + "Def_" + this.target.getId() + "_" + Misc.genUID(),
                    this.target.getFaction(),
                    this.target.getPrimaryEntity(),
                    1f,
                    duration);
            MilitaryResponseScript defScript = new MilitaryResponseScript(defParams);
            this.target.getContainingLocation().addScript(defScript);
            this.scripts.add(defScript);

            this.sentOrders = true;
        }
    }

    protected void removeMilScripts() {
        if (this.scripts != null) {
            for (MilitaryResponseScript script : this.scripts) {
                script.forceDone();
            }
        }
    }

    @Override
    protected void updateRoutes() {
        resetRoutes();

        List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(this.intel.getRouteSourceId());
        for (RouteManager.RouteData route : routes) {
            if (this.target.getStarSystem() != null) {
                route.addSegment(new RouteManager.RouteSegment(3f, this.target.getStarSystem().getCenter()));
            }
            route.addSegment(new RouteManager.RouteSegment(1000f, this.target.getPrimaryEntity()));
        }
    }

    @Override
    protected void updateStatus() {
        abortIfNeededBasedOnFP(true);

        if (this.status != RaidIntel.RaidStageStatus.ONGOING) {
            return;
        }

        if (this.target == null) {
            this.status = RaidIntel.RaidStageStatus.FAILURE;
            removeMilScripts();
            giveReturnOrdersToStragglers(getRoutes());
            return;
        }

        boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(this.intel.getSystem().getCenter());
        if (!inSpawnRange && this.elapsed > this.maxDays) {
            autoresolve();
            return;
        }

        boolean targetRaided = Misc.flagHasReason(this.target.getMemoryWithoutUpdate(), MemFlags.RECENTLY_RAIDED, this.intel.getFaction().getId());

        if (this.elapsed > this.maxDays) {
            if (targetRaided) {
                this.status = RaidIntel.RaidStageStatus.SUCCESS;
                removeMilScripts();
            } else {
                this.status = RaidIntel.RaidStageStatus.FAILURE;
                giveReturnOrdersToStragglers(getRoutes());
                removeMilScripts();
            }
        }
    }

    protected void autoresolve() {
        float raidStr = WarSimScript.getFactionStrength(this.intel.getFaction(), this.intel.getSystem());
        float defenderStr = WarSimScript.getEnemyStrength(this.intel.getFaction(), this.intel.getSystem());

        this.status = RaidIntel.RaidStageStatus.FAILURE;

        if (!this.target.getFaction().isHostileTo(this.intel.getFaction())) {
            return;
        }

        defenderStr += WarSimScript.getStationStrength(this.target.getFaction(), this.intel.getSystem(), this.target.getPrimaryEntity());
        if (defenderStr >= raidStr) {
            return;
        }

        Industry station = Misc.getStationIndustry(this.target);
        if (station != null) {
            OrbitalStation.disrupt(station);
        }

        performAction(null, this.target.getPrimaryEntity());

        this.status = RaidIntel.RaidStageStatus.SUCCESS;
        removeMilScripts();
    }

    @Override
    public boolean isPlayerTargeted() {
        return this.target.isPlayerOwned();
    }

    @Override
    public boolean canDoAction(CampaignFleetAPI fleet, SectorEntityToken target) {
        if (target != this.target.getPrimaryEntity()) {
            return false;
        }
        if (!this.target.getFaction().isHostileTo(this.intel.getFaction())) {
            return false;
        }
        if (Misc.flagHasReason(this.target.getMemoryWithoutUpdate(),
                MemFlags.RECENTLY_RAIDED, this.intel.getFaction().getId())) {
            return false;
        }
        return this.acquisitionIntel.getOutcome() == null;
    }

    @Override
    public String getActionApproachText(CampaignFleetAPI fleet, SectorEntityToken target) {
        return "moving in to acquire an artifact at " + this.target.getName();
    }

    @Override
    public String getActionText(CampaignFleetAPI fleet, SectorEntityToken target) {
        return "acquiring artifact at " + this.target.getName();
    }

    @Override
    public void performAction(CampaignFleetAPI fleet, SectorEntityToken target) {
        float raidStr = this.intel.getRaidFPAdjusted() / this.intel.getNumFleets() * Misc.FP_TO_GROUND_RAID_STR_APPROX_MULT;
        if (fleet != null) {
            raidStr = MarketCMD.getRaidStr(fleet);
        }

        float maxPenalty = 3f;
        new MarketCMD(this.target.getPrimaryEntity()).doGenericRaid(this.intel.getFaction(), raidStr, maxPenalty);
    }

    @Override
    public String getActionPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
        return "preparing to acquire an artifact at " + from.getName();
    }

    @Override
    public String getActionInSystemText(CampaignFleetAPI fleet) {
        return "attacking " + this.target.getContainingLocation().getNameWithLowercaseTypeShort();
    }

    @Override
    public String getActionDefaultText(CampaignFleetAPI fleet) {
        return "travelling to " + this.target.getName();
    }

    @Override
    public String getRecentActedKey() {
        return "$sep_ari_recentlyActedAnAcquisition";
    }

    @Override
    public String getRecentAffectedKey() {
        return "$sep_ari_recentlyAffectedByAcquisition";
    }

    @Override
    public float getActionDuration() {
        return this.intel.getActionStage().getMaxDays() / 5f;
    }
}
