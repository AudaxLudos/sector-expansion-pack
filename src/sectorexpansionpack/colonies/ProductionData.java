package sectorexpansionpack.colonies;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

public class ProductionData {
    public static Map<String, IndustryEntry> INDUSTRIES = new HashMap<>();

    static {
        addIndustrySupplies(Industries.FARMING,
                Commodities.FOOD);
        addIndustryConditionDemands(Industries.FARMING,
                Conditions.FARMLAND_POOR, Conditions.FARMLAND_ADEQUATE, Conditions.FARMLAND_RICH, Conditions.FARMLAND_BOUNTIFUL);

        addIndustrySupplies(Industries.MINING,
                Commodities.ORGANICS, Commodities.ORE, Commodities.RARE_ORE, Commodities.VOLATILES);
        addIndustryConditionDemands(Industries.MINING,
                Conditions.ORGANICS_TRACE, Conditions.ORGANICS_COMMON, Conditions.ORGANICS_ABUNDANT, Conditions.ORGANICS_PLENTIFUL,
                Conditions.ORE_SPARSE, Conditions.ORE_MODERATE, Conditions.ORE_ABUNDANT, Conditions.ORE_RICH, Conditions.ORE_ULTRARICH,
                Conditions.RARE_ORE_SPARSE, Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_ABUNDANT, Conditions.RARE_ORE_RICH, Conditions.RARE_ORE_ULTRARICH,
                Conditions.VOLATILES_TRACE, Conditions.VOLATILES_DIFFUSE, Conditions.VOLATILES_ABUNDANT, Conditions.VOLATILES_PLENTIFUL);

        addIndustrySupplies(Industries.REFINING,
                Commodities.METALS, Commodities.RARE_METALS);
        addIndustryCommodityDemands(Industries.REFINING,
                Commodities.ORE, Commodities.RARE_ORE);

        addIndustrySupplies(Industries.LIGHTINDUSTRY,
                Commodities.DOMESTIC_GOODS, Commodities.LUXURY_GOODS);
        addIndustryCommodityDemands(Industries.LIGHTINDUSTRY,
                Commodities.ORGANICS);

        addIndustrySupplies(Industries.HEAVYINDUSTRY,
                Commodities.HEAVY_MACHINERY, Commodities.SUPPLIES, Commodities.HAND_WEAPONS, Commodities.SHIPS);
        addIndustryCommodityDemands(Industries.HEAVYINDUSTRY,
                Commodities.METALS, Commodities.RARE_METALS);

        addIndustrySupplies(Industries.FUELPROD,
                Commodities.FUEL);
        addIndustryCommodityDemands(Industries.FUELPROD,
                Commodities.VOLATILES);
    }

    public static IndustryEntry getIndustryEntry(String industryId) {
        if (!INDUSTRIES.containsKey(industryId)) {
            INDUSTRIES.put(industryId, new IndustryEntry());
        }
        return INDUSTRIES.get(industryId);
    }

    public static void addIndustrySupplies(String industryId, String... supplyIds) {
        IndustryEntry entry = getIndustryEntry(industryId);
        entry.supplies.addAll(List.of(supplyIds));
    }

    public static void addIndustryCommodityDemands(String industryId, String... commodityIds) {
        IndustryEntry entry = getIndustryEntry(industryId);
        entry.commodityDemands.addAll(List.of(commodityIds));
    }

    public static void addIndustryConditionDemands(String industryId, String... conditionIds) {
        IndustryEntry entry = getIndustryEntry(industryId);
        entry.conditionDemands.addAll(List.of(conditionIds));
    }

    public static boolean canSupportIndustry(MarketAPI market, String industryId) {
        IndustryEntry entry = getIndustryEntry(industryId);

        // Check all condition demands first
        boolean conditionsDemandMet = entry.conditionDemands.isEmpty();
        for (String conditionId : entry.conditionDemands) {
            conditionsDemandMet = conditionsDemandMet || market.hasCondition(conditionId);
            if (conditionsDemandMet) {
                break;
            }
        }

        // For each commodity demand, check if it can be supplied
        boolean commodityDemandMet = entry.commodityDemands.isEmpty();
        for (String commodityId : entry.commodityDemands) {
            commodityDemandMet = commodityDemandMet || canSupplyCommodity(market, commodityId);
            if (commodityDemandMet) {
                break;
            }
        }

        return conditionsDemandMet && commodityDemandMet;
    }

    private static boolean canSupplyCommodity(MarketAPI market, String commodityId) {
        // Check if current market can produce commodity
        for (Industry industry : market.getIndustries()) {
            IndustryEntry entry = getIndustryEntry(industry.getId());
            if (entry.supplies.contains(commodityId)) {
                if (industry.getSupply(commodityId).getQuantity().getModifiedInt() > 0) {
                    return true;
                }
            }
        }

        // Check if faction markets can export commodity
        for (MarketAPI otherMarket : Misc.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())) {
            for (Industry industry : otherMarket.getIndustries()) {
                IndustryEntry entry = getIndustryEntry(industry.getId());
                if (entry.supplies.contains(commodityId)) {
                    if (industry.getSupply(commodityId).getQuantity().getModifiedInt() > 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static class IndustryEntry {
        public Set<String> supplies = new HashSet<>();
        public Set<String> commodityDemands = new HashSet<>();
        public Set<String> conditionDemands = new HashSet<>();
    }
}
