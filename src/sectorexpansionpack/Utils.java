package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

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

    public static List<JumpPointAPI> getHyperspaceJumpPoints(StarSystemAPI system) {
        List<JumpPointAPI> results = new ArrayList<>();
        for (SectorEntityToken entity : Global.getSector().getHyperspace().getJumpPoints()) {
            JumpPointAPI jumpPoint = (JumpPointAPI) entity;
            if (jumpPoint.getDestinationStarSystem() == system) {
                results.add(jumpPoint);
            }
        }

        return results;
    }

    public static SectorEntityToken getClosestJumpPoint(SectorEntityToken from) {
        SectorEntityToken closest = null;
        float min = Float.MAX_VALUE;
        for (SectorEntityToken to : from.getContainingLocation().getJumpPoints()) {
            float dist = Misc.getDistance(from, to);
            if (min > dist) {
                min = dist;
                closest = to;
            }
        }
        return closest;
    }

    public static List<MissionScenarioSpec> getMissionScenarios(String missionId) {
        List<MissionScenarioSpec> results = new ArrayList<>();
        List<MissionScenarioSpec> specs = new ArrayList<>(Global.getSettings().getAllSpecs(MissionScenarioSpec.class));
        for (MissionScenarioSpec spec : specs) {
            if (Objects.equals(missionId, spec.getMissionId())) {
                results.add(spec);
            }
        }
        return results;
    }

    public static MissionScenarioSpec pickMissionScenario(String missionId, Random random) {
        WeightedRandomPicker<MissionScenarioSpec> picker = new WeightedRandomPicker<>(random);
        for (MissionScenarioSpec spec : getMissionScenarios(missionId)) {
            picker.add(spec, spec.getFrequency());
        }
        return picker.pick();
    }
}
