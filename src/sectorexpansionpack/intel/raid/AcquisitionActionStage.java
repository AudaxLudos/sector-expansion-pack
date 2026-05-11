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
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class AcquisitionActionStage extends ActionStage implements BaseAssignmentAI.FleetActionDelegate {
    protected AcquisitionRaidIntel acquisitionIntel;
    protected List<MilitaryResponseScript> scripts = new ArrayList<>();
    protected MarketAPI target;
    protected boolean sentOrders = false;

    public AcquisitionActionStage(RaidIntel raid, MarketAPI target, float durDays) {
        super(raid);
        this.acquisitionIntel = (AcquisitionRaidIntel) raid;
        this.target = target;
        this.maxDays = durDays;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!this.sentOrders) {
            removeMilScripts();

            // scripts get removed anyway so we don't care about when they expire naturally
            // just make sure they're around for long enough
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
            for (MilitaryResponseScript s : this.scripts) {
                s.forceDone();
            }
        }
    }

    @Override
    protected void updateRoutes() {
        resetRoutes();

        List<RouteManager.RouteData> routes = RouteManager.getInstance().getRoutesForSource(this.intel.getRouteSourceId());
        for (RouteManager.RouteData route : routes) {
            if (this.target.getStarSystem() != null) {
                route.addSegment(new RouteManager.RouteSegment(3f, this.target.getStarSystem().getCenter(), this.target.getPrimaryEntity()));
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

        performRaid(null, this.target);

        this.status = RaidIntel.RaidStageStatus.SUCCESS;

        removeMilScripts();
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = this.intel.getCurrentStage();
        int index = this.intel.getStageIndex(this);

        float opad = 10f;

        // Failure descriptions are handled in acquisition raid intel
        if (curr == index) {
            info.addPara("Conducting operations in the " + this.intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }

    @Override
    public boolean isPlayerTargeted() {
        return this.target.isPlayerOwned();
    }

    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (!market.getFaction().isHostileTo(this.intel.getFaction())) {
            return false;
        }
        if (Misc.flagHasReason(market.getMemoryWithoutUpdate(),
                MemFlags.RECENTLY_RAIDED, this.intel.getFaction().getId())) {
            return false;
        }
        return this.acquisitionIntel.getOutcome() == null;
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "moving in to acquire a special item at " + market.getName();
    }

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "acquiring special item at " + market.getName();
    }

    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        float raidStr = this.intel.getRaidFPAdjusted() / this.intel.getNumFleets() * Misc.FP_TO_GROUND_RAID_STR_APPROX_MULT;
        if (fleet != null) {
            raidStr = MarketCMD.getRaidStr(fleet);
        }

        float maxPenalty = 3f;

        new MarketCMD(market.getPrimaryEntity()).doGenericRaid(this.intel.getFaction(), raidStr, maxPenalty);
    }

    @Override
    public String getRaidPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
        return "orbiting " + from.getName();
    }

    @Override
    public String getRaidInSystemText(CampaignFleetAPI fleet) {
        return "attacking " + this.target.getContainingLocation().getNameWithLowercaseTypeShort();
    }

    @Override
    public String getRaidDefaultText(CampaignFleetAPI fleet) {
        return "travelling to " + this.target.getName();
    }
}
