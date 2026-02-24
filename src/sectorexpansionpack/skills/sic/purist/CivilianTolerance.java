package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class CivilianTolerance extends SCBaseSkillPlugin {
    @Override
    public String getAffectsString() {
        return "civilian ships";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        tooltip.addPara("Exclude civilian ships from penalty calculations (does not apply bonuses to civilian ships)", Misc.getHighlightColor(), 0f);
    }
}
