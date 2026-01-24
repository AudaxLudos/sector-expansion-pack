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

public class StandardRepairs extends SCBaseSkillPlugin {
    public static float REPAIR_RATE_PER_DAY_MULT = 0.40f;
    public static float CR_RECOVERY_RATE_PER_DAY_MOD = 0.05f;

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

        tooltip.addPara("%s (Max: %s) hull and armor repair rate outside of combat", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(REPAIR_RATE_PER_DAY_MULT * bonusMult* penaltyMult * 100f) + "%", Math.round(REPAIR_RATE_PER_DAY_MULT* bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) flat increase to combat readiness recovered per day", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(CR_RECOVERY_RATE_PER_DAY_MOD * bonusMult* penaltyMult * 100f) + "%", Math.round(CR_RECOVERY_RATE_PER_DAY_MOD* bonusMult * 100f) + "%");

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

            stats.getRepairRatePercentPerDay().modifyPercent(id, REPAIR_RATE_PER_DAY_MULT * bonusMult * penaltyMult);
            stats.getBaseCRRecoveryRatePercentPerDay().modifyFlat(id, CR_RECOVERY_RATE_PER_DAY_MOD * bonusMult * penaltyMult);
        }
    }
}
