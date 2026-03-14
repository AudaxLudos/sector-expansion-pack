package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCAptitudeSection;
import second_in_command.specs.SCBaseAptitudePlugin;
import second_in_command.specs.SCBaseSkillPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AptitudePurist extends SCBaseAptitudePlugin {
    public static final String PURIST_FLEET_DATA_KEY = "sep_purist_fleet_data_key";
    public static final float AVERAGE_DESIGN_TYPE_NEEDED = 0.5f;
    public static final float SKILL_EFFECT_MAX_MULT = 1f;
    public static final float SKILL_EFFECT_REDUCTION_MULT = 0.1f;

    private static PuristFleetData computePuristFleetData(SCData data) {
        PuristFleetData pData = new PuristFleetData();
        Map<String, Integer> counts = new HashMap<>();
        float totalShips = 0;

        for (SCBaseSkillPlugin skill : data.getAllActiveSkillsPlugins()) {
            if (Objects.equals(skill.getId(), "sep_sic_civilian_tolerance")) {
                pData.hasCivilianTolerance = true;
            } else if (Objects.equals(skill.getId(), "sep_sic_design_compromise")) {
                pData.hasDesignCompromise = true;
            } else if (Objects.equals(skill.getId(), "sep_sic_doctrine_extremism")) {
                pData.hasDoctrineExtremism = true;
            }
        }

        for (FleetMemberAPI member : data.getFleet().getFleetData().getMembersListCopy()) {
            String type = member.getVariant().getHullSpec().getManufacturer();
            if (pData.hasCivilianTolerance) {
                if (!member.getVariant().isCivilian()) {
                    counts.merge(type, 1, Integer::sum);
                    totalShips++;
                }
            } else {
                counts.merge(type, 1, Integer::sum);
                totalShips++;
            }
        }

        pData.primary = counts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        pData.secondary = counts.entrySet()
                .stream()
                .filter(e -> !Objects.equals(e.getKey(), pData.primary))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        pData.nonCommonTypeCount = 0;
        for (String type : counts.keySet()) {
            if (Objects.equals(type, pData.primary)) {
                continue;
            }
            if (pData.hasDesignCompromise && Objects.equals(type, pData.secondary)) {
                continue;
            }
            pData.nonCommonTypeCount++;
        }

        pData.bonusMultMax = !pData.hasDoctrineExtremism ? SKILL_EFFECT_MAX_MULT : DoctrineExtremism.SKILL_EFFECT_MAX_MULT;
        pData.bonusMult = 0f;
        if (pData.primary != null && !pData.primary.isBlank()) {
            pData.bonusMult = 1f;
        }
        pData.bonusMult *= pData.bonusMultMax;

        pData.nonCommonTypePenalty = pData.nonCommonTypeCount * SKILL_EFFECT_REDUCTION_MULT * pData.bonusMultMax;
        int effectivePrimaryCount = counts.getOrDefault(pData.primary, 0);
        if (pData.hasDesignCompromise && pData.secondary != null) {
            effectivePrimaryCount += counts.getOrDefault(pData.secondary, 0);
        }
        float ratio = totalShips > 0 ? (float) effectivePrimaryCount / totalShips : 0f;
        pData.otherTypeDominancePenalty = ratio >= AVERAGE_DESIGN_TYPE_NEEDED ? 0f : SKILL_EFFECT_REDUCTION_MULT * pData.bonusMultMax;
        pData.penaltyMult = pData.nonCommonTypePenalty + pData.otherTypeDominancePenalty;

        pData.totalMult = pData.bonusMult - pData.penaltyMult;

        return pData;
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
        return "sep_sic_optimized_engines";
    }

    @Override
    public void createSections() {
        SCAptitudeSection section1 = new SCAptitudeSection(true, 0, "industry1");
        section1.addSkill("sep_sic_baseline_restoration");
        section1.addSkill("sep_sic_civilian_tolerance");
        section1.addSkill("sep_sic_harmonized_sensors");
        section1.addSkill("sep_sic_standard_repairs");
        section1.addSkill("sep_sic_unified_logistics");
        addSection(section1);

        SCAptitudeSection section2 = new SCAptitudeSection(true, 2, "industry1");
        section2.addSkill("sep_sic_efficient_operations");
        section2.addSkill("sep_sic_targeting_synergy");
        section2.addSkill("sep_sic_tech_familiarity");
        addSection(section2);

        SCAptitudeSection section3 = new SCAptitudeSection(false, 4, "industry1");
        section3.addSkill("sep_sic_design_compromise");
        section3.addSkill("sep_sic_doctrine_extremism");
        addSection(section3);
    }

    @Override
    public Float getNPCFleetSpawnWeight(SCData scData, CampaignFleetAPI campaignFleetAPI) {
        PuristFleetData pData = getPuristFleetData(scData);
        if (pData.nonCommonTypeCount >= 3) {
            return 0f;
        } else if (pData.nonCommonTypeCount == 2) {
            return 0.50f;
        } else if (pData.nonCommonTypeCount == 1) {
            return 0.75f;
        }
        return 1f;
    }

    @Override
    public void addCodexDescription(TooltipMakerAPI tooltip) {
        tooltip.addPara(
                "The %s aptitude rewards captains who use only a single ship design type in their fleet, " +
                        "granting broad improvements to those ships. However, for every other design type present " +
                        "in the fleet, these improvements become less effective.",
                0f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                getName());
    }

    public static class PuristFleetData {
        String primary;
        String secondary;
        boolean hasCivilianTolerance = false;
        boolean hasDesignCompromise = false;
        boolean hasDoctrineExtremism = false;
        int nonCommonTypeCount;
        float nonCommonTypePenalty;
        float otherTypeDominancePenalty;

        float bonusMultMax;
        float bonusMult;
        float penaltyMult;
        float totalMult;
    }
}
