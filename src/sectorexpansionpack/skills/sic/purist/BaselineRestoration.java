package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;
import java.util.Objects;

public class BaselineRestoration extends SCBaseSkillPlugin {
    public static float SHIP_RECOVERY_MOD = 2f;
    public static float INSTANT_REPAIR_MOD = 0.25f;

    @Override
    public String getAffectsString() {
        return "ships with the most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        tooltip.addPara("%s skill efficiency*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), Math.round(pData.totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Based value of %s (Max: %s) due to %s primary design type", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getDesignTypeColor(pData.primary)}, Math.round(pData.bonusMult * 100f) + "%", Math.round(pData.bonusMultMax * 100f) + "%", pData.primary);
        tooltip.addPara("Reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(pData.nonCommonTypePenalty * 100f) + "%", pData.nonCommonTypeCount + "");
        tooltip.addPara("Reduced by %s due to the dominance of other design types", 0f, Misc.getNegativeHighlightColor(), Math.round(pData.otherTypeDominancePenalty * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s chance for ships to be recoverable if lost in combat (%s × skill efficiency)", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(),
                "+" + Math.round(pData.totalMult * SHIP_RECOVERY_MOD * 100f) + "%", Math.round(SHIP_RECOVERY_MOD * 100f) + "%");
        tooltip.addPara("%s of hull and armor damage taken repaired after combat ends, at no cost (%s × skill efficiency)", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(),
                "+" + Math.round(pData.totalMult * INSTANT_REPAIR_MOD * 100f) + "%", Math.round(INSTANT_REPAIR_MOD * 100f) + "%");

        String statReductionMultText = Math.round(pData.bonusMultMax * AptitudePurist.SKILL_EFFECT_REDUCTION_MULT * 100f) + "%";
        String dominantFractionText = Math.round(pData.bonusMultMax * AptitudePurist.AVERAGE_DESIGN_TYPE_NEEDED * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The highest number of ships with the same design type will be the most common type" +
                ". If there is a tie, the type is chosen alphabetically" +
                ". Each design type other than the most common reduces skill efficiency by " + statReductionMultText +
                ". If " + dominantFractionText + " of the fleet is not the most common type skill efficiency is reduced by " + statReductionMultText, Misc.getGrayColor(), 10f);
        label.setHighlight(statReductionMultText, dominantFractionText, statReductionMultText);
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        String variantType = variant.getHullSpec().getManufacturer();
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        if (Objects.equals(variantType, pData.primary) || (pData.hasDesignCompromise && Objects.equals(variantType, pData.secondary))) {
            stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(getId(), pData.totalMult * SHIP_RECOVERY_MOD);
            stats.getDynamic().getMod(Stats.INSTA_REPAIR_FRACTION).modifyFlat(id, pData.totalMult * INSTANT_REPAIR_MOD);
        }
    }
}
