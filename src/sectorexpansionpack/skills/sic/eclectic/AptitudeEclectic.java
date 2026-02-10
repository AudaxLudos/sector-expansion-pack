package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;
import second_in_command.specs.SCAptitudeSection;
import second_in_command.specs.SCBaseAptitudePlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AptitudeEclectic extends SCBaseAptitudePlugin {
    public static String ECLECTIC_FLEET_DATA_KEY = "sep_eclectic_fleet_data_key";
    public static int DESIGN_TYPE_SHIP_LIMIT = 3;
    public static float SKILL_EFFECT_MAX_MULT = 1f;
    public static float SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT = 0.2f;
    public static float SKILL_EFFECT_REDUCTION_MULT = 0.2f;

    private static EclecticFleetData computeEclecticFleetData(SCData data) {
        EclecticFleetData eclecticData = new EclecticFleetData();
        Map<String, Integer> counts = new HashMap<>();

        for (FleetMemberAPI member : data.getFleet().getFleetData().getMembersListCopy()) {
            String type = member.getVariant().getHullSpec().getManufacturer();
            counts.merge(type, 1, Integer::sum);
        }

        eclecticData.designTypes = counts.size();
        eclecticData.hasDesignTolerance = data.getAllActiveSkillsPlugins()
                .stream()
                .anyMatch(s -> Objects.equals(s.getId(), "sep_sic_design_tolerance"));
        eclecticData.hasDiverseFleet = data.getAllActiveSkillsPlugins()
                .stream()
                .anyMatch(s -> Objects.equals(s.getId(), "sep_sic_diverse_fleet"));

        int designTypesShipLimit = DESIGN_TYPE_SHIP_LIMIT;
        if (eclecticData.hasDiverseFleet) {
            designTypesShipLimit = DiverseFleet.DESIGN_TYPE_SHIP_LIMIT;
        } else if (eclecticData.hasDesignTolerance) {
            designTypesShipLimit = DesignTolerance.DESIGN_TYPE_SHIP_LIMIT;
        }

        eclecticData.designTypesAboveLimit = 0;
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() > designTypesShipLimit) {
                eclecticData.designTypesAboveLimit++;
            }
        }

        return eclecticData;
    }

    public static EclecticFleetData getEclecticFleetData(SCData data) {
        Object cached = data.getFleet().getFleetData()
                .getCacheClearedOnSync()
                .get(ECLECTIC_FLEET_DATA_KEY);

        if (cached instanceof EclecticFleetData) {
            return (EclecticFleetData) cached;
        }

        EclecticFleetData stats = computeEclecticFleetData(data);
        data.getFleet().getFleetData()
                .getCacheClearedOnSync()
                .put(ECLECTIC_FLEET_DATA_KEY, stats);

        return stats;
    }

    @Override
    public String getOriginSkillId() {
        return "sep_sic_shared_telemetry";
    }

    @Override
    public void createSections() {
        SCAptitudeSection section1 = new SCAptitudeSection(true, 0, "industry1");
        section1.addSkill("sep_sic_alternative_replacements");
        section1.addSkill("sep_sic_every_nook_and_cranny");
        section1.addSkill("sep_sic_improvised_restoration");
        section1.addSkill("sep_sic_joint_reclamation");
        section1.addSkill("sep_sic_logistics_reallocation");
        addSection(section1);

        SCAptitudeSection section2 = new SCAptitudeSection(true, 2, "industry1");
        section2.addSkill("sep_sic_collaborative_training");
        section2.addSkill("sep_sic_flux_optimizations");
        addSection(section2);

        SCAptitudeSection section3 = new SCAptitudeSection(false, 4, "industry1");
        section3.addSkill("sep_sic_design_tolerance");
        section3.addSkill("sep_sic_diverse_fleet");
        addSection(section3);
    }

    @Override
    public Float getNPCFleetSpawnWeight(SCData scData, CampaignFleetAPI campaignFleetAPI) {
        return 0f;
    }

    @Override
    public void addCodexDescription(TooltipMakerAPI tooltip) {
        tooltip.addPara("test", 0f);
    }

    public static class EclecticFleetData {
        int designTypes = 0;
        int designTypesAboveLimit = 0;
        boolean hasDesignTolerance = false;
        boolean hasDiverseFleet = false;

        public float getSkillEffectBonus() {
            return this.designTypes * SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT;
        }

        public float getSkillEffectPenalty() {
            return this.designTypesAboveLimit * SKILL_EFFECT_REDUCTION_MULT;
        }

        public float getSkillEffectTotal() {
            return Math.min(getMaxSkillEffectMult(), getSkillEffectBonus() - getSkillEffectPenalty());
        }

        public float getMaxSkillEffectMult() {
            return !this.hasDiverseFleet ? SKILL_EFFECT_MAX_MULT : DiverseFleet.SKILL_EFFECT_MAX_MULT;
        }

        public int getDesignTypeShipLimit() {
            if (this.hasDiverseFleet) {
                return DiverseFleet.DESIGN_TYPE_SHIP_LIMIT;
            }
            if (this.hasDesignTolerance) {
                return DesignTolerance.DESIGN_TYPE_SHIP_LIMIT;
            }
            return DESIGN_TYPE_SHIP_LIMIT;
        }
    }
}
