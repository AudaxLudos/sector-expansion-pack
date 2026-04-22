package sectorexpansionpack.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class TractorBeamEffect implements BeamEffectPlugin {
    public static final float MIN_FORCE = 16000f;
    public static final float MAX_FORCE = 16000f;
    public IntervalUtil hitInterval = new IntervalUtil(0.6f, 0.8f);
    public boolean wasZero = true;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        String key = "sep_tractor_beam_renderer_" + beam.getWeapon().getSlot().getId();
        TractorBeamRenderer data = (TractorBeamRenderer) beam.getSource().getCustomData().get(key);
        if (data == null) {
            data = new TractorBeamRenderer(beam);
            engine.addLayeredRenderingPlugin(data);
            beam.getSource().setCustomData(key, data);
        }
        data.beam = beam; // Manually update the beam as each new firing cycle creates a new beam

        if (beam.getSource().getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
            return;
        }
        CombatEntityAPI target = beam.getDamageTarget();
        if (target == null) {
            return;
        }
        float dur = beam.getDamage().getDpsDuration();
        if (!this.wasZero) {
            dur = 0f;
        }

        this.hitInterval.advance(dur);
        if (this.hitInterval.intervalElapsed()) {
            float distance = Misc.getDistance(target.getLocation(), beam.getFrom());
            float distancePercent = distance / beam.getWeapon().getRange();
            float force = distancePercent * (MAX_FORCE - -MIN_FORCE) + -MIN_FORCE;
            float mass = Math.max(1f, target.getMass());

            Vector2f dir = Misc.getUnitVector(target.getLocation(), beam.getTo());
            Vector2f velChange = new Vector2f();
            dir.normalise(velChange);
            velChange.scale(force / mass);

            Vector2f.add(velChange, target.getVelocity(), target.getVelocity());
        }
    }
}
