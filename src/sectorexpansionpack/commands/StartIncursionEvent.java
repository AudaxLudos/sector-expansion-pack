package sectorexpansionpack.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;
import sectorexpansionpack.intel.IncursionFleetIntel;

import java.util.ArrayList;
import java.util.List;

public class StartIncursionEvent implements BaseCommandWithSuggestion {
    @Override
    public List<String> getSuggestions(int i, List<String> prevSuggestions, CommandContext context) {
        List<String> suggestions = new ArrayList<>();
        if (i == 0) {
            suggestions.addAll(Global.getSector().getEconomy().getMarketsCopy().stream().map(MarketAPI::getName).toList());
            suggestions.addAll(Global.getSector().getAllFactions().stream().map(FactionAPI::getId).toList());
        } else if (i == 1) {
            suggestions.addAll(Global.getSector().getEconomy().getMarketsCopy().stream().map(MarketAPI::getName).toList());
            suggestions.addAll(Global.getSector().getAllFactions().stream().map(FactionAPI::getId).toList());
        } else if (i == 2) {
            suggestions.addAll(Global.getSettings().getAllSpecialItemSpecs().stream().map(SpecialItemSpecAPI::getId).toList());
        }
        return suggestions;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Error: This command is campaign-only.");
            return CommandResult.WRONG_CONTEXT;
        }

        String[] tmp = args.split(" ");

        if (args.isEmpty()) {
            new IncursionFleetIntel();
        } else {
            Object source = null;
            if (tmp.length >= 1) {
                source = CommandUtils.findBestFactionMatch(tmp[0]);
                if (source == null) {
                    source = CommandUtils.findBestMarketMatch(tmp[0]);
                    if (source == null) {
                        Console.showMessage("No faction id or market name found with text of '" + tmp[0] + "'");
                        return CommandResult.ERROR;
                    }
                }
            }

            Object target = null;
            if (tmp.length >= 2) {
                target = CommandUtils.findBestFactionMatch(tmp[1]);
                if (target == null) {
                    target = CommandUtils.findBestMarketMatch(tmp[1]);
                    if (target == null) {
                        Console.showMessage("No faction id or market name found with text of '" + tmp[1] + "'");
                        return CommandResult.ERROR;
                    }
                }
            }

            SpecialItemSpecAPI item = null;
            if (tmp.length >= 3) {
                item = Global.getSettings().getSpecialItemSpec(tmp[2]);
                if (item == null) {
                    Console.showMessage("No colony item with Id '" + tmp[2] + "'");
                    return CommandResult.ERROR;
                }
            }

            new IncursionFleetIntel(source, target, item);
        }

        return CommandResult.SUCCESS;
    }
}
