package sectorexpansionpack.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.EnumSet;

public class TractorBeamRenderer extends BaseCombatLayeredRenderingPlugin {
    public SpriteAPI texture;
    public BeamAPI beam;
    public WeaponAPI weapon;
    public ShipAPI source;
    public float texOffset = 0f;
    public float texScrollSpeed = 300f;
    public Color texColor = new Color(140, 140, 255, 255);

    public TractorBeamRenderer(BeamAPI beam) {
        this.texture = Global.getSettings().getSprite("fx", "sep_tractor_beam_stream");
        this.beam = beam;
        this.weapon = beam.getWeapon();
        this.source = beam.getSource();
    }

    @Override
    public void advance(float amount) {
        this.texOffset += (amount * this.texScrollSpeed) / this.texture.getWidth();
        if (this.texOffset > 1f) {
            this.texOffset--;
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        GL11.glPushMatrix();
        GL11.glTranslatef(this.weapon.getLocation().x, this.weapon.getLocation().y, 0f);
        GL11.glRotatef(this.weapon.getCurrAngle() - 90f, 0f, 0f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        this.texture.bindTexture();

        boolean wireframe = false;
        if (wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        float height = this.weapon.getRange() + 50f;
        int columns = 6;
        int rows = (int) (height / 50f);
        float bottomWidth = 10f;
        float topWidth = 300f;

        float revealProgress = (this.beam.getLengthPrevFrame() + 50f) / height;
        int revealedRows = (int) Math.min(rows - 1, rows * revealProgress);

        // GL11.glEnable(GL11.GL_STENCIL_TEST);
        // GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        // GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
        // GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        //
        // GL11.glStencilMask(255);
        // GL11.glColorMask(false, false, false, false);
        // GL11.glDisable(GL11.GL_TEXTURE_2D);
        //
        // GL11.glBegin(GL11.GL_QUADS);
        // GL11.glVertex2f(-topWidth, 0f);
        // GL11.glVertex2f(topWidth, 0f);
        // GL11.glVertex2f(topWidth, revealHeight);
        // GL11.glVertex2f(-topWidth, revealHeight);
        // GL11.glEnd();
        //
        // GL11.glColorMask(true, true, true, true);
        // GL11.glEnable(GL11.GL_TEXTURE_2D);
        // GL11.glStencilFunc(GL11.GL_EQUAL, 1, 1);
        // GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        for (int row = 0; row < rows; row++) {
            float yBottom = (float) row / rows * height;
            float yTop = (float) (row + 1) / rows * height;

            float tCurr = (float) row / rows;
            float tNext = (float) (row + 1) / rows;

            float widthBottom = bottomWidth * (1f - tCurr) + topWidth * tCurr;
            float widthTop = bottomWidth * (1f - tNext) + topWidth * tNext;

            float bottomAlpha = 1f;
            float topAlpha = 1f;
            if (row == revealedRows) {
                bottomAlpha = 1f;
                topAlpha = 0f;
            } else if (row >= revealedRows) {
                bottomAlpha = 0f;
                topAlpha = 0f;
            }

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int col = 0; col <= columns; col++) {
                float xBottom = -widthBottom / 2f + ((float) col / columns * widthBottom);
                float xTop = -widthTop / 2f + ((float) col / columns * widthTop);

                float uCurr = (float) row / rows;
                float uNext = (float) (row + 1) / rows;
                float v = (float) col / columns;

                GL11.glTexCoord2f(-uCurr - this.texOffset, v);
                GL11.glColor4ub(
                        (byte) this.texColor.getRed(),
                        (byte) this.texColor.getGreen(),
                        (byte) this.texColor.getBlue(),
                        (byte) (this.texColor.getAlpha() * bottomAlpha * this.beam.getBrightness()));
                GL11.glVertex2f(xBottom, yBottom);

                GL11.glTexCoord2f(-uNext - this.texOffset, v);
                GL11.glColor4ub(
                        (byte) this.texColor.getRed(),
                        (byte) this.texColor.getGreen(),
                        (byte) this.texColor.getBlue(),
                        (byte) (this.texColor.getAlpha() * topAlpha * this.beam.getBrightness()));
                GL11.glVertex2f(xTop, yTop);
            }
            GL11.glEnd();
        }

        // GL11.glDisable(GL11.GL_STENCIL_TEST);

        if (wireframe) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }

        GL11.glPopMatrix();
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
    }

    @Override
    public boolean isExpired() {
        return !this.source.isAlive();
    }
}
