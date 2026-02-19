package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class SynchronizedDrives extends SCBaseSkillPlugin {
    public static float MAX_BURN_MOD = 2;
    public static float ACCELERATION_MULT = 1f;
    public static float MOVE_SLOW_SPEED_MOD = 3f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        tooltip.addPara("%s (Max: %s) skill efficiency*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), Math.round(pData.totalMult * 100f) + "%", Math.round(pData.bonusMultMax * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Based value of %s (Max: %s) due to %s primary design type", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getDesignTypeColor(pData.primary)}, Math.round(pData.bonusMult * 100f) + "%", Math.round(pData.bonusMultMax * 100f) + "%", pData.primary);
        tooltip.addPara("Reduced by %s due to %s other design types in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(pData.nonCommonTypePenalty * 100f) + "%", pData.nonCommonTypeCount + "");
        tooltip.addPara("Reduced by %s due to the dominance of other design types", 0f, Misc.getNegativeHighlightColor(), Math.round(pData.otherTypeDominancePenalty * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) maximum burn level", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(pData.totalMult * MAX_BURN_MOD), "" + Math.round(pData.bonusMultMax * MAX_BURN_MOD));
        tooltip.addPara("%s (Max: %s) maneuverability for the fleet outside of combat", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(pData.totalMult * ACCELERATION_MULT * 100f) + "%", Math.round(pData.bonusMultMax * ACCELERATION_MULT * 100f) + "%");
        tooltip.addPara("%s (Max: %s) burn level at which the fleet is considered to be moving slowly*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(pData.totalMult * MOVE_SLOW_SPEED_MOD), Math.round(pData.bonusMultMax * MOVE_SLOW_SPEED_MOD) + "");

        String statReductionMultText = Math.round(AptitudePurist.SKILL_EFFECT_REDUCTION_MULT * 100f) + "%";
        String dominantFractionText = Math.round(AptitudePurist.AVERAGE_DESIGN_TYPE_NEEDED * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The highest number of ships with the same design type will be the most common type" +
                ". If there is a tie, the type is chosen alphabetically" +
                ". Each design type other than the most common reduces skill efficiency by " + statReductionMultText +
                ". If " + dominantFractionText + " of the fleet is not the most common type skill efficiency is reduced by " + statReductionMultText, Misc.getGrayColor(), 10f);
        label.setHighlight(statReductionMultText, dominantFractionText, statReductionMultText);
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        data.getFleet().getStats().getFleetwideMaxBurnMod().modifyFlat(getId(), (float) Math.round(pData.totalMult * MAX_BURN_MOD), "Synchronized Drives");
        data.getFleet().getStats().getAccelerationMult().modifyMult(getId(), 1f + (pData.totalMult * ACCELERATION_MULT), "Synchronized Drives");
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(getId(), pData.totalMult * MOVE_SLOW_SPEED_MOD, "Synchronized Drives");
    }

    @Override
    public void onActivation(SCData data) {
        AptitudePurist.PuristFleetData pData = AptitudePurist.getPuristFleetData(data);

        data.getFleet().getStats().getFleetwideMaxBurnMod().modifyFlat(getId(), (float) Math.round(pData.totalMult * MAX_BURN_MOD), "Synchronized Drives");
        data.getFleet().getStats().getAccelerationMult().modifyMult(getId(), 1f + (pData.totalMult * ACCELERATION_MULT), "Synchronized Drives");
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(getId(), pData.totalMult * MOVE_SLOW_SPEED_MOD, "Synchronized Drives");
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getFleet().getStats().getFleetwideMaxBurnMod().unmodify(getId());
        data.getFleet().getStats().getAccelerationMult().unmodify(getId());
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).unmodify(getId());
    }
}
