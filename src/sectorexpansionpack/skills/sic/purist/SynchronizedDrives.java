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
        AptitudePurist.PuristFleetData puristData = AptitudePurist.getPuristFleetData(data);
        float penaltyMult = puristData.computeTotalPenaltyMult();
        float bonusMult = puristData.getDoctrineExtremismMult();
        String typeText = puristData.nonCommonTypeCount > 1 ? "types" : "type";

        tooltip.addPara("The most common design type is %s*", 0f, Misc.getHighlightColor(), Misc.getDesignTypeColor(puristData.primary), puristData.primary);
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill effects are reduced by %s due to %s other design " + typeText + " in the fleet", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(puristData.nonCommonTypePenalty * bonusMult * 100f) + "%", puristData.nonCommonTypeCount + "");
        tooltip.addPara("Skill effects are reduced by a further %s due to the dominance of other design types", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(puristData.otherTypeDominancePenalty * bonusMult * 100f) + "%");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) maximum burn level", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(MAX_BURN_MOD * bonusMult * penaltyMult), "" + Math.round(MAX_BURN_MOD * bonusMult));
        tooltip.addPara("%s (Max: %s) maneuverability for the fleet outside of combat", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(ACCELERATION_MULT * bonusMult * penaltyMult * 100f) + "%", Math.round(ACCELERATION_MULT * bonusMult * 100f) + "%");
        tooltip.addPara("%s (Max: %s) burn level at which the fleet is considered to be moving slowly*", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(MOVE_SLOW_SPEED_MOD * bonusMult * penaltyMult), Math.round(MOVE_SLOW_SPEED_MOD * bonusMult) + "");

        String statReductionMultText = Math.round(AptitudePurist.SKILL_EFFECT_REDUCTION_MULT * 100f) + "%";
        String dominantFractionText = Math.round(AptitudePurist.AVERAGE_DESIGN_TYPE_NEEDED * 100f) + "%";
        LabelAPI label = tooltip.addPara("*The highest number of ships with the same design type will be the most common type. " +
                "If there is a tie, the type is chosen alphabetically. Each different design type other than the most common incurs a "
                + statReductionMultText + " penalty." + " At least " + dominantFractionText + " of the fleet must share the most common type to avoid the "
                + statReductionMultText + " dominated penalty.", Misc.getGrayColor(), 10f);
        label.setHighlight(statReductionMultText, dominantFractionText, statReductionMultText);
        label.setHighlightColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
        tooltip.addPara("*A slow moving fleet is harder to detect in some types of terrain, and can avoid some hazards. Some abilities also make the fleet move slowly when activated. A fleet is considered slow-moving at a burn level of half of its slowest ship.", 10f, Misc.getGrayColor(), Misc.getHighlightColor());
    }

    @Override
    public void advance(SCData data, Float amount) {
        AptitudePurist.PuristFleetData puristData = AptitudePurist.getPuristFleetData(data);
        float penaltyMult = puristData.computeTotalPenaltyMult();
        float bonusMult = puristData.getDoctrineExtremismMult();

        data.getFleet().getStats().getFleetwideMaxBurnMod().modifyFlat(getId(), (float) Math.round(MAX_BURN_MOD * bonusMult * penaltyMult), "Synchronized Drives");
        data.getFleet().getStats().getAccelerationMult().modifyMult(getId(), 1f + (ACCELERATION_MULT * bonusMult * penaltyMult), "Synchronized Drives");
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(getId(), MOVE_SLOW_SPEED_MOD * bonusMult * penaltyMult, "Synchronized Drives");
    }

    @Override
    public void onActivation(SCData data) {
        AptitudePurist.PuristFleetData puristData = AptitudePurist.getPuristFleetData(data);
        float penaltyMult = puristData.computeTotalPenaltyMult();
        float bonusMult = puristData.getDoctrineExtremismMult();

        data.getFleet().getStats().getFleetwideMaxBurnMod().modifyFlat(getId(), (float) Math.round(MAX_BURN_MOD * bonusMult * penaltyMult), "Synchronized Drives");
        data.getFleet().getStats().getAccelerationMult().modifyMult(getId(), 1f + (ACCELERATION_MULT * bonusMult * penaltyMult), "Synchronized Drives");
        data.getFleet().getStats().getDynamic().getMod(Stats.MOVE_SLOW_SPEED_BONUS_MOD).modifyFlat(getId(), MOVE_SLOW_SPEED_MOD * bonusMult * penaltyMult, "Synchronized Drives");
    }

    @Override
    public void onDeactivation(SCData data) {
        data.getFleet().getStats().getFleetwideMaxBurnMod().unmodify(getId());
        data.getFleet().getStats().getAccelerationMult().unmodify(getId());
    }
}
