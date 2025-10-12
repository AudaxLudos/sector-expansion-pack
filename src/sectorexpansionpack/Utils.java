package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SleeperPodsSpecial;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.intel.ExpeditionFleetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Utils {
    public static Random random = new Random();

    public static void setRandom(Random random) {
        Utils.random = random;
    }

    public static boolean rollProbability(float p) {
        return random.nextFloat() >= p;
    }

    public static boolean rollProbability(Random random, float p) {
        return random.nextFloat() >= p;
    }

    public static List<CustomCampaignEntityAPI> getNearbyEntitiesWithType(SectorEntityToken from, String entityType, float maxDist) {
        List<CustomCampaignEntityAPI> result = new ArrayList<>();
        for (Object other : from.getContainingLocation().getEntities(CustomCampaignEntityAPI.class)) {
            if (other instanceof CustomCampaignEntityAPI entity) {
                if (!Objects.equals(entity.getCustomEntityType(), entityType)) {
                    continue;
                }
                float dist = Misc.getDistance(from.getLocation(), entity.getLocation());
                if (dist <= maxDist) {
                    result.add(entity);
                }
            }
        }

        return result;
    }

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
            if (Misc.getSalvageSpecial(entity) instanceof SleeperPodsSpecial.SleeperPodsSpecialData data) {
                if (data.officer != null && data.officer.getMemoryWithoutUpdate().getBoolean(MemFlags.EXCEPTIONAL_SLEEPER_POD_OFFICER)) {
                    System.out.println("System : " + entity.getStarSystem().getName());
                    if (entity.getOrbitFocus() != null) {
                        System.out.println(entity.getOrbitFocus().getName());
                    }
                }
            }
        }

        //import sectorexpansionpack.intel.ExpeditionFleetManager;
        ExpeditionFleetManager test = ExpeditionFleetManager.getInstance();
        if (test != null) {
            System.out.println(test.getActiveCount());
        }
    }
}
