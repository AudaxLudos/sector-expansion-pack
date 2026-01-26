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

public class UnifiedLogistics extends SCBaseSkillPlugin {
    public static float SUPPLIES_PER_MONTH_MULT = 0.25f;
    public static float FUEL_USE_MULT = 0.25f;
    public static float FUEL_CAP_MULT = 0.2f;
    public static float CARGO_CAP_MULT = 0.2f;

    @Override
    public String getAffectsString() {
        return "ships with the most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.PuristFleetData puristData = AptitudePurist.getPuristFleetData(data);
        float penaltyMult = puristData.computeTotalPenaltyMult();
        float bonusMult = puristData.getDoctrineExtremismMult();
        String typeText = puristData.nonCommonTypeCount > 1 ? "types" : "type";

        tooltip.addPara("The most common design type is %s*", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(puristData.primary), puristData.primary);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill effects are reduced by %s due to %s other design " + typeText + " in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(puristData.nonCommonTypePenalty * bonusMult * 100f) + "%", puristData.nonCommonTypeCount + "");
        tooltip.addPara("Skill effects are reduced by a further %s due to the dominance of other design types", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(puristData.otherTypeDominancePenalty * bonusMult * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) monthly supply consumption for ship maintenance", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(SUPPLIES_PER_MONTH_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(SUPPLIES_PER_MONTH_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel usage", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_USE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(FUEL_USE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) cargo capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(CARGO_CAP_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(CARGO_CAP_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel capacity", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(FUEL_CAP_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(FUEL_CAP_MULT * bonusMult * 100f) + "%");

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
        AptitudePurist.PuristFleetData puristData = AptitudePurist.getPuristFleetData(data);

        if (Objects.equals(variantType, puristData.primary) || (puristData.hasDesignCompromise && Objects.equals(variantType, puristData.secondary))) {
            float penaltyMult = puristData.computeTotalPenaltyMult();
            float bonusMult = puristData.getDoctrineExtremismMult();

            stats.getSuppliesPerMonth().modifyMult(getId(), 1f - SUPPLIES_PER_MONTH_MULT * bonusMult * penaltyMult);
            stats.getFuelUseMod().modifyMult(getId(), 1f - FUEL_USE_MULT * bonusMult * penaltyMult);
            stats.getFuelMod().modifyMult(getId(), 1f + FUEL_CAP_MULT * bonusMult * penaltyMult);
            stats.getCargoMod().modifyMult(getId(), 1f + CARGO_CAP_MULT * bonusMult * penaltyMult);
        }
    }
}
