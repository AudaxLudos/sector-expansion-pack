package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCAptitudeSection;
import second_in_command.specs.SCBaseAptitudePlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AptitudePurist extends SCBaseAptitudePlugin {
    public static String PRIMARY_SHIP_DESIGN_TYPE_KEY = "sep_sic_primary_ship_design_type_key";
    public static String SECONDARY_SHIP_DESIGN_TYPE_KEY = "sep_sic_secondary_ship_design_type_key";
    public static String NON_COMMON_SHIP_DESIGN_TYPE_COUNT_KEY = "sep_sic_non_common_design_type_count_key";

    public static Map<String, Integer> getCountByShipDesignType(SCData data) {
        Map<String, Integer> result = new HashMap<>();

        for (FleetMemberAPI member : data.getFleet().getFleetData().getMembersListCopy()) {
            ShipHullSpecAPI spec = member.getVariant().getHullSpec();
            String designType = spec.getManufacturer();
            int count = 0;
            if (result.containsKey(designType)) {
                count = result.get(designType);
            }
            count++;
            result.put(designType, count);
        }

        return result;
    }

    public static String getPrimaryShipDesignType(SCData data) {
        String result = (String) data.getFleet().getFleetData().getCacheClearedOnSync().get(PRIMARY_SHIP_DESIGN_TYPE_KEY);
        if (result != null) {
            return result;
        }

        Map<String, Integer> countByDesignType = getCountByShipDesignType(data);
        Map.Entry<String, Integer> type = countByDesignType.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (type != null) {
            result = type.getKey();
        }
        data.getFleet().getFleetData().getCacheClearedOnSync().put(PRIMARY_SHIP_DESIGN_TYPE_KEY, result);

        return result;
    }

    public static String getSecondaryShipDesignType(SCData data) {
        String result = (String) data.getFleet().getFleetData().getCacheClearedOnSync().get(SECONDARY_SHIP_DESIGN_TYPE_KEY);
        if (result != null) {
            return result;
        }

        String primaryType = getPrimaryShipDesignType(data);
        Map<String, Integer> countByDesignType = getCountByShipDesignType(data);
        Map.Entry<String, Integer> type = countByDesignType.entrySet().stream().filter(t -> !Objects.equals(t.getKey(), primaryType)).max(Map.Entry.comparingByValue()).orElse(null);
        if (type != null) {
            result = type.getKey();
        }
        data.getFleet().getFleetData().getCacheClearedOnSync().put(SECONDARY_SHIP_DESIGN_TYPE_KEY, result);

        return result;
    }

    public static int getNonCommonShipDesignTypeCount(SCData data) {
        Integer result = (Integer) data.getFleet().getFleetData().getCacheClearedOnSync().get(NON_COMMON_SHIP_DESIGN_TYPE_COUNT_KEY);
        if (result != null) {
            return result;
        }

        result = 0;
        String primaryType = getPrimaryShipDesignType(data);
        String secondaryType = getSecondaryShipDesignType(data);
        Map<String, Integer> countByDesignType = getCountByShipDesignType(data);

        for (Map.Entry<String, Integer> entry : countByDesignType.entrySet()) {
            if (Objects.equals(primaryType, entry.getKey())) {
                continue;
            }
            if (data.getAllActiveSkillsPlugins().stream().anyMatch(s -> Objects.equals(s.getId(), "sep_sic_design_compromise"))
                    && Objects.equals(secondaryType, entry.getKey())) {
                continue;
            }
            result++;
        }

        data.getFleet().getFleetData().getCacheClearedOnSync().put(NON_COMMON_SHIP_DESIGN_TYPE_COUNT_KEY, result);

        return result;
    }

    @Override
    public String getOriginSkillId() {
        return "sep_sic_cohesive_formation";
    }

    @Override
    public void createSections() {
        SCAptitudeSection section1 = new SCAptitudeSection(true, 0, "industry1");
        section1.addSkill("sep_sic_baseline_restoration");
        section1.addSkill("sep_sic_compact_profile");
        section1.addSkill("sep_sic_standard_repairs");
        section1.addSkill("sep_sic_unified_logistics");
        addSection(section1);

        SCAptitudeSection section2 = new SCAptitudeSection(true, 0, "industry1");
        section2.addSkill("sep_sic_efficient_operations");
        section2.addSkill("sep_sic_equipment_familiarity");
        addSection(section2);

        SCAptitudeSection section3 = new SCAptitudeSection(true, 0, "industry1");
        section3.addSkill("sep_sic_design_compromise");
        addSection(section3);
    }

    @Override
    public Float getNPCFleetSpawnWeight(SCData scData, CampaignFleetAPI campaignFleetAPI) {
        return 0f;
    }

    @Override
    public void addCodexDescription(TooltipMakerAPI tooltip) {
        tooltip.addPara(
                "The %s aptitude rewards fleets built around a single design type, granting broad improvements to ships with the most common design type. " +
                        "The presence of ships with other design types reduces the effectiveness of such improvements. " +
                        "As a result, the aptitude favors a disciplined fleet composition and is well suited to captains seeking to enforce consistent fleet standards.", 0f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                "Purist");
    }
}
