package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
    public static String FLEET_DESIGN_STATS_KEY = "sep_fleet_design_stats";
    public static float AVERAGE_DESIGN_TYPE_NEEDED = 0.5f;
    public static float SKILL_EFFECT_REDUCTION_MULT = 0.1f;

    private static FleetDesignData computeFleetDesignStats(SCData data) {
        FleetDesignData stats = new FleetDesignData();
        Map<String, Integer> counts = new HashMap<>();
        float totalShips = 0;

        for (FleetMemberAPI member : data.getFleet().getFleetData().getMembersListCopy()) {
            String type = member.getVariant().getHullSpec().getManufacturer();
            counts.merge(type, 1, Integer::sum);
            totalShips++;
        }

        stats.primary = counts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        stats.secondary = counts.entrySet()
                .stream()
                .filter(e -> !Objects.equals(e.getKey(), stats.primary))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        stats.hasDesignCompromise = data.getAllActiveSkillsPlugins()
                .stream()
                .anyMatch(s -> Objects.equals(s.getId(), "sep_sic_design_compromise"));
        stats.hasDoctrineExtremism = data.getAllActiveSkillsPlugins()
                .stream()
                .anyMatch(s -> Objects.equals(s.getId(), "sep_sic_doctrine_extremism"));

        int nonCommonTypeCount = 0;
        for (String type : counts.keySet()) {
            if (Objects.equals(type, stats.primary)) {
                continue;
            }
            if (stats.hasDesignCompromise && Objects.equals(type, stats.secondary)) {
                continue;
            }
            nonCommonTypeCount++;
        }
        stats.nonCommonTypeCount = nonCommonTypeCount;
        stats.nonCommonTypePenalty = nonCommonTypeCount * SKILL_EFFECT_REDUCTION_MULT;

        int effectivePrimaryCount = counts.getOrDefault(stats.primary, 0);
        if (stats.hasDesignCompromise && stats.secondary != null) {
            effectivePrimaryCount += counts.getOrDefault(stats.secondary, 0);
        }
        float ratio = totalShips > 0 ? (float) effectivePrimaryCount / totalShips : 0f;
        stats.otherTypeDominancePenalty = ratio >= AVERAGE_DESIGN_TYPE_NEEDED ? 0f : SKILL_EFFECT_REDUCTION_MULT;

        return stats;
    }

    public static FleetDesignData getFleetDesignData(SCData data) {
        Object cached = data.getFleet().getFleetData()
                .getCacheClearedOnSync()
                .get(FLEET_DESIGN_STATS_KEY);

        if (cached instanceof FleetDesignData) {
            return (FleetDesignData) cached;
        }

        FleetDesignData stats = computeFleetDesignStats(data);
        data.getFleet().getFleetData()
                .getCacheClearedOnSync()
                .put(FLEET_DESIGN_STATS_KEY, stats);

        return stats;
    }

    @Override
    public String getOriginSkillId() {
        return "sep_sic_synchronized_drives";
    }

    @Override
    public void createSections() {
        SCAptitudeSection section1 = new SCAptitudeSection(true, 0, "industry1");
        section1.addSkill("sep_sic_baseline_restoration");
        section1.addSkill("sep_sic_harmonized_sensors");
        section1.addSkill("sep_sic_standard_repairs");
        section1.addSkill("sep_sic_unified_logistics");
        addSection(section1);

        SCAptitudeSection section2 = new SCAptitudeSection(true, 3, "industry1");
        section2.addSkill("sep_sic_efficient_operations");
        section2.addSkill("sep_sic_equipment_familiarity");
        addSection(section2);

        SCAptitudeSection section3 = new SCAptitudeSection(false, 5, "industry1");
        section3.addSkill("sep_sic_design_compromise");
        section3.addSkill("sep_sic_doctrine_extremism");
        addSection(section3);
    }

    @Override
    public Float getNPCFleetSpawnWeight(SCData scData, CampaignFleetAPI campaignFleetAPI) {
        return 1f;
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

    public static class FleetDesignData {
        String primary;
        String secondary;
        boolean hasDesignCompromise = false;
        boolean hasDoctrineExtremism = false;
        int nonCommonTypeCount;
        float nonCommonTypePenalty;
        float otherTypeDominancePenalty;

        float computeTotalPenaltyMult() {
            return 1f - (this.nonCommonTypePenalty + this.otherTypeDominancePenalty) * getDoctrineExtremismMult();
        }

        float getDoctrineExtremismMult() {
            float doctrineExtremismMult = 1f;
            if (this.hasDoctrineExtremism) {
                doctrineExtremismMult = 2f;
            }
            return doctrineExtremismMult;
        }
    }
}
