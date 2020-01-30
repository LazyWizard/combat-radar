package org.lazywizard.radar.renderers;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.util.DrawQueue;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public abstract class BaseMemoryUsageRenderer
{
    private static boolean SHOW_MEMORY;
    private MemoryMXBean memory;
    private DrawQueue drawQueue;
    private float barWidth, barHeight, barX, barY;
    private CommonRadar radar;

    protected void reloadSettings(JSONObject settings) throws JSONException
    {
        SHOW_MEMORY = settings.getBoolean("showMemoryUsage");
    }

    protected void init(CommonRadar radar, float extraPaddingLeft)
    {
        if (!SHOW_MEMORY)
        {
            return;
        }

        this.radar = radar;
        final Vector2f radarCenter = radar.getRenderCenter();
        final float radarRadius = radar.getRenderRadius();

        // Location and size of bar on the screen
        barWidth = radarRadius * 0.07f;
        barHeight = radarRadius * 2f;
        barX = radarCenter.x - (radarRadius * 1.2f) - barWidth - extraPaddingLeft;
        barY = radarCenter.y - radarRadius;

        memory = ManagementFactory.getMemoryMXBean();
        drawQueue = new DrawQueue(24);
    }

    private static float[] createRect(float llx, float lly, float urx, float ury)
    {
        return new float[]
        {
            llx, lly,
            urx, lly,
            urx, ury,
            llx, ury
        };
    }

    private void createBar(MemoryUsage memory)
    {
        final double maxMemory = memory.getMax();
        // If max allocatable memory is undefined, show used vs committed memory instead
        if (maxMemory == -1)
        {
            final float usedHeight = barHeight * (float) (memory.getUsed() / (double) memory.getCommitted());
            drawQueue.setNextColor(Color.WHITE, radar.getRadarAlpha());
            drawQueue.addVertices(createRect(barX, barY, barX + barWidth, barY + usedHeight));
            drawQueue.setNextColor(Color.DARK_GRAY, radar.getRadarAlpha());
            drawQueue.addVertices(createRect(barX, barY + usedHeight, barX + barWidth, barY + barHeight));
            drawQueue.finishShape(GL_QUADS);
        }
        else
        {
            final float usedHeight = barHeight * (float) (memory.getUsed() / maxMemory),
                    commitHeight = barHeight * (float) (memory.getCommitted() / maxMemory);
            drawQueue.setNextColor(Color.WHITE, radar.getRadarAlpha());
            drawQueue.addVertices(createRect(barX, barY, barX + barWidth, barY + usedHeight));
            drawQueue.setNextColor(Color.GRAY, radar.getRadarAlpha());
            drawQueue.addVertices(createRect(barX, barY + usedHeight, barX + barWidth, barY + commitHeight));
            drawQueue.setNextColor(Color.DARK_GRAY, radar.getRadarAlpha());
            drawQueue.addVertices(createRect(barX, barY + commitHeight, barX + barWidth, barY + barHeight));
            drawQueue.finishShape(GL_QUADS);
        }
    }

    protected void render(boolean isUpdateFrame)
    {
        if (!SHOW_MEMORY)
        {
            return;
        }

        if (isUpdateFrame)
        {
            drawQueue.clear();
            createBar(memory.getHeapMemoryUsage());
            drawQueue.finish();
        }

        // Draw memory usage
        glLineWidth(Math.max(1f, barWidth * .25f));
        glEnable(GL_BLEND);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        drawQueue.draw();
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisable(GL_BLEND);
    }
}
