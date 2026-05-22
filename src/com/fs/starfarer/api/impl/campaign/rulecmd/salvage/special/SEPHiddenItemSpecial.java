package com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.intel.ExpeditionFleetIntel;
import sectorexpansionpack.intel.raid.ExcavationRaidIntel;

public class SEPHiddenItemSpecial extends BaseSalvageSpecial {
    public static final String CONTINUE = "continue";
    HiddenSpecialItemSpecialData data;

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        this.data = (HiddenSpecialItemSpecialData) specialData;

        initSpecialItem();
    }

    public void initSpecialItem() {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(this.data.specialItemId);

        if (spec == null) {
            initNothing();
            return;
        }

        addText("Your salvage crews find a well-hidden safe. After an EMP pulse and some delicate work " +
                "with a plasma cutter, the safe yields its contents.");

        if (this.entity.getMemoryWithoutUpdate().get(ExpeditionFleetIntel.EVENT_KEY) instanceof ExpeditionFleetIntel intel) {
            intel.unsetEventMemoryFlags();
            intel.finish(true);
        } else if (this.entity.getMemoryWithoutUpdate().get(ExcavationRaidIntel.EVENT_KEY) instanceof ExcavationRaidIntel intel) {
            intel.setOutcome(ExcavationRaidIntel.Outcome.FAILED);
            intel.forceFail(true);
            Misc.makeUnimportant(this.entity, ExcavationRaidIntel.HAS_ARTIFACT_REASON);
            this.entity.getMemoryWithoutUpdate().unset(ExcavationRaidIntel.TARGET_KEY);
            this.entity.getMemoryWithoutUpdate().unset(ExcavationRaidIntel.EVENT_KEY);
        }

        SpecialItemData specialItemData = new SpecialItemData(this.data.specialItemId, null);
        this.playerFleet.getCargo().addSpecial(specialItemData, 1);
        AddRemoveCommodity.addItemGainText(specialItemData, 1, this.text);

        setDone(true);
    }

    public static class HiddenSpecialItemSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        final String specialItemId;

        public HiddenSpecialItemSpecialData(String specialItemId) {
            this.specialItemId = specialItemId;
        }

        @Override
        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new SEPHiddenItemSpecial();
        }
    }
}
