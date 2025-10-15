package sectorexpansionpack.ghosts;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ghosts.BaseGhostBehavior;
import com.fs.starfarer.api.impl.campaign.ghosts.SensorGhost;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class GBCollideRunScript extends BaseGhostBehavior {
    protected SectorEntityToken other;
    protected int maxBurn;
    protected Script script;

    public GBCollideRunScript(SectorEntityToken other, int maxBurn, Script script) {
        super(4f);
        this.other = other;
        this.maxBurn = maxBurn;
        this.script = script;
    }

    @Override
    public void advance(float amount, SensorGhost ghost) {
        if (this.other.getContainingLocation() != ghost.getEntity().getContainingLocation() || !this.other.isAlive()) {
            end();
            return;
        }
        super.advance(amount, ghost);

        float speed = Misc.getSpeedForBurnLevel(this.maxBurn);
        Vector2f loc = Misc.getInterceptPoint(ghost.getEntity(), this.other, speed);
        ghost.moveTo(loc, this.maxBurn);

        float dist = Misc.getDistance(ghost.getEntity(), this.other);
        if (dist < ghost.getEntity().getRadius() + this.other.getRadius() + 0f) {
            if (this.script != null) {
                this.script.run();
                this.script = null; // Ensure it won't run a second time
            }
            end();
        }
    }
}
