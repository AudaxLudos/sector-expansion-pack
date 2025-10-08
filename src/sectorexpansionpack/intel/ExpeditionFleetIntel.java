package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGTravelAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGWaitAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SEPHiddenItemSpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.misc.ExpeditionFleetDepartureIntel;
import sectorexpansionpack.intel.misc.LeakedArtifactLocationIntel;
import sectorexpansionpack.missions.EntityFinderMission;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class ExpeditionFleetIntel extends FleetGroupIntel {
    public static final String EVENT_KEY = "$sep_efi_eventRef";
    public static final String SOURCE_KEY = "$sep_efi_source";
    public static final String FLEET_KEY = "$sep_efi_fleet";
    public static final String TARGET_KEY = "$sep_efi_target";
    public static Logger log = Global.getLogger(ExpeditionFleetIntel.class);
    public static String PREPARE_ACTION = "prepare_action";
    public static String GOTO_ACTION = "travel_action";
    public static String LOOT_ACTION = "loot_action";
    public static String RETURN_ACTION = "return_action";
    public static String DOCK_ACTION = "dock_action";

    protected float revealChance = 0.2f;
    protected boolean isLeaked = false;
    protected FGWaitAction lootAction;
    protected SpecialItemSpecAPI specialItemSpec;
    protected SpecialItemData specialItemData;
    protected MarketAPI source;
    protected SectorEntityToken target;

    public ExpeditionFleetIntel(SpecialItemSpecAPI specialItemSpec, MarketAPI source, SectorEntityToken target) {
        this.specialItemSpec = specialItemSpec;
        this.specialItemData = new SpecialItemData(specialItemSpec.getId(), null);
        this.source = source;
        this.target = target;

        setFaction(source.getFactionId());
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "preparing for expedition"), PREPARE_ACTION);
        addAction(new FGTravelAction(this.source.getPrimaryEntity(), this.target.getStarSystem().getCenter()), GOTO_ACTION);
        this.lootAction = new FGWaitAction(this.target, 30f, "exploring " + target.getName());
        addAction(this.lootAction, LOOT_ACTION);
        addAction(new FGTravelAction(this.target, this.source.getPrimaryEntity()), RETURN_ACTION);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "Docking to " + source.getName()), DOCK_ACTION);

        createRoute(source.getFactionId(), 10, 1, null);
        getRoute().setDelay((float) (3f + Math.random() * 6f));
        log.info("Created an expedition fleet at " + this.source.getName() + " in " + this.source.getStarSystem().getNameWithLowercaseType() + " and will goto " + this.target.getStarSystem().getNameWithLowercaseTypeShort());

        // Mark source so it won't be reselected for future expedition
        this.source.getMemoryWithoutUpdate().set(SOURCE_KEY, true);

        // Mark target so it won't be reselected for future expedition
        Misc.makeImportant(this.target, "specialItemLocation");
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);
        Misc.setSalvageSpecial(this.target, new SEPHiddenItemSpecial.HiddenSpecialItemSpecialData(this.specialItemSpec.getId()));

        if (Utils.rollProbability(this.revealChance)) {
            new ExpeditionFleetDepartureIntel(getRoute(), this.source);
        } else {
            this.revealChance += 0.1f;
        }
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        if (action == null) {
            return;
        }

        if (action.getId().equals(LOOT_ACTION)) {
            Misc.makeUnimportant(this.target, "specialItemLocation");
            this.target.getMemoryWithoutUpdate().unset(MemFlags.SALVAGE_SPECIAL_DATA);

            // TODO: Make fleet aggressive and defensive when they reach the location
            if (isSpawnedFleets()) {
                Misc.makeImportant(this.fleets.get(0), "hasSpecialItem");
                Misc.addDefeatTrigger(this.fleets.get(0), "SEPEFGIFleetDefeated");
            }
        } else if (action.getId().equals(DOCK_ACTION)) {
            EntityFinderMission efm = new EntityFinderMission();
            efm.requireMarketFaction(this.source.getFactionId());
            efm.requireMarketNotHidden();
            efm.requireMarketNotInHyperspace();
            efm.requireMarketFactionNotPlayer();
            efm.requireMarketCanUseSpecialItem(this.specialItemData);
            efm.preferMarketSizeAtMost(100);
            efm.preferMarketIs(this.source);
            MarketAPI market = efm.pickMarket();

            if (market == null) {
                log.info("Failed to find market to install special item");
                return;
            }

            Industry ind = pickIndustryToInstallItem(market, this.specialItemData);
            ind.setSpecialItem(this.specialItemData);
            IntelInfoPlugin message = new MessageIntel("Install special item to " + ind.getCurrentName() + " in the " + market.getName() + " within the " + market.getStarSystem().getNameWithLowercaseTypeShort());
            Global.getSector().getIntelManager().addIntel(message);
            log.info("Installing special item in " + ind.getCurrentName() + " on " + market.getName() + " within the " + market.getStarSystem().getNameWithLowercaseTypeShort());
        }

        if (!this.isLeaked && !PREPARE_ACTION.equals(action.getId()) && !DOCK_ACTION.equals(action.getId())) {
            if (Utils.rollProbability(this.revealChance)) {
                this.isLeaked = true;

                new LeakedArtifactLocationIntel(action.getId(), this.source, this.target, 3f);
            } else {
                this.revealChance += 0.2f;
            }
        }
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
        super.spawnFleet(route);

        // If the fleet spawns midway
        // We ensure that the fleet is marked properly
        if (isSpawnedFleets()) {
            if (this.lootAction.isActionFinished()) {
                Misc.makeImportant(this.fleets.get(0), "hasSpecialItem");
                Misc.addDefeatTrigger(this.fleets.get(0), "SEPEFGIFleetDefeated");
            }
        }

        return null;
    }

    public Industry pickIndustryToInstallItem(MarketAPI market, SpecialItemData specialItemData) {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
        for (Industry industry : market.getIndustries()) {
            if (industry.wantsToUseSpecialItem(specialItemData)) {
                industryPicker.add(industry);
            }
        }
        return industryPicker.pick();
    }

    @Override
    protected void notifyEnded() {
        unsetTargetMem();
        unsetFleetMem();
        unsetSourceMem();

        log.info("Expedition Fleet Intel Ended");
    }

    @Override
    protected boolean isPlayerTargeted() {
        return false;
    }

    @Override
    protected void spawnFleets() {
        FleetCreatorMission fcm = new FleetCreatorMission(getRandom());

        fcm.beginFleet();
        fcm.createFleet(FleetCreatorMission.FleetStyle.QUALITY, 10, this.source.getFactionId(), this.source.getLocationInHyperspace());
        fcm.setFleetSource(this.source);
        fcm.triggerMakeLowRepImpact();
        fcm.triggerSetFleetFlag(FLEET_KEY);
        fcm.triggerSetFleetMemoryValue(EVENT_KEY, this);
        fcm.triggerFleetSetName("Expedition Fleet");

        CampaignFleetAPI fleet = fcm.createFleet();
        if (fleet != null && this.route != null) {
            setLocationAndCoordinates(fleet, this.route.getCurrent());
            this.fleets.add(fleet);
        }
    }

    @Override
    protected SectorEntityToken getSource() {
        return this.source.getPrimaryEntity();
    }

    @Override
    protected SectorEntityToken getDestination() {
        return this.target.getStarSystem().getHyperspaceAnchor();
    }

    @Override
    protected String getBaseName() {
        return "Artifact Expedition";
    }

    @Override
    protected void addNonUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
    }

    @Override
    protected void addUpdateBulletPoints(TooltipMakerAPI info, Color tc, Object param, ListInfoMode mode, float initPad) {
    }

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        if (action.equals("removeDefeatTrigger")) {
            return true;
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    public void unsetFleetMem() {
        Misc.makeUnimportant(this.fleets.get(0), "hasSpecialItem");
        this.fleets.get(0).getMemoryWithoutUpdate().unset(FLEET_KEY);
        this.fleets.get(0).getMemoryWithoutUpdate().unset(EVENT_KEY);
    }

    public void unsetTargetMem() {
        Misc.makeUnimportant(this.target, "specialItemLocation");
        this.target.getMemoryWithoutUpdate().unset(TARGET_KEY);
        this.target.getMemoryWithoutUpdate().unset(EVENT_KEY);
    }

    public void unsetSourceMem() {
        this.source.getMemoryWithoutUpdate().unset(SOURCE_KEY);
    }
}
