package sectorexpansionpack.ui.dialogs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class MatrixCatalystDialogDelegate extends BaseCustomDialogDelegate implements CustomUIPanelPlugin {
    public static final String SEND_TO_STORAGE_KEY = "$sep_matrix_catalyst_send_to_storage";
    public static final float WIDTH = 400f;
    public static final float HEIGHT = 464f;
    public static List<AICoreRecipe> AI_CORE_RECIPES = new ArrayList<>();

    static {
        AI_CORE_RECIPES.add(new AICoreRecipe(Commodities.GAMMA_CORE, 4, Commodities.BETA_CORE, 1));
        AI_CORE_RECIPES.add(new AICoreRecipe(Commodities.BETA_CORE, 3, Commodities.ALPHA_CORE, 1));
        AI_CORE_RECIPES.add(new AICoreRecipe(Commodities.ALPHA_CORE, 1, Commodities.BETA_CORE, 3));
        AI_CORE_RECIPES.add(new AICoreRecipe(Commodities.BETA_CORE, 1, Commodities.GAMMA_CORE, 4));
    }

    private final MarketAPI market;
    private CustomPanelAPI bgPanel;
    private CustomPanelAPI mPanel;

    public MatrixCatalystDialogDelegate(MarketAPI market) {
        this.market = market;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.bgPanel = panel;
        refreshPanel();
    }

    private void refreshPanel() {
        if (this.bgPanel == null) {
            return;
        }
        if (this.mPanel != null) {
            this.bgPanel.removeComponent(this.mPanel);
        }

        this.mPanel = this.bgPanel.createCustomPanel(WIDTH, HEIGHT, this);
        TooltipMakerAPI mElement = this.mPanel.createUIElement(WIDTH, HEIGHT, false);

        mElement.addSectionHeading("AI Cores Available", Alignment.MID, 0f);
        List<String> aiCoreIds = AI_CORE_RECIPES.stream().map(recipe -> recipe.ingredientItemId).distinct().toList();
        CustomPanelAPI resourcePanel = this.mPanel.createCustomPanel(WIDTH, 30f, null);
        TooltipMakerAPI prevElem = null;
        for (String aiCoreId : aiCoreIds) {
            CommoditySpecAPI commoditySpec = Global.getSettings().getCommoditySpec(aiCoreId);
            float aiCoreInCargoCount = this.market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().getCommodityQuantity(aiCoreId);
            aiCoreInCargoCount += Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(aiCoreId);
            TooltipMakerAPI resourceElement = resourcePanel.createUIElement(100f, 0f, false);
            TooltipMakerAPI resourceImageElement = resourceElement.beginImageWithText(commoditySpec.getIconName(), 20f);
            resourceImageElement.addPara("× %s", 0f, Misc.getTextColor(), Misc.getTextColor(), Math.round(aiCoreInCargoCount) + "").getPosition().setXAlignOffset(-5f);
            resourceElement.addImageWithText(0f);
            resourcePanel.addUIElement(resourceElement);
            if (prevElem != null) {
                resourceElement.getPosition().rightOfMid(prevElem, 0f);
            }
            prevElem = resourceElement;
        }
        mElement.addCustom(resourcePanel, 0f);

        mElement.addSectionHeading("AI Core Recipes", Alignment.MID, 10f);
        for (AICoreRecipe recipe : AI_CORE_RECIPES) {
            float columnItemWidth = WIDTH * 0.3f;
            float columnArrowWidth = WIDTH * 0.1f;
            float columnButtonWidth = WIDTH * 0.3f;

            CustomPanelAPI recipePanel = this.mPanel.createCustomPanel(WIDTH, 70f, this);

            TooltipMakerAPI ingredientElement = recipePanel.createUIElement(columnItemWidth, 0f, false);
            CommoditySpecAPI ingredientItemSpec = Global.getSettings().getCommoditySpec(recipe.ingredientItemId);
            TooltipMakerAPI ingredientImageElement = ingredientElement.beginImageWithText(ingredientItemSpec.getIconName(), 50f);
            ingredientImageElement.addPara("× %s", 0f, Misc.getTextColor(), Misc.getTextColor(), recipe.ingredientItemCount + "").getPosition().setXAlignOffset(-5f);
            ingredientElement.addImageWithText(0f);
            ingredientElement.getPosition().inLMid(-10f);
            recipePanel.addUIElement(ingredientElement);

            TooltipMakerAPI arrowsElement = recipePanel.createUIElement(columnArrowWidth, 0f, false);
            arrowsElement.addImage("graphics/ui/icons/codex_arrow_forward.png", 30f, 0f);
            arrowsElement.getPosition().rightOfMid(ingredientElement, 0f);
            recipePanel.addUIElement(arrowsElement);

            TooltipMakerAPI resultElement = recipePanel.createUIElement(columnItemWidth, 0f, false);
            CommoditySpecAPI resultItemSpec = Global.getSettings().getCommoditySpec(recipe.resultItemId);
            TooltipMakerAPI resultImageElement = resultElement.beginImageWithText(resultItemSpec.getIconName(), 50f);
            resultImageElement.addPara("× %s", 0f, Misc.getTextColor(), Misc.getTextColor(), recipe.resultItemCount + "").getPosition().setXAlignOffset(-5f);
            resultElement.addImageWithText(0f);
            resultElement.getPosition().rightOfMid(arrowsElement, 0f);
            recipePanel.addUIElement(resultElement);

            TooltipMakerAPI convertButtonElement = recipePanel.createUIElement(columnButtonWidth, 0f, false);
            ButtonAPI craftOneButton = convertButtonElement.addButton("Convert × 1", new CraftRecipeData(recipe, 1), columnButtonWidth, 20f, 0f);
            craftOneButton.setEnabled(canCraftRecipe(recipe, 1));
            ButtonAPI craftAllButton = convertButtonElement.addButton("Convert × All", new CraftRecipeData(recipe, -1), columnButtonWidth, 20f, 5f);
            craftAllButton.setEnabled(canCraftRecipe(recipe, -1));
            convertButtonElement.getPosition().rightOfMid(resultElement, 0f);
            recipePanel.addUIElement(convertButtonElement);

            mElement.addCustom(recipePanel, 0f);
        }

        boolean sendToStorage = Global.getSector().getMemory().getBoolean(SEND_TO_STORAGE_KEY);
        ButtonAPI sendToStorageButton = mElement.addCheckbox(WIDTH, 20f, "Send to market storage", SEND_TO_STORAGE_KEY, ButtonAPI.UICheckboxSize.SMALL, 10f);
        sendToStorageButton.setChecked(sendToStorage);

        this.mPanel.addUIElement(mElement);
        this.bgPanel.addComponent(this.mPanel);
    }

    private boolean canCraftRecipe(AICoreRecipe recipe, int doCount) {
        float marketStorageIngredientCount = Misc.getStorageCargo(this.market).getCommodityQuantity(recipe.ingredientItemId);
        float playerStorageIngredientCount = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(recipe.ingredientItemId);
        float totalIngredientCount = marketStorageIngredientCount + playerStorageIngredientCount;

        if (doCount > 0f) {
            return doCount * recipe.ingredientItemCount <= totalIngredientCount;
        } else {
            return totalIngredientCount / recipe.ingredientItemCount >= 1f;
        }
    }

    @Override
    public String getConfirmText() {
        return "Close";
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return super.getCustomPanelPlugin();
    }

    @Override
    public void positionChanged(PositionAPI position) {
    }

    @Override
    public void renderBelow(float alphaMult) {
    }

    @Override
    public void render(float alphaMult) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId instanceof CraftRecipeData data) {
            boolean sendToStorage = Global.getSector().getMemory().getBoolean(SEND_TO_STORAGE_KEY);
            float marketStorageIngredientCount = Misc.getStorageCargo(this.market).getCommodityQuantity(data.recipe.ingredientItemId);
            float playerStorageIngredientCount = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(data.recipe.ingredientItemId);
            float totalIngredientCount = marketStorageIngredientCount + playerStorageIngredientCount;
            if (!(data.doCount >= 1f)) {
                data.doCount = (float) Math.floor(totalIngredientCount / data.recipe.ingredientItemCount);
            }
            float needIngredientCount = data.doCount * data.recipe.ingredientItemCount;

            if (marketStorageIngredientCount > needIngredientCount) {
                Misc.getStorageCargo(this.market).removeCommodity(data.recipe.ingredientItemId, needIngredientCount);
            } else {
                Misc.getStorageCargo(this.market).removeCommodity(data.recipe.ingredientItemId, marketStorageIngredientCount);
                needIngredientCount -= marketStorageIngredientCount;
            }
            if (playerStorageIngredientCount > needIngredientCount) {
                Global.getSector().getPlayerFleet().getCargo().removeCommodity(data.recipe.ingredientItemId, needIngredientCount);
            } else {
                Global.getSector().getPlayerFleet().getCargo().removeCommodity(data.recipe.ingredientItemId, playerStorageIngredientCount);
                needIngredientCount -= marketStorageIngredientCount;
            }

            if (!sendToStorage) {
                Global.getSector().getPlayerFleet().getCargo().addCommodity(data.recipe.resultItemId, data.doCount * data.recipe.resultItemCount);
            } else {
                Misc.getStorageCargo(this.market).addCommodity(data.recipe.resultItemId, data.doCount * data.recipe.resultItemCount);
            }
        } else if (buttonId == SEND_TO_STORAGE_KEY) {
            boolean sendToStorage = Global.getSector().getMemory().getBoolean(SEND_TO_STORAGE_KEY);
            Global.getSector().getMemory().set(SEND_TO_STORAGE_KEY, !sendToStorage);
        }
        refreshPanel();
    }

    public static class AICoreRecipe {
        String ingredientItemId;
        int ingredientItemCount;
        String resultItemId;
        int resultItemCount;

        public AICoreRecipe(String ingredientItemId, int ingredientItemCount, String resultItemId, int resultItemCount) {
            this.ingredientItemId = ingredientItemId;
            this.ingredientItemCount = ingredientItemCount;
            this.resultItemId = resultItemId;
            this.resultItemCount = resultItemCount;
        }
    }

    public static class CraftRecipeData {
        AICoreRecipe recipe;
        float doCount;

        public CraftRecipeData(AICoreRecipe recipe, float doCount) {
            this.recipe = recipe;
            this.doCount = doCount;
        }
    }
}
