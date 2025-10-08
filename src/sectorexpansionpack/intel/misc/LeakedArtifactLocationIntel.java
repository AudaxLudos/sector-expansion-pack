package sectorexpansionpack.intel.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.intel.ExpeditionFleetIntel;

import java.awt.*;
import java.util.Set;

public class LeakedArtifactLocationIntel extends BaseIntelPlugin {
    protected String actionId;
    protected MarketAPI source;
    protected SectorEntityToken target;
    protected FactionAPI faction;
    protected float sinceKnown;
    protected boolean showArtifact = false;

    public LeakedArtifactLocationIntel(String actionId, MarketAPI source, SectorEntityToken target, float postingRangeLY) {
        this.actionId = actionId;
        this.source = source;
        this.target = target;
        this.faction = source.getFaction();

        setPostingRangeLY(postingRangeLY, true);
        setPostingLocation(source.getPrimaryEntity());

        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color fc = this.faction.getBaseUIColor();
        float oPad = 10f;

        info.addImage(this.faction.getLogo(), width, 128, oPad);

        info.addPara("An unknown contact %s %s has leaked a location of an artifact and notes that %s %s expedition fleet was deployed to recover it.", oPad,
                new Color[]{tc, fc, tc, fc},
                this.source.getOnOrAt(), this.source.getName(), this.faction.getPersonNamePrefixAOrAn(), this.faction.getPersonNamePrefix());

        if (ExpeditionFleetIntel.GOTO_ACTION.equals(this.actionId)) {
            String possibleLoc = BreadcrumbSpecial.getLocatedString(this.target, true);
            info.addPara("The artifact is rumored to be %s. A %s expedition fleet will likely be in the area.", oPad,
                    new Color[]{h, fc},
                    possibleLoc, this.faction.getPersonNamePrefix());
        } else if (ExpeditionFleetIntel.LOOT_ACTION.equals(this.actionId)) {
            info.addPara("The artifact is rumored to be carried by %s %s expedition fleet who is scheduled to return to %s in the %s.", oPad,
                    new Color[] {tc, fc, fc, fc},
                    this.faction.getPersonNamePrefixAOrAn(), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (ExpeditionFleetIntel.RETURN_ACTION.equals(this.actionId)) {
            info.addPara("The artifact is rumored to be carried by %s %s expedition fleet who is docking to %s in the %s.", oPad,
                    new Color[] {tc, fc, fc, fc},
                    this.faction.getPersonNamePrefixAOrAn(), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort());
        }

        addBulletPoints(info, ListInfoMode.IN_DESC);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float oPad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = oPad;

        Color tc = getBulletColorForMode(mode);
        Color g = Misc.getGrayColor();

        bullet(info);
        boolean isUpdate = getListInfoParam() != null;

        if (mode != ListInfoMode.IN_DESC) {
            String locText = this.target.getStarSystem().getNameWithLowercaseTypeShort();
            if (ExpeditionFleetIntel.RETURN_ACTION.equals(this.actionId) || ExpeditionFleetIntel.DOCK_ACTION.equals(this.actionId)) {
                locText = "Unknown";
            }
            info.addPara("Location: %s", initPad, tc, g, locText);
            initPad = 0f;
        }

        String nameText = "Unknown";
        if (this.showArtifact) {
            nameText = "ArtifactName";
        }
        info.addPara("Artifact: Unknown", initPad, tc, g, nameText);

        unindent(info);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (this.sinceKnown <= 0 && amount > 0) {
            sendUpdateIfPlayerHasIntel(new Object(), true);
        }

        float days = Misc.getDays(amount);
        this.sinceKnown += days;
    }

    @Override
    public boolean shouldRemoveIntel() {
        if (isImportant()) return false;
        return this.sinceKnown > getBaseDaysAfterEnd();
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "sep_toArtifact");
    }

    @Override
    protected String getName() {
        return "Leaked Artifact Location";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(this.faction.getId());
        tags.add("Leaks");
        return tags;
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        return 15f;
    }
}
