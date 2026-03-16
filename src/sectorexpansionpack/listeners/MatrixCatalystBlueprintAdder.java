package sectorexpansionpack.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;

import java.util.Objects;

public class MatrixCatalystBlueprintAdder extends BaseCampaignEventListener {
    public MatrixCatalystBlueprintAdder() {
        super(false);
    }

    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
        FleetEncounterContextPlugin.DataForEncounterSide data = plugin.getLoserData();
        CampaignFleetAPI fleet = data.getFleet();
        if (!fleet.isPlayerFleet() && Objects.equals(fleet.getFaction().getId(), Factions.REMNANTS)) {
            for (FleetEncounterContextPlugin.FleetMemberData memberData : data.getOwnCasualties()) {
                if (memberData.getStatus() == FleetEncounterContextPlugin.Status.DESTROYED || memberData.getStatus() == FleetEncounterContextPlugin.Status.DISABLED) {
                    if (Objects.equals(memberData.getMember().getHullId(), "remnant_station2") || Objects.equals(memberData.getMember().getHullId(), "remnant_station1")) {
                        loot.addSpecial(new SpecialItemData(Items.INDUSTRY_BP, "sep_matrix_catalyst"), 1f);
                        for (CampaignEventListener l : Global.getSector().getAllListeners()) {
                            if (l instanceof MatrixCatalystBlueprintAdder listener) {
                                Global.getSector().removeListener(listener);
                            }
                        }
                        return;
                    }
                }
            }
        }
    }
}
