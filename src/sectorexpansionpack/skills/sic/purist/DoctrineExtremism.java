package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class DoctrineExtremism extends SCBaseSkillPlugin {
    public static float SKILL_EFFECT_MAX_MULT = 2f;

    @Override
    public String getAffectsString() {
        return "ships with the most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        tooltip.addPara("Purist skill efficiency base value is doubled", Misc.getHighlightColor(), 0f);
        tooltip.addPara("Purist skill efficiency penalties are doubled", Misc.getDarkHighlightColor(), 0f);
    }
}
