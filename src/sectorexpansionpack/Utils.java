package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.SleeperPodsSpecial;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import sectorexpansionpack.intel.ExpeditionFleetManager;
import sectorexpansionpack.intel.IncursionFleetIntel;
import sectorexpansionpack.intel.IncursionFleetManager;

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

    public static Industry pickIndustryToInstallItem(MarketAPI market, SpecialItemData specialItemData) {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
        for (Industry industry : market.getIndustries()) {
            if (industry.wantsToUseSpecialItem(specialItemData)) {
                industryPicker.add(industry);
            }
        }
        return industryPicker.pick();
    }
}
