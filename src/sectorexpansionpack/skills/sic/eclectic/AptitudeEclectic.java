package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import second_in_command.SCData;
import second_in_command.specs.SCAptitudeSection;
import second_in_command.specs.SCBaseAptitudePlugin;

import java.util.HashMap;
import java.util.Map;

public class AptitudeEclectic extends SCBaseAptitudePlugin {
    public static String ECLECTIC_FLEET_DATA_KEY = "sep_eclectic_fleet_data_key";
    public static float SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT = 0.2f;
    public static float SKILL_EFFECT_REDUCTION_MULT = 0.2f;

    private static EclecticFleetData computeEclecticFleetData(SCData data) {
        EclecticFleetData eclecticData = new EclecticFleetData();
        Map<String, Integer> counts = new HashMap<>();
        float totalShips = 0;

        for (FleetMemberAPI member : data.getFleet().getFleetData().getMembersListCopy()) {
            String type = member.getVariant().getHullSpec().getManufacturer();
            counts.merge(type, 1, Integer::sum);
            totalShips++;
        }

        eclecticData.designTypes = counts.size();
        eclecticData.designTypesAverage = totalShips / counts.size();

        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() > eclecticData.designTypesAverage) {
                eclecticData.designTypesAboveAverage++;
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
        // SCAptitudeSection section1 = new SCAptitudeSection(true, 0, "industry1");
        // addSection(section1);
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
        int designTypes;
        float designTypesAverage;
        int designTypesAboveAverage = 0;

        public float getSkillEffectBonus() {
            return this.designTypes * SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT;
        }

        public float getSkillEffectPenalty() {
            return this.designTypesAboveAverage * SKILL_EFFECT_REDUCTION_MULT;
        }

        public float getSkillEffectTotal() {
            return getSkillEffectBonus() - getSkillEffectPenalty();
        }
    }
}
