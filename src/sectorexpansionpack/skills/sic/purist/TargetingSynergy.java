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

public class TargetingSynergy extends SCBaseSkillPlugin {
    public static float NON_MISSILE_WEAPON_RANGE_MULT = 0.05f;
    public static float NON_MISSILE_WEAPON_DAMAGE_MULT = 0.05f;

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

        tooltip.addPara("%s (Max: %s) non-missile weapon range", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(NON_MISSILE_WEAPON_RANGE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(NON_MISSILE_WEAPON_RANGE_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) non-missile weapon damage", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(NON_MISSILE_WEAPON_DAMAGE_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(NON_MISSILE_WEAPON_DAMAGE_MULT * bonusMult * 100f) + "%");

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

            stats.getBallisticWeaponRangeBonus().modifyMult(getId(), 1f + (NON_MISSILE_WEAPON_RANGE_MULT * bonusMult * penaltyMult));
            stats.getBallisticWeaponDamageMult().modifyMult(getId(), 1f + (NON_MISSILE_WEAPON_DAMAGE_MULT * bonusMult * penaltyMult));

            stats.getEnergyWeaponRangeBonus().modifyMult(getId(), 1f + (NON_MISSILE_WEAPON_RANGE_MULT * bonusMult * penaltyMult));
            stats.getEnergyWeaponDamageMult().modifyMult(getId(), 1f + (NON_MISSILE_WEAPON_DAMAGE_MULT * bonusMult * penaltyMult));
        }
    }
}
