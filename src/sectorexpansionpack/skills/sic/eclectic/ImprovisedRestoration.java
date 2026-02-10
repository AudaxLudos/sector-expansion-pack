package sectorexpansionpack.skills.sic.eclectic;

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

public class ImprovisedRestoration extends SCBaseSkillPlugin {
    public static float SHIP_RECOVERY_MOD = 2f;
    public static float INSTANT_REPAIR_MOD = 0.5f;

    @Override
    public String getAffectsString() {
        return "all ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float bonusMult = eclecticData.getSkillEffectBonus();
        float maxMult = eclecticData.getMaxSkillEffectMult();
        float penaltyMult = eclecticData.getSkillEffectPenalty();
        float totalMult = eclecticData.getSkillEffectTotal();

        tooltip.addPara("Total skill efficiency is at %s*", 0f, Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Math.round(totalMult * 100f) + "%");
        tooltip.setBulletedListMode("   - ");
        tooltip.addPara("Skill efficiency is increased by %s due to %s design types in the fleet", 0f, new Color[]{Misc.getPositiveHighlightColor(), Misc.getHighlightColor()}, Math.round(bonusMult * 100f) + "%", eclecticData.designTypes + "");
        tooltip.addPara("Skill efficiency is reduced by %s due to %s design types above there ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(penaltyMult * 100f) + "%", eclecticData.designTypesAboveLimit + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) chance for ships to be recoverable if lost in combat", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * SHIP_RECOVERY_MOD * 100f) + "%", Math.round(maxMult * SHIP_RECOVERY_MOD * 100f) + "%");
        tooltip.addPara("%s (Max: %s) of hull and armor damage taken repaired after combat ends, at no cost", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * INSTANT_REPAIR_MOD * 100f) + "%", Math.round(maxMult * INSTANT_REPAIR_MOD * 100f) + "%");

        String designTypeShipLimit = eclecticData.getDesignTypeShipLimit() + "";
        String skillEfficiencyLimit = Math.round(eclecticData.getMaxSkillEffectMult() * 100f) + "%";
        String skillEfficiencyPerType = Math.round(AptitudeEclectic.SKILL_EFFECT_BONUS_PER_DESIGN_TYPE_MULT * 100f) + "%";
        LabelAPI label = tooltip.addPara("*Skill efficiency is capped at " + skillEfficiencyLimit +
                ". Every design type in the fleet increases skill efficiency by " + skillEfficiencyPerType +
                ". Every design type has a ship limit of " + designTypeShipLimit +
                ". Every design type above there ship limit reduces skill efficiency by " + skillEfficiencyPerType, Misc.getGrayColor(), 10f);
        label.setHighlight(skillEfficiencyLimit, skillEfficiencyPerType, designTypeShipLimit, skillEfficiencyPerType);
        label.setHighlightColors(Misc.getHighlightColor(), Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @Override
    public void applyEffectsBeforeShipCreation(SCData data, MutableShipStatsAPI stats, ShipVariantAPI variant, ShipAPI.HullSize hullSize, String id) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float effectMult = eclecticData.getSkillEffectTotal();

        stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(getId(), effectMult * SHIP_RECOVERY_MOD);
        stats.getDynamic().getMod(Stats.INSTA_REPAIR_FRACTION).modifyFlat(id, effectMult * INSTANT_REPAIR_MOD);
    }
}
