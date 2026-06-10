package sectorexpansionpack.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;
import sectorexpansionpack.colonies.FactionColonyAI;
import sectorexpansionpack.colonies.actions.ColonyAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StartColonyAIAction implements BaseCommandWithSuggestion {
    @Override
    public List<String> getSuggestions(int i, List<String> list, CommandContext commandContext) {
        List<String> suggestions = new ArrayList<>();

        if (i == 0) {
            suggestions.addAll(Global.getSector().getEconomy().getMarketsCopy().stream().map(MarketAPI::getName).toList());
            suggestions.addAll(Global.getSector().getAllFactions().stream().map(FactionAPI::getId).toList());
        } else if (i == 1) {
            suggestions.addAll(FactionColonyAI.COLONY_ACTIONS.stream().map(ColonyAction::id).toList());
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

        MarketAPI market = null;
        if (tmp.length >= 1) {
            market = CommandUtils.findBestMarketMatch(tmp[0]);
            if (market == null) {
                Console.showMessage("No faction id or market name found with text of '" + tmp[0] + "'");
                return CommandResult.ERROR;
            }
        }

        if (tmp.length >= 2) {
            boolean actionValid = FactionColonyAI.COLONY_ACTIONS.stream().anyMatch(ca -> Objects.equals(ca.id(), tmp[1]));
            if (!actionValid) {
                Console.showMessage("No action found with id of '" + tmp[1] + "'");
                return CommandResult.ERROR;
            }
        }

        ColonyAction action = FactionColonyAI.COLONY_ACTIONS.stream().filter(ca -> Objects.equals(ca.id(), tmp[1])).findFirst().orElse(null);
        if (action == null) {
            Console.showMessage("No action found with id of '" + tmp[1] + "'");
            return CommandResult.ERROR;
        }

        if (!action.canPerform(market)) {
            Console.showMessage("Could not perform action at " + market.getName() + " in the " + market.getStarSystem().getNameWithLowercaseTypeShort());
            return CommandResult.ERROR;
        }

        action.perform(market);

        return CommandResult.SUCCESS;
    }
}
