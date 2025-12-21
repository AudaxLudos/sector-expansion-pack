package sectorexpansionpack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import sectorexpansionpack.intel.misc.ArtifactInstallationIntel;
import sectorexpansionpack.missions.hub.SEPHubMissionWithBarEvent;

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

    public static void findMarketToInstallSpecialItem(SEPHubMissionWithBarEvent efm, MarketAPI source, SpecialItemData data, Logger log) {
        findMarketToInstallSpecialItem(efm, source.getFactionId(), source, data, log);
    }

    public static void findMarketToInstallSpecialItem(SEPHubMissionWithBarEvent efm, String factionId, MarketAPI source, SpecialItemData data, Logger log) {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(data.getId());
        efm.resetSearch();
        efm.requireMarketFaction(factionId);
        efm.requireMarketNotHidden();
        efm.requireMarketNotInHyperspace();
        efm.requireMarketFactionNotPlayer();
        efm.requireMarketCanUseSpecialItem(data);
        efm.preferMarketSizeAtMost(100);
        efm.preferMarketIs(source);
        MarketAPI market = efm.pickMarket();
        if (market == null) {
            log.info("Failed to find market to install special item");
            return;
        }

        Industry ind = Utils.pickIndustryToInstallItem(market, data);
        if (ind == null) {
            log.info("Failed to find industry on market to install special item");
            return;
        }

        SpecialItemData prevSpecialItem = ind.getSpecialItem();
        if (prevSpecialItem != null) {
            findMarketToInstallSpecialItem(efm, market.getFactionId(), market, prevSpecialItem, log);
        }

        ind.setSpecialItem(data);
        new ArtifactInstallationIntel(market, ind, spec);
        log.info(String.format("Installing %s to %s facility %s %s in the %s",
                spec.getName(), ind.getCurrentName(), market.getOnOrAt(),
                market.getName(), market.getStarSystem().getNameWithLowercaseTypeShort()));
    }

    public static Industry pickIndustryToInstallItem(MarketAPI market, SpecialItemData specialItemData) {
        WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
        for (Industry industry : market.getIndustries()) {
            if (!industry.wantsToUseSpecialItem(specialItemData)) {
                continue;
            }
            if (Utils.canSpecialItemBeInstalled(specialItemData.getId(), industry)) {
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

    public static <E extends Enum<E>> boolean isInEnum(String value, Class<E> enumClass) {
        for (E e : enumClass.getEnumConstants()) {
            if (e.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNumeric(String str) {
        return str.matches("^-?\\d+(?:\\.\\d+)?$");
    }

    public static boolean canSpecialItemBeInstalled(String specialItemId, Industry industry) {
        InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(specialItemId);
        if (effect != null) {
            List<String> unmet = effect.getUnmetRequirements(industry);
            return unmet == null || unmet.isEmpty();
        }
        return true;
    }
}
