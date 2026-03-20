package sectorexpansionpack.intel.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import sectorexpansionpack.intel.ExpeditionFleetIntel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LeakedArtifactLocationIntel extends BaseIntelPlugin {
    protected final String actionId;
    protected final MarketAPI source;
    protected final SectorEntityToken target;
    protected final FactionAPI faction;
    protected final boolean showArtifact = false;
    protected final long queuedTimestamp;
    protected final BaseIntelPlugin intel;

    public LeakedArtifactLocationIntel(String actionId, MarketAPI source, SectorEntityToken target, ExpeditionFleetIntel intel) {
        this.actionId = actionId;
        this.source = source;
        this.target = target;
        this.faction = source.getFaction();
        this.intel = intel;
        this.queuedTimestamp = Global.getSector().getClock().getTimestamp();

        setPostingRangeLY(3f, true);
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
                    new Color[]{tc, fc, fc, fc},
                    this.faction.getPersonNamePrefixAOrAn(), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort());
        } else if (ExpeditionFleetIntel.RETURN_ACTION.equals(this.actionId)) {
            info.addPara("The artifact is rumored to be carried by %s %s expedition fleet who is docking to %s in the %s.", oPad,
                    new Color[]{tc, fc, fc, fc},
                    this.faction.getPersonNamePrefixAOrAn(), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseTypeShort());
        }

        addBulletPoints(info, ListInfoMode.IN_DESC);

        addCreatedTimestamp(info, tc, oPad);
    }

    public void addCreatedTimestamp(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();

        long msPerMin = 60L * 1000L;
        long msPerHour = msPerMin * 60L;
        long msPerDay = msPerHour * 24L;
        long msPerMonth = msPerDay * 30L;
        long msPerCycle = msPerDay * 365L;

        long diff = Global.getSector().getClock().getTimestamp() - this.queuedTimestamp;

        String agoStr;
        List<String> highlights = new ArrayList<>();
        if (diff < msPerHour) {
            long minutes = diff / msPerMin;
            agoStr = minutes + " " + (minutes == 1 ? "minute" : "minutes");
        } else if (diff < msPerDay) {
            long hours = diff / msPerHour;
            agoStr = hours + " " + (hours == 1 ? "hour" : "hours");
            long rem = diff - hours * msPerHour;
            long minutes = rem / msPerMin;
            agoStr += " " + minutes + " " + (minutes == 1 ? "minute" : "minutes");
        } else if (diff < msPerMonth) {
            long days = diff / msPerDay;
            agoStr = days + " " + (days == 1 ? "day" : "days");
            highlights.add("" + days);
        } else if (diff < msPerCycle) {
            long months = diff / msPerMonth;
            agoStr = months + " " + (months == 1 ? "month" : "months");
            long rem = diff - months * msPerMonth;
            long days = rem / msPerDay;
            agoStr += " and " + days + " " + (days == 1 ? "day" : "days");
            highlights.add("" + months);
            highlights.add("" + days);
        } else {
            long cycles = diff / msPerCycle;
            agoStr = cycles + " " + (cycles == 1 ? "cycle" : "cycles");
            long rem = diff - cycles * msPerCycle;
            long months = rem / msPerMonth;
            agoStr += " and " + months + " " + (months == 1 ? "month" : "months");
            highlights.add("" + cycles);
            highlights.add("" + months);
        }

        long days = diff / msPerDay;
        if (days >= 1) {
            info.addPara("Leak is " + agoStr + " old.", pad, tc, h, highlights.toArray(new String[0]));
        } else {
            info.addPara("Leak is less than a day old.", pad);
        }
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float oPad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) {
            initPad = oPad;
        }

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
    public boolean shouldRemoveIntel() {
        if (isImportant()) {
            return false;
        }
        return this.intel.isEnded();
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
}
