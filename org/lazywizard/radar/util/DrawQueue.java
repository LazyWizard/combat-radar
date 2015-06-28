package org.lazywizard.radar.util;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import static org.lwjgl.opengl.GL11.*;

/**
 * A class to simplify multi-shape rendering by keeping track of vertex and
 * color buffers for you.
 * <p>
 * Usage instructions:
 * <p>
 * Step 1: Call {@link DrawQueue#setNextColor(java.awt.Color, float)} to set the
 * color of the following vertices. If you don't call this the DrawQueue will
 * use a default color of solid white, or whatever was used previously if
 * reusing an existing DrawQueue.
 * <p>
 * Step 2: Add the vertices of your shape with
 * {@link DrawQueue#addVertices(float[])}. You can change the color again
 * between sets of vertices. Once you are done setting up that shape call
 * {@link DrawQueue#finishShape()}. You <i>must</i> finish a shape after adding
 * all vertices or you will encounter graphical errors! Repeat step 1 and 2 for
 * all shapes you wish to draw.
 * <p>
 * Step 3: Once all shapes have been added to the DrawQueue, call
 * {@link DrawQueue#finish()} to finalize the contents and ready it for drawing.
 * <p>
 * Step 4: Call {@link DrawQueue#draw()} to draw the DrawQueue's contents. The
 * OpenGL client states {@link GL11#GL_VERTEX_ARRAY} and
 * {@link GL11#GL_COLOR_ARRAY} must be enabled for this method to function
 * correctly. You can call this method as many times as you want.
 * <p>
 * Step 5: When you need to recreate a DrawQueue, just return to Step 1. After
 * finishing a DrawQueue it is ready for writing again.
 *
 * @author LazyWizard
 */
public class DrawQueue
{
    private static final int SIZEOF_VERTEX = 2, SIZEOF_COLOR = 4;
    private final boolean allowResize;
    private final float[] currentColor = new float[]
    {
        1f, 1f, 1f, 1f
    };
    private final List<BatchMarker> resetIndices = new ArrayList<>();
    private FloatBuffer vertexMap, colorMap;
    private boolean finished = false;

    public DrawQueue(int maxVertices)
    {
        this(maxVertices, false);
    }

    public DrawQueue(int maxVertices, boolean allowResize)
    {
        vertexMap = BufferUtils.createFloatBuffer(maxVertices * SIZEOF_VERTEX);
        colorMap = BufferUtils.createFloatBuffer(maxVertices * SIZEOF_COLOR);
        this.allowResize = allowResize;
    }

    private void resize(int newCapacity)
    {
        if (!finished)
        {
            finish();
        }

        Global.getLogger(DrawQueue.class).log(Level.DEBUG,
                "Resizing to " + newCapacity);
        FloatBuffer newVertexMap = BufferUtils.createFloatBuffer(newCapacity * SIZEOF_VERTEX),
                newColorMap = BufferUtils.createFloatBuffer(newCapacity * SIZEOF_COLOR);

        newVertexMap.put(vertexMap);
        newColorMap.put(colorMap);
        vertexMap = newVertexMap;
        colorMap = newColorMap;

        finished = false;
    }

    private void clear()
    {
        vertexMap.clear();
        colorMap.clear();
        resetIndices.clear();
        finished = false;
    }

    public void setNextColor(Color color, float alphaMod)
    {
        currentColor[0] = color.getRed() / 255f;
        currentColor[1] = color.getGreen() / 255f;
        currentColor[2] = color.getBlue() / 255f;
        currentColor[3] = color.getAlpha() / 255f * alphaMod;
    }

    public void addVertices(float[] vertices)
    {
        if ((vertices.length & 1) != 0)
        {
            throw new RuntimeException("Vertices must be added in pairs!");
        }

        if (finished)
        {
            clear();
        }

        final int requiredCapacity = vertices.length + vertexMap.position();
        if (allowResize && requiredCapacity > vertexMap.capacity())
        {
            resize((int) (requiredCapacity * 1.5f / SIZEOF_VERTEX));
        }

        // Individual puts are much faster, but won't check limitations on bounds
        for (int x = 0; x < vertices.length; x++)
        {
            vertexMap.put(vertices[x]);

            // Keep color map updated
            if ((x & 1) == 0)
            {
                for (int y = 0; y < currentColor.length; y++)
                {
                    colorMap.put(currentColor[y]);
                }
            }
        }

        finished = false;
    }

    public void finishShape(int shapeDrawMode)
    {
        resetIndices.add(new BatchMarker(vertexMap.position() / 2, shapeDrawMode));
    }

    /**
     * Readies the list of shapes for drawing. <i>Must</i> be called before
     * calling {@link DrawQueue#draw()}, otherwise an exception will be thrown!
     *
     * Once this method has been called, any further calls to
     * {@link DrawQueue#addVertices(float[])} will <i>replace</i> the
     * DrawQueue's existing contents, not add to them.
     */
    public void finish()
    {
        if (finished)
        {
            throw new RuntimeException("DrawQueue is already finished!");
        }

        finished = true;
        vertexMap.flip();
        colorMap.flip();
    }

    /**
     * Requires OpenGL client states GL_VERTEX_ARRAY and GL_COLOR_ARRAY to be
     * enabled.
     */
    public void draw()
    {
        if (!finished)
        {
            throw new RuntimeException("DrawQueue must call finish() before drawing!");
        }

        // Don't draw if there's nothing to render!
        if (vertexMap.limit() == 0)
        {
            return;
        }

        glVertexPointer(SIZEOF_VERTEX, 0, vertexMap);
        glColorPointer(SIZEOF_COLOR, 0, colorMap);

        int lastIndex = 0;
        for (BatchMarker marker : resetIndices)
        {
            glDrawArrays(marker.drawMode, lastIndex, marker.resetIndex - lastIndex);
            lastIndex = marker.resetIndex;
        }
    }

    private class BatchMarker
    {
        private final int resetIndex, drawMode;

        private BatchMarker(int resetIndex, int drawMode)
        {
            this.resetIndex = resetIndex;
            this.drawMode = drawMode;
        }
    }
}
