package org.lazywizard.radar.renderers;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import org.lazywizard.radar.CommonRadar;
import org.lwjgl.util.vector.Vector2f;
import static org.lazywizard.lazylib.opengl.ColorUtils.glColor;
import static org.lwjgl.opengl.GL11.*;

public abstract class BaseMemoryUsageRenderer
{
    private MemoryMXBean memory;
    private Vector2f barLocation;
    private float barWidth, barHeight, usedFraction = 0f, commitFraction = 0f;

    protected void initiate(CommonRadar radar)
    {
        final Vector2f radarCenter = radar.getRenderCenter();
        final float radarRadius = radar.getRenderRadius();

        // Location and size of bar on the screen
        barWidth = radarRadius * .09f;
        barHeight = radarRadius * 2f;
        barLocation = new Vector2f(radarCenter.x - (radarRadius * 1.15f) - barWidth,
                radarCenter.y - radarRadius);

        memory = ManagementFactory.getMemoryMXBean();
    }

    protected void render(boolean isUpdateFrame)
    {
        if (isUpdateFrame)
        {
            final MemoryUsage heap = memory.getHeapMemoryUsage();
            final double maxMemory = heap.getMax();
            if (maxMemory == -1)
            {
                usedFraction = (float) (heap.getUsed() / (double) heap.getCommitted());
                commitFraction = usedFraction;
            }
            else
            {
                usedFraction = (float) (heap.getUsed() / maxMemory);
                commitFraction = (float) (heap.getCommitted() / maxMemory);
            }
        }

        glLineWidth(barWidth);
        glBegin(GL_LINES);
        glColor(Color.WHITE);
        glVertex2f(barLocation.x, barLocation.y);
        glVertex2f(barLocation.x, barLocation.y + (barHeight * usedFraction));
        glColor(Color.GRAY);
        glVertex2f(barLocation.x, barLocation.y + (barHeight * usedFraction));
        glVertex2f(barLocation.x, barLocation.y + (barHeight * commitFraction));
        glColor(Color.DARK_GRAY);
        glVertex2f(barLocation.x, barLocation.y + (barHeight * commitFraction));
        glVertex2f(barLocation.x, barLocation.y + barHeight);
        glEnd();
    }
}
