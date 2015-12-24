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
    private float barWidth, barHeight, halfWidth,
            heapUsedFraction = 0f, heapCommitFraction = 0f,
            nonHeapUsedFraction = 0f, nonHeapCommitFraction = 0f;

    protected void initiate(CommonRadar radar)
    {
        final Vector2f radarCenter = radar.getRenderCenter();
        final float radarRadius = radar.getRenderRadius();

        // Location and size of bar on the screen
        barWidth = radarRadius * .045f;
        halfWidth = barWidth * 0.5f;
        barHeight = radarRadius * 2f;
        barLocation = new Vector2f(radarCenter.x - (radarRadius * 1.15f) - (barWidth * 2f),
                radarCenter.y - radarRadius);

        memory = ManagementFactory.getMemoryMXBean();
    }

    protected void render(boolean isUpdateFrame)
    {
        if (isUpdateFrame)
        {
            final MemoryUsage heap = memory.getHeapMemoryUsage(),
                    nonHeap = memory.getNonHeapMemoryUsage();
            final double maxHeapMemory = heap.getMax(), maxNonHeapMemory = nonHeap.getMax();
            if (maxHeapMemory == -1)
            {
                heapUsedFraction = (float) (heap.getUsed() / (double) heap.getCommitted());
                heapCommitFraction = 1f;
            }
            else
            {
                heapUsedFraction = (float) (heap.getUsed() / maxHeapMemory);
                heapCommitFraction = (float) (heap.getCommitted() / maxHeapMemory);
            }

            if (maxNonHeapMemory == -1)
            {
                nonHeapUsedFraction = (float) (nonHeap.getUsed() / (double) nonHeap.getCommitted());
                nonHeapCommitFraction = 1f;
            }
            else
            {
                nonHeapUsedFraction = (float) (nonHeap.getUsed() / maxNonHeapMemory);
                nonHeapCommitFraction = (float) (nonHeap.getCommitted() / maxNonHeapMemory);
            }
        }

        glLineWidth(barWidth);
        glBegin(GL_LINES);

        // Heap memory
        // Used
        glColor(Color.WHITE);
        glVertex2f(barLocation.x - halfWidth, barLocation.y);
        glVertex2f(barLocation.x - halfWidth, barLocation.y + (barHeight * heapUsedFraction));
        // Committed
        glColor(Color.GRAY);
        glVertex2f(barLocation.x - halfWidth, barLocation.y + (barHeight * heapUsedFraction));
        glVertex2f(barLocation.x - halfWidth, barLocation.y + (barHeight * heapCommitFraction));
        // Unused, uncommitted
        glColor(Color.DARK_GRAY);
        glVertex2f(barLocation.x - halfWidth, barLocation.y + (barHeight * heapCommitFraction));
        glVertex2f(barLocation.x - halfWidth, barLocation.y + barHeight);

        // Non-heap memory
        // Used
        glColor(Color.WHITE);
        glVertex2f(barLocation.x + halfWidth, barLocation.y);
        glVertex2f(barLocation.x + halfWidth, barLocation.y + (barHeight * nonHeapUsedFraction));
        // Committed
        glColor(Color.GRAY);
        glVertex2f(barLocation.x + halfWidth, barLocation.y + (barHeight * nonHeapUsedFraction));
        glVertex2f(barLocation.x + halfWidth, barLocation.y + (barHeight * nonHeapCommitFraction));
        // Unused, uncommitted
        glColor(Color.DARK_GRAY);
        glVertex2f(barLocation.x + halfWidth, barLocation.y + (barHeight * nonHeapCommitFraction));
        glVertex2f(barLocation.x + halfWidth, barLocation.y + barHeight);

        glEnd();
    }
}
