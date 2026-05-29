package sectorexpansionpack.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BreadcrumbSpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExcavationLeakedIntel extends BaseIntelPlugin {
    protected final MarketAPI source;
    protected final SectorEntityToken target;
    protected final FactionAPI faction;
    protected final SpecialItemSpecAPI artifact;
    protected final RaidIntel.RaidStage stage;
    protected final BaseIntelPlugin intel;
    protected final long queuedTimestamp;

    public ExcavationLeakedIntel(MarketAPI source, SectorEntityToken target, SpecialItemSpecAPI artifact, RaidIntel.RaidStage stage, BaseIntelPlugin intel) {
        this.source = source;
        this.faction = source.getFaction();
        this.target = target;
        this.artifact = artifact;
        this.stage = stage;
        this.intel = intel;
        this.queuedTimestamp = Global.getSector().getClock().getTimestamp();

        Global.getSector().addScript(this);
        Global.getSector().getIntelManager().queueIntel(this);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (this.intel == null || this.intel.isEnding() || this.intel.isEnded()) {
            endAfterDelay();
        }
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    @Override
    protected String getName() {
        return "Leaked " + this.artifact.getName() + " Location";
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor();
        float opad = 10f;

        addBasicDescription(info, width, height);
        addDetailsSection(info, width, height);
        addCreatedTimestamp(info, tc, opad);
        if (this.intel == null || this.intel.isEnding() || this.intel.isEnded()) {
            addDeleteButton(info, width);
        }
    }

    public void addBasicDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        float opad = 10f;

        info.addImage(this.artifact.getIconName(), width, 96f, opad);

        info.addPara("An unknown contact has leaked a location of an artifact called %s.", opad,
                new Color[]{h}, this.artifact.getName());
    }

    public void addDetailsSection(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color fc = this.faction.getBaseUIColor();
        float opad = 10f;
        String possibleLoc = BreadcrumbSpecial.getLocatedString(this.target, true);

        info.addSectionHeading("Details", this.faction.getBaseUIColor(), this.faction.getDarkUIColor(), Alignment.MID, opad);

        if (this.stage instanceof OrganizeStage) {
            info.addPara("The artifact is rumored to be %s. %s %s excavation force has been assembled to recover the artifact " +
                            "and will likely return back to %s in the %s.", opad,
                    new Color[]{h, tc, fc},
                    possibleLoc, Misc.ucFirst(this.faction.getPersonNamePrefixAOrAn()), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseType());
        } else if (this.stage instanceof AssembleStage) {
            info.addPara("The artifact is rumored to be %s. %s %s excavation force has been assembled to recover the artifact " +
                            "and will likely return back to %s in the %s.", opad,
                    new Color[]{h, tc, fc},
                    possibleLoc, Misc.ucFirst(this.faction.getPersonNamePrefixAOrAn()), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseType());
        } else if (this.stage instanceof TravelStage && !(this.stage instanceof GenericReturnStage)) {
            info.addPara("The artifact is rumored to be %s. %s %s excavation force has been assembled to recover the artifact " +
                            "and will likely return back to %s in the %s.", opad,
                    new Color[]{h, tc, fc},
                    possibleLoc, Misc.ucFirst(this.faction.getPersonNamePrefixAOrAn()), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseType());
        } else if (this.stage instanceof ActionStage) {
            info.addPara("The artifact is rumored to be in the possession of %s %s excavation force and is returning to %s in the %s.", opad,
                    new Color[]{tc, fc, fc, tc},
                    this.faction.getPersonNamePrefixAOrAn(), this.faction.getPersonNamePrefix(),
                    this.source.getName(), this.source.getStarSystem().getNameWithLowercaseType());
        }
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

        if (this.intel == null || this.intel.isEnding() || this.intel.isEnded()) {
            info.addPara("Intel has expired and no longer reliable.", pad, tc);
            return;
        }

        long days = diff / msPerDay;
        if (days >= 1) {
            info.addPara("Intel is " + agoStr + " old.", pad, tc, h, highlights.toArray(new String[0]));
        } else {
            info.addPara("Intel is less than a day old.", pad);
        }
    }

    @Override
    public String getIcon() {
        return this.artifact.getIconName();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(this.faction.getId());
        tags.add("Leaks");
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return this.target.getStarSystem().getCenter();
    }
}
