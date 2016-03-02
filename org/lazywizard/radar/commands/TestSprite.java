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
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class TestSprite implements BaseCommand
{
    private static final Logger Log = Logger.getLogger(TestSprite.class);

    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        if (!context.isInCombat())
        {
            return CommandResult.WRONG_CONTEXT;
        }

        Global.getCombatEngine().addPlugin(new ShipSpriteRenderer());
        return CommandResult.SUCCESS;
    }

    private static class ShipSpriteRenderer extends BaseEveryFrameCombatPlugin
    {
        private Color getColor(ShipAPI ship, int playerSide)
        {
            Color baseColor;

            // Hulks
            if (ship.isHulk())
            {
                baseColor = Color.GRAY;
            }
            // Teammates and allies
            else if (ship.getOwner() == playerSide)
            {
                if (ship.isAlly())
                {
                    baseColor = Color.BLUE;
                }
                else
                {
                    baseColor = Color.GREEN;
                }
            }
            // Enemies
            else if (ship.getOwner() + playerSide == 1)
            {
                baseColor = Color.RED;
            }
            // Neutral (doesn't show up in vanilla)
            else
            {
                baseColor = Color.GRAY;
            }

            // Convert color to float array
            float[] color = new float[]
            {
                baseColor.getRed() / 255f,
                baseColor.getGreen() / 255f,
                baseColor.getBlue() / 255f,
                baseColor.getAlpha() / 255f
            };

            // Adjust alpha levels for phasing/fighter takeoff and landing
            if (ship.getCombinedAlphaMult() <= 0f)
            {
                color[3] = 0f;
            }
            else
            {
                color[3] *= .7f * Math.max(.2f, 1f - ((1f - ship.getCombinedAlphaMult()) * 2f));
            }

            //return color;
            return new Color(color[0], color[1], color[2], color[3]);
        }

        @Override
        public void renderInWorldCoords(ViewportAPI viewport)
        {
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            //glTexEnv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_ADD);
            
            for (ShipAPI ship : Global.getCombatEngine().getShips())
            {
                if (viewport.isNearViewport(ship.getLocation(),
                        ship.getCollisionRadius() + 250f))
                {
                    final SpriteAPI sprite = ship.getSpriteAPI();
                    final SpriteBatch batch = new SpriteBatch(sprite);
                    final Vector2f loc = ship.getLocation();
                    batch.add(loc.x, loc.y, ship.getFacing(),
                            sprite.getWidth(), sprite.getHeight(),
                            getColor(ship, 0), 1f);
                    batch.add(loc.x, loc.y, ship.getFacing(),
                            sprite.getWidth() * .95f,
                            sprite.getHeight() * .95f,
                            getColor(ship, 1), 1f);
                    batch.finish();
                    batch.draw();
                    //renderShip(ship);
                }
            }

            glDisable(GL_BLEND);
            glDisable(GL_TEXTURE_2D);
        }
    }
}
