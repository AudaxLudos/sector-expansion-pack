package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class DoctrineExtremism extends SCBaseSkillPlugin {
    @Override
    public String getAffectsString() {
        return "ships with the most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        tooltip.addPara("Purist skill effects are doubled", Misc.getHighlightColor(), 0f);
        tooltip.addPara("Purist skill penalties are doubled", Misc.getDarkHighlightColor(), 0f);
    }
}
