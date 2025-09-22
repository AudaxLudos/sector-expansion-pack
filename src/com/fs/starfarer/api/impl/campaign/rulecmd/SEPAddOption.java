package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

// SEPAddOption text id order(optional)
public class SEPAddOption extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) {
            return false;
        }

        String text = params.get(0).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
        String id = params.get(1).getString(memoryMap);

        OptionPanelAPI options = dialog.getOptionPanel();
        options.addOption(text, id);

        return true;
    }

    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }

    @Override
    public int getOptionOrder(List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        return params.get(2).getInt(memoryMap);
    }
}
