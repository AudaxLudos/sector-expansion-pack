package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;
import java.util.Objects;

public class EquipmentFamiliarity extends SCBaseSkillPlugin {
    public static float CREW_MIN_REQ_MULT = 0.2f;
    public static float PEAK_PERFORMANCE_TIME_MULT = 0.25f;
    public static float MAX_COMBAT_READINESS_MOD = 0.15f;

    @Override
    public String getAffectsString() {
        return "ships with the most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);
        float penaltyMult = designData.computeTotalPenaltyMult();
        float bonusMult = designData.getDoctrineExtremismMult();
        String typeText = designData.nonCommonTypeCount > 1 ? "types" : "type";

        tooltip.addPara("The most common design type is %s*", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(designData.primary), designData.primary);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill effects are reduced by %s due to %s other design " + typeText + " in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.nonCommonTypePenalty * bonusMult * 100f) + "%", designData.nonCommonTypeCount + "");
        tooltip.addPara("Skill effects are reduced by a further %s due to the dominance of other design types", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(designData.otherTypeDominancePenalty * bonusMult * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) minimum crew requirements", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "-" + Math.round(CREW_MIN_REQ_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(CREW_MIN_REQ_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) peak performance time", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(PEAK_PERFORMANCE_TIME_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(PEAK_PERFORMANCE_TIME_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) max combat readiness", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(MAX_COMBAT_READINESS_MOD * bonusMult * penaltyMult * 100f) + "%", Math.round(MAX_COMBAT_READINESS_MOD * bonusMult * 100f) + "%");

        String statReductionMultText = Math.round(AptitudePurist.SKILL_EFFECT_REDUCTION_MULT * 100f) + "%";
        String dominantFractionText = Math.round(AptitudePurist.AVERAGE_DESIGN_TYPE_NEEDED * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The highest number of ships with the same design type will be the most common type. " +
                "If there is a tie, the type is chosen alphabetically. Each different design type other than the most common incurs a "
                + statReductionMultText + " penalty." + " At least " + dominantFractionText + " of the fleet must share the most common type to avoid the "
                + statReductionMultText + " dominated penalty.", Misc.getGrayColor(), 10f);
        label.setHighlight(statReductionMultText, dominantFractionText, statReductionMultText);
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        String variantType = variant.getHullSpec().getManufacturer();
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);

        if (Objects.equals(variantType, designData.primary) || (designData.hasDesignCompromise && Objects.equals(variantType, designData.secondary))) {
            float penaltyMult = designData.computeTotalPenaltyMult();
            float bonusMult = designData.getDoctrineExtremismMult();

            stats.getMinCrewMod().modifyMult(getId(), 1f - CREW_MIN_REQ_MULT * bonusMult * penaltyMult);
            stats.getPeakCRDuration().modifyMult(getId(), 1f + PEAK_PERFORMANCE_TIME_MULT * bonusMult * penaltyMult);
            stats.getMaxCombatReadiness().modifyFlat(getId(), MAX_COMBAT_READINESS_MOD * bonusMult * penaltyMult, "Design Commonality");
        }
    }
}
