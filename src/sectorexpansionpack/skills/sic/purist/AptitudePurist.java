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
    public static String PURIST_FLEET_DATA_KEY = "sep_purist_fleet_data_key";
    public static float AVERAGE_DESIGN_TYPE_NEEDED = 0.5f;
    public static float SKILL_EFFECT_REDUCTION_MULT = 0.1f;

    private static PuristFleetData computePuristFleetData(SCData data) {
        PuristFleetData puristData = new PuristFleetData();
        Map<String, Integer> counts = new HashMap<>();
        float totalShips = 0;

        for (FleetMemberAPI member : data.getFleet().getFleetData().getMembersListCopy()) {
            String type = member.getVariant().getHullSpec().getManufacturer();
            counts.merge(type, 1, Integer::sum);
            totalShips++;
        }

        puristData.primary = counts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        puristData.secondary = counts.entrySet()
                .stream()
                .filter(e -> !Objects.equals(e.getKey(), puristData.primary))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        puristData.hasDesignCompromise = data.getAllActiveSkillsPlugins()
                .stream()
                .anyMatch(s -> Objects.equals(s.getId(), "sep_sic_design_compromise"));
        puristData.hasDoctrineExtremism = data.getAllActiveSkillsPlugins()
                .stream()
                .anyMatch(s -> Objects.equals(s.getId(), "sep_sic_doctrine_extremism"));

        int nonCommonTypeCount = 0;
        for (String type : counts.keySet()) {
            if (Objects.equals(type, puristData.primary)) {
                continue;
            }
            if (puristData.hasDesignCompromise && Objects.equals(type, puristData.secondary)) {
                continue;
            }
            nonCommonTypeCount++;
        }
        puristData.nonCommonTypeCount = nonCommonTypeCount;
        puristData.nonCommonTypePenalty = nonCommonTypeCount * SKILL_EFFECT_REDUCTION_MULT;

        int effectivePrimaryCount = counts.getOrDefault(puristData.primary, 0);
        if (puristData.hasDesignCompromise && puristData.secondary != null) {
            effectivePrimaryCount += counts.getOrDefault(puristData.secondary, 0);
        }
        float ratio = totalShips > 0 ? (float) effectivePrimaryCount / totalShips : 0f;
        puristData.otherTypeDominancePenalty = ratio >= AVERAGE_DESIGN_TYPE_NEEDED ? 0f : SKILL_EFFECT_REDUCTION_MULT;

        return puristData;
    }

    public static PuristFleetData getPuristFleetData(SCData data) {
        Object cached = data.getFleet().getFleetData()
                .getCacheClearedOnSync()
                .get(PURIST_FLEET_DATA_KEY);

        if (cached instanceof PuristFleetData) {
            return (PuristFleetData) cached;
        }

        PuristFleetData stats = computePuristFleetData(data);
        data.getFleet().getFleetData()
                .getCacheClearedOnSync()
                .put(PURIST_FLEET_DATA_KEY, stats);

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

        SCAptitudeSection section2 = new SCAptitudeSection(true, 2, "industry1");
        section2.addSkill("sep_sic_efficient_operations");
        section2.addSkill("sep_sic_equipment_familiarity");
        section2.addSkill("sep_sic_targeting_synergy");
        addSection(section2);

        SCAptitudeSection section3 = new SCAptitudeSection(false, 4, "industry1");
        section3.addSkill("sep_sic_design_compromise");
        section3.addSkill("sep_sic_doctrine_extremism");
        addSection(section3);
    }

    @Override
    public Float getNPCFleetSpawnWeight(SCData scData, CampaignFleetAPI campaignFleetAPI) {
        return 10f;
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

    public static class PuristFleetData {
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
