package org.lazywizard.radar.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatNebulaAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class TestNebula implements BaseCommand
{
    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        if (!context.isInCombat())
        {
            Console.showMessage(CommonStrings.ERROR_COMBAT_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        Global.getCombatEngine().addPlugin(new NebulaDebugRendererPlugin());
        Console.showMessage("Now rendering nebula tiles.");
        return CommandResult.SUCCESS;
    }

    private static class NebulaDebugRendererPlugin extends BaseEveryFrameCombatPlugin
    {
        @Override
        public void renderInWorldCoords(ViewportAPI viewport)
        {
            final CombatEngineAPI engine = Global.getCombatEngine();
            final CombatNebulaAPI nebula = engine.getNebula();
            final float tileSize = nebula.getTileSizeInPixels(),
                    halfTile = tileSize * 0.5f, tileRenderSize = tileSize * 1f;

            glPushAttrib(GL_ALL_ATTRIB_BITS);
            glMatrixMode(GL_PROJECTION);
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(1f, 1f, 0f, .2f);

            final float centerX = engine.getMapWidth() * 0.5f,
                    centerY = engine.getMapHeight() * 0.5f;
            final Vector2f tmpVector = new Vector2f();
            for (int x = 0; x < nebula.getTilesWide(); x++)
            {
                for (int y = 0; y < nebula.getTilesHigh(); y++)
                {
                    if (!nebula.tileHasNebula(x, y))
                    {
                        continue;
                    }

                    final float rawX = (tileSize * x) - centerX - halfTile,
                            rawY = (tileSize * y) - centerY - halfTile;
                    tmpVector.set(rawX,rawY);
                    if (viewport.isNearViewport(tmpVector, tileRenderSize))
                    {
                        DrawUtils.drawCircle(rawX, rawY, tileRenderSize, 144, true);
                    }
                }
            }

            glDisable(GL_BLEND);
            glPopAttrib();
        }
    }
}
