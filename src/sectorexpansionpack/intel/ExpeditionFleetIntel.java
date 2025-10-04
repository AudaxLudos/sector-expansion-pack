package sectorexpansionpack.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGTravelAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGWaitAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SEPHiddenItemSpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import sectorexpansionpack.intel.misc.ExpeditionFleetDepartureIntel;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class ExpeditionFleetIntel extends FleetGroupIntel {
    public static final String EVENT_KEY = "$sep_efi_eventRef";
    public static final String FLEET_KEY = "$sep_efi_fleet";
    public static final String TARGET_KEY = "$sep_efi_target";
    public static Logger log = Global.getLogger(ExpeditionFleetIntel.class);
    public static String PREPARE_ACTION = "prepare_action";
    public static String GOTO_ACTION = "travel_action";
    public static String LOOT_ACTION = "loot_action";
    public static String RETURN_ACTION = "return_action";
    public static String DOCK_ACTION = "dock_action";

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
        addAction(new FGTravelAction(this.source.getPrimaryEntity(), this.target.getStarSystem().getHyperspaceAnchor()), GOTO_ACTION);
        addAction(new FGWaitAction(this.target, 30f, "exploring and looting " + target.getStarSystem().getNameWithLowercaseTypeShort()), LOOT_ACTION);
        addAction(new FGTravelAction(this.target, this.source.getPrimaryEntity()), RETURN_ACTION);
        addAction(new FGWaitAction(this.source.getPrimaryEntity(), 15f, "Docking to" + source.getName()), DOCK_ACTION);

        createRoute(source.getFactionId(), 10, 1, null);
        getRoute().setDelay((float) (3f + Math.random() * 6f));
        log.info("Created an expedition fleet at " + this.source.getName() + " in " + this.source.getStarSystem().getNameWithLowercaseType() + " and will goto " + this.target.getStarSystem().getNameWithLowercaseTypeShort());

        Misc.makeImportant(this.target, "special item location");
        this.target.getMemoryWithoutUpdate().set(TARGET_KEY, true);
        this.target.getMemoryWithoutUpdate().set(EVENT_KEY, this);
        Misc.setSalvageSpecial(this.target, new SEPHiddenItemSpecial.HiddenSpecialItemSpecialData(this.specialItemSpec.getId()));

        // TODO: Roll chance to reveal fleet
        new ExpeditionFleetDepartureIntel(getRoute(), this.source);
    }

    @Override
    protected void notifyActionFinished(FGAction action) {
        if (action.getId().equals(LOOT_ACTION)) {
            unsetTargetMem();

            Misc.makeImportant(this.fleets.get(0), "has special item");
            Misc.addDefeatTrigger(this.fleets.get(0), "SEPEFGIFleetDefeated");
        }
        // TODO: Remove special item on target entity when fleet finishes looting
        // TODO: Install special item on source market when fleet returns to source market
        // TODO: Roll chance to leak the expedition's status
    }

    @Override
    protected void notifyEnded() {
        unsetTargetMem();
        unsetFleetMem();
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
        Misc.makeUnimportant(this.fleets.get(0), "specialItemRemoved");
        this.target.getMemoryWithoutUpdate().removeAllRequired(FLEET_KEY);
        this.target.getMemoryWithoutUpdate().removeAllRequired(EVENT_KEY);
    }

    public void unsetTargetMem() {
        Misc.makeUnimportant(this.target, "specialItemRemoved");
        this.target.getMemoryWithoutUpdate().removeAllRequired(TARGET_KEY);
        this.target.getMemoryWithoutUpdate().removeAllRequired(EVENT_KEY);
        this.target.getMemoryWithoutUpdate().removeAllRequired(MemFlags.SALVAGE_SPECIAL_DATA);
    }

    public void failFleet() {
        finish(false);
    }
}
