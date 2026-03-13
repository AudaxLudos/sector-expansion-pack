package sectorexpansionpack.listeners;

import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import sectorexpansionpack.ui.dialogs.MatrixCatalystDialogDelegate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MatrixCatalystOptionProvider extends BaseIndustryOptionProvider {
    public static final Object CUSTOM_PLUGIN = new Object();

    @Override
    public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
        return !allowUnderConstruction && !Objects.equals(ind.getId(), "sep_matrix_catalyst");
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (isUnsuitable(ind, false)) {
            return null;
        }
        List<IndustryOptionData> result = new ArrayList<>();

        IndustryOptionData opt = new IndustryOptionData("Combine or fragment AI cores", CUSTOM_PLUGIN, ind, this);
        result.add(opt);

        return result;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == CUSTOM_PLUGIN) {
            tooltip.addPara("A specialized structure capable of combining or fragmenting AI cores", 0f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == CUSTOM_PLUGIN) {
            if (!opt.ind.isBuilding()) {
                BaseCustomDialogDelegate dialogDelegate = new MatrixCatalystDialogDelegate(opt.ind.getMarket());
                ui.showDialog(MatrixCatalystDialogDelegate.WIDTH, MatrixCatalystDialogDelegate.HEIGHT, dialogDelegate);
            }
        }
    }
}
