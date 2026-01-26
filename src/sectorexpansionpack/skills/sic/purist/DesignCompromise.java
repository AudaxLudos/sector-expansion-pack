package sectorexpansionpack.skills.sic.purist;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import second_in_command.SCData;
import second_in_command.specs.SCBaseSkillPlugin;

public class DesignCompromise extends SCBaseSkillPlugin {
    @Override
    public String getAffectsString() {
        return "ships with the second most common design type";
    }

    @Override
    public void addTooltip(SCData data, TooltipMakerAPI tooltip) {
        AptitudePurist.PuristFleetData puristData = AptitudePurist.getPuristFleetData(data);

        tooltip.addPara("Skill effects will also be applied to the second most common design type", Misc.getHighlightColor(), 0f);
        tooltip.setBulletedListMode("   - ");
        if (puristData.secondary != null) {
            tooltip.addPara("The second most common design type is %s", 0f, Misc.getTextColor(), Misc.getDesignTypeColor(puristData.secondary), puristData.secondary);
        } else {
            tooltip.addPara("No second most common design type found", 0f, Misc.getNegativeHighlightColor());
        }
        tooltip.addPara("The second most common design type will be excluded from penalty calculations", 0f);
        tooltip.setBulletedListMode(null);
    }
}
