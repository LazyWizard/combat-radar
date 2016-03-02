package org.lazywizard.radar.commands;

import java.awt.Color;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.radar.util.SpriteBatch;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class TestSpriteAngle implements BaseCommand
{
    private static final Logger Log = Logger.getLogger(TestSpriteAngle.class);

    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        if (!context.isInCombat())
        {
            return CommandResult.WRONG_CONTEXT;
        }

        Global.getCombatEngine().addPlugin(new SpriteAngleRenderer());
        return CommandResult.SUCCESS;
    }

    private static class SpriteAngleRenderer extends BaseEveryFrameCombatPlugin
    {
        @Override
        public void renderInUICoords(ViewportAPI viewport)
        {
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);

            final Vector2f center = new Vector2f(Display.getWidth() * 0.5f, Display.getHeight() * 0.5f);
            final ShipAPI player = Global.getCombatEngine().getPlayerShip();
            final SpriteAPI sprite = Global.getSettings().getSprite("graphics/lw_radar/campaign/nebula.png");//player.getSpriteAPI();
            final SpriteBatch batch = new SpriteBatch(sprite);

            batch.add(center.x, center.y, player.getFacing(),
                    sprite.getWidth()*4f, sprite.getHeight(), Color.WHITE, 1f);
            batch.finish();
            batch.draw();

            glDisable(GL_BLEND);
            glDisable(GL_TEXTURE_2D);

            glColor4f(1f, 1f, 1f, 1f);
            glBegin(GL_POINTS);
            glVertex2f(center.x, center.y);
            glEnd();
        }
    }
}
