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
        AptitudePurist.FleetDesignData designData = AptitudePurist.getFleetDesignData(data);

        tooltip.addPara("Skill effects will also be applied to ships with the second most common design type", Misc.getHighlightColor(), 0f);
        tooltip.setBulletedListMode("   - ");
        if (designData.secondary != null) {
            tooltip.addPara("The second common design type is %s", 0f, Misc.getTextColor(), Misc.getHighlightColor(), designData.secondary);
        } else {
            tooltip.addPara("No second common design type found", 0f, Misc.getNegativeHighlightColor());
        }
        tooltip.setBulletedListMode(null);
    }
}
