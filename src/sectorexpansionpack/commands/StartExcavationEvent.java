package sectorexpansionpack.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;
import sectorexpansionpack.Settings;
import sectorexpansionpack.Utils;
import sectorexpansionpack.intel.raid.ExcavationRaidIntel;
import sectorexpansionpack.intel.raid.ExcavationRaidIntelV2;
import sectorexpansionpack.missions.EntityFinderMission;

import java.util.ArrayList;
import java.util.List;

public class StartExcavationEvent implements BaseCommandWithSuggestion {
    @Override
    public List<String> getSuggestions(int i, List<String> list, CommandContext commandContext) {
        List<String> suggestions = new ArrayList<>();
        if (i == 0) {
            suggestions.addAll(Global.getSector().getEconomy().getMarketsCopy().stream().map(MarketAPI::getName).toList());
            suggestions.addAll(Global.getSector().getAllFactions().stream().map(FactionAPI::getId).toList());
        } else if (i == 1) {
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

        String specialItemId = null;
        if (tmp.length >= 2) {
            specialItemId = tmp[1];
        }

        MarketAPI sourceM = pickSource(source);
        if (sourceM == null) {
            Console.showMessage("No source market found");
            return CommandResult.ERROR;
        }

        SectorEntityToken targetM = pickTarget(sourceM);
        if (targetM == null) {
            Console.showMessage("No target market found");
            return CommandResult.ERROR;
        }

        SpecialItemSpecAPI specialItem = pickSpecialItem(specialItemId, sourceM);
        if (specialItem == null) {
            Console.showMessage("No special item found for source market");
            return CommandResult.ERROR;
        }

        new ExcavationRaidIntelV2(sourceM, targetM, specialItem);

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

    protected SectorEntityToken pickTarget(MarketAPI source) {
        EntityFinderMission efm = new EntityFinderMission();
        efm.requireSystemWithinRangeOf(source.getLocation(), 15);
        if (efm.rollProbability(ExcavationRaidIntelV2.WRECK_CHANCE)) {
            efm.requireEntityNoMemoryFlag(ExcavationRaidIntelV2.TARGET_KEY);
            efm.requireEntityNoSpecialSalvage();
            efm.requireEntityType(Entities.WRECK);
            efm.preferEntityInDirectionOfOtherMissions();
            efm.preferEntityUndiscovered();
            return efm.pickEntity();
        } else {
            efm.requirePlanetNoMemoryFlag(ExcavationRaidIntelV2.TARGET_KEY);
            efm.requirePlanetWithRuins();
            efm.requirePlanetUnexploredRuins();
            efm.preferPlanetInDirectionOfOtherMissions();
            return efm.pickPlanet();
        }
    }

    public SpecialItemSpecAPI pickSpecialItem(String specialItemId, MarketAPI source) {
        if (specialItemId != null && !specialItemId.isBlank()) {
            return Global.getSettings().getSpecialItemSpec(specialItemId);
        }

        WeightedRandomPicker<SpecialItemSpecAPI> picker = new WeightedRandomPicker<>();
        for (String itemId : ItemEffectsRepo.ITEM_EFFECTS.keySet()) {
            if (Settings.COLONY_ITEM_WHITELIST.contains(itemId)) {
                continue;
            }

            SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(itemId);

            if (spec.getParams() == null || spec.getParams().isEmpty()) {
                continue;
            }
            float weight = 1f;
            for (Industry industry : source.getIndustries()) {
                if (!industry.wantsToUseSpecialItem(new SpecialItemData(spec.getId(), spec.getParams()))) {
                    continue;
                }
                if (Utils.canSpecialItemBeInstalled(spec.getId(), industry)) {
                    weight = 3f;
                }
            }
            picker.add(spec, weight);
        }

        return picker.pick();
    }
}
