package sectorexpansionpack.skills.sic.eclectic;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class DiverseFleet extends SCBaseSkillPlugin {
    public static int DESIGN_TYPE_SHIP_LIMIT = 2;
    public static float SKILL_EFFECT_MAX_MULT = 2f;

    @Override
    public String getAffectsString() {
        return "fleet";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        tooltip.addPara("Max ship count per design type is set to %s", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), DESIGN_TYPE_SHIP_LIMIT + "");
        tooltip.addPara("Skill efficiency cap increased to %s", 0f, Misc.getHighlightColor(), Misc.getHighlightColor(), Math.round(SKILL_EFFECT_MAX_MULT * 100f) + "%");
    }
}
