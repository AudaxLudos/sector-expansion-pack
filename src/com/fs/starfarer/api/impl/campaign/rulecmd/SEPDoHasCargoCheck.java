package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * SEPDoHasCargoCheck <cargo id> <quantity> <option id>
 */
public class SEPDoHasCargoCheck extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) {
            return false;
        }

        OptionPanelAPI options = dialog.getOptionPanel();

        String id = params.get(0).getString(memoryMap);
        float quantity = params.get(1).getInt(memoryMap);
        String option = params.get(2).getString(memoryMap);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        boolean hasCargo = cargo.getCommodityQuantity(id) >= quantity
                || cargo.getNumFighters(id) >= quantity
                || cargo.getNumWeapons(id) >= quantity
                || cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(id, null)) >= quantity;

        if (!hasCargo) {
            options.setEnabled(option, false);
        }

        return false;
    }
}
