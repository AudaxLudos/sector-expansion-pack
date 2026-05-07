package sectorexpansionpack.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;
import sectorexpansionpack.Settings;
import sectorexpansionpack.intel.raid.AcquisitionRaidIntel;
import sectorexpansionpack.missions.EntityFinderMission;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StartAcquisitionEvent implements BaseCommandWithSuggestion {
    @Override
    public List<String> getSuggestions(int i, List<String> list, CommandContext commandContext) {
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

        String specialItemId = null;
        if (tmp.length >= 3) {
            specialItemId = tmp[2];
        }

        MarketAPI sourceM = pickSource(source);
        if (sourceM == null) {
            Console.showMessage("No source market found");
            return CommandResult.ERROR;
        }

        MarketAPI targetM = pickTarget(target, sourceM);
        if (targetM == null) {
            Console.showMessage("No target market found");
            return CommandResult.ERROR;
        }

        SpecialItemSpecAPI specialItem = pickSpecialItem(specialItemId, sourceM, targetM);
        if (specialItem == null) {
            Console.showMessage("No special item found at target market");
            return CommandResult.ERROR;
        }


        new AcquisitionRaidIntel(sourceM, targetM, specialItem);

        return CommandResult.SUCCESS;
    }

    protected MarketAPI pickSource(Object object) {
        EntityFinderMission efm = new EntityFinderMission();
        if (object instanceof FactionAPI f) {
            efm.requireMarketFaction(f.getId());
        } else if (object instanceof MarketAPI m) {
            efm.requireMarketIs(m);
        } else {
            efm.preferMarketMilitary();
        }
        efm.requireMarketNotHidden();
        efm.requireMarketFactionNotPlayer();
        return efm.pickMarket();
    }

    protected MarketAPI pickTarget(Object object, MarketAPI source) {
        EntityFinderMission efm = new EntityFinderMission();
        if (object instanceof FactionAPI f) {
            efm.requireMarketFaction(f.getId());
        } else if (object instanceof MarketAPI m) {
            efm.requireMarketIs(m);
        } else {
            efm.requireMarketFactionNot(source.getFactionId());
        }
        efm.requireMarketFactionNotPlayer();
        efm.requireMarketNotHidden();
        efm.requireMarketHasCompatibleSpecialItemsWithOther(source);
        return efm.pickMarket();
    }

    public SpecialItemSpecAPI pickSpecialItem(String specialItemId, MarketAPI source, MarketAPI target) {
        WeightedRandomPicker<SpecialItemData> picker = new WeightedRandomPicker<>();
        for (Industry targetInd : target.getIndustries()) {
            SpecialItemData otherData = targetInd.getSpecialItem();
            if (otherData != null) {
                for (Industry ind : source.getIndustries()) {
                    if (!Settings.COLONY_ITEM_WHITELIST.contains(otherData.getId())) {
                        continue;
                    }
                    if (Objects.equals(otherData.getId(), specialItemId)) {
                        return Global.getSettings().getSpecialItemSpec(otherData.getId());
                    }
                    if (ind.wantsToUseSpecialItem(otherData)) {
                        picker.add(otherData);
                    }
                }
            }
        }

        SpecialItemData data = picker.pick();

        return Global.getSettings().getSpecialItemSpec(data.getId());
    }
}
