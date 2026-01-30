package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

import java.awt.*;

public class JointReclamation extends SCBaseSkillPlugin {
    public static float POST_BATTLE_SALVAGE_MOD = 0.4f;
    public static float FUEL_SALVAGE_BONUS = 0.4f;
    public static float NON_COMBAT_CREW_LOSS_MULT = 0.5f;

    @Override
    public String getAffectsString() {
        return "fleet";
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
        tooltip.addPara("Skill efficiency is reduced by %s due to %s design types above there ship limit", 0f, new Color[]{Misc.getNegativeHighlightColor(), Misc.getHighlightColor()}, Math.round(penaltyMult * 100f) + "%", eclecticData.designTypesAboveAverage + "");
        tooltip.setBulletedListMode(null);

        tooltip.addPara("%s (Max: %s) post-battle salvage", 10f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * POST_BATTLE_SALVAGE_MOD * 100f) + "%", Math.round(maxMult * POST_BATTLE_SALVAGE_MOD * 100f) + "%");
        tooltip.addPara("%s (Max: %s) fuel salvage", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * FUEL_SALVAGE_BONUS * 100f) + "%", Math.round(maxMult * FUEL_SALVAGE_BONUS * 100f) + "%");
        tooltip.addPara("%s (Max: %s) crew loss to non-combat operations", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), "+" + Math.round(totalMult * NON_COMBAT_CREW_LOSS_MULT * 100f) + "%", Math.round(maxMult * NON_COMBAT_CREW_LOSS_MULT * 100f) + "%");

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
    public void advance(SCData data, Float amount) {
        AptitudeEclectic.EclecticFleetData eclecticData = AptitudeEclectic.getEclecticFleetData(data);
        float effectMult = eclecticData.getSkillEffectTotal();

        data.getFleet().getStats().getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).modifyFlat(getId(), effectMult * POST_BATTLE_SALVAGE_MOD);
        data.getFleet().getStats().getDynamic().getStat(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET).modifyFlat(getId(), effectMult * FUEL_SALVAGE_BONUS);
        data.getFleet().getStats().getDynamic().getStat(Stats.NON_COMBAT_CREW_LOSS_MULT).modifyMult(getId(), 1f - effectMult * NON_COMBAT_CREW_LOSS_MULT);
    }
}
