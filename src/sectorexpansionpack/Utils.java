package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.impl.campaign.CryosleeperEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SleeperPodsSpecial;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.util.Misc;

public class Utils {
    public void RunCodeScripts() {
        for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
            OfficerLevelupPlugin plugin = (OfficerLevelupPlugin) Global.getSettings().getPlugin("officerLevelUp");
            System.out.println("Max Level : " + plugin.getMaxLevel(officer.getPerson()));
            for (String key : officer.getPerson().getMemoryWithoutUpdate().getKeys()) {
                System.out.println(key + " : " + officer.getPerson().getMemoryWithoutUpdate().get(key));
            }
        }

        //import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SleeperPodsSpecial;
        for (SectorEntityToken entity : Global.getSector().getEntitiesWithTag(Tags.SALVAGEABLE)) {
            if (Misc.getSalvageSpecial(entity) instanceof SleeperPodsSpecial.SleeperPodsSpecialData) {
                SleeperPodsSpecial.SleeperPodsSpecialData data = (SleeperPodsSpecial.SleeperPodsSpecialData) Misc.getSalvageSpecial(entity);
                if (data.officer != null && data.officer.getMemoryWithoutUpdate().getBoolean(MemFlags.EXCEPTIONAL_SLEEPER_POD_OFFICER)) {
                    System.out.println("System : " + entity.getStarSystem().getName());
                    if (entity.getOrbitFocus() != null) {
                        System.out.println(entity.getOrbitFocus().getName());
                    }
                }
            }
        }
    }
}
