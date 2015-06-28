package org.lazywizard.radar.util;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

/**
 * A class to simplify multi-shape rendering by keeping track of draw modes and
 * vertex/color buffers for you, using nothing higher than {@link GL11} methods.
 * <p>
 * Usage instructions:
 * <p>
 * Step 1: Call {@link DrawQueue#setNextColor(java.awt.Color, float)} to set the
 * color of the following vertices. If you don't call this the DrawQueue will
 * use a default color of solid white, or whatever was used previously if you
 * are reusing an existing DrawQueue.
 * <p>
 * Step 2: Add the vertices of your shape with
 * {@link DrawQueue#addVertices(float[])}. You can change the color again
 * between sets of vertices.
 * <p>
 * Step 3: Once you are done setting up a shape call
 * {@link DrawQueue#finishShape(int)}. You <i>must</i> finish a shape after
 * adding all vertices or you will encounter graphical errors!
 * <p>
 * Step 4: Repeat steps 1-3 for each shape you wish to draw. Once all shapes
 * have been added to the DrawQueue, call {@link DrawQueue#finish()} to
 * finalize the contents and ready it for drawing.
 * <p>
 * Step 5: Call {@link DrawQueue#draw()} to draw the DrawQueue's contents. The
 * OpenGL client states {@link GL11#GL_VERTEX_ARRAY} and
 * {@link GL11#GL_COLOR_ARRAY} must be enabled for this method to function
 * correctly. You can call this method as many times as you want.
 * <p>
 * Step 6: When you need to recreate a DrawQueue, just return to Step 1. After
 * finishing a DrawQueue it is ready for writing again.
 *
 * @author LazyWizard
 */
// TODO: Create subimplementation that uses VAO/VBO if machine supports OpenGL version
public class DrawQueue
{
    private static final Logger LOG = Global.getLogger(DrawQueue.class);
    private static final int SIZEOF_VERTEX = 2, SIZEOF_COLOR = 4;
    private final boolean allowResize;
    private final float[] currentColor = new float[]
    {
        1f, 1f, 1f, 1f
    };
    private final List<BatchMarker> batchMarkers = new ArrayList<>();
    private FloatBuffer vertexMap, colorMap;
    private boolean finished = false;

    /**
     * Creates a new fixed-size DrawQueue.
     *
     * @param maxVertices The maximum number of vertices this DrawQueue should
     *                    hold, used for allocating native buffers of the proper
     *                    size.
     */
    public DrawQueue(int maxVertices)
    {
        this(maxVertices, false);
    }

    /**
     * Creates a new DrawQueue, with the option of making it auto-resize when
     * data overflows.
     *
     * @param maxVertices The maximum number of vertices this DrawQueue should
     *                    hold, used for allocating native buffers of the proper
     *                    size.
     * @param allowResize Whether the DrawQueue will allocate larger buffers if
     *                    you exceed {@code maxVertices} vertices. Reallocating
     *                    is relatively expensive, so this flag should only be
     *                    used when you don't know how much data you will be
     *                    passing in.
     */
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

        LOG.log(Level.DEBUG, "Resizing to " + newCapacity);
        vertexMap = BufferUtils.createFloatBuffer(newCapacity * SIZEOF_VERTEX).put(vertexMap);
        colorMap = BufferUtils.createFloatBuffer(newCapacity * SIZEOF_COLOR).put(colorMap);
        finished = false;
    }

    /**
     * Clears all data from the DrawQueue.
     */
    public void clear()
    {
        vertexMap.clear();
        colorMap.clear();
        batchMarkers.clear();
        finished = false;
    }

    /**
     * Sets the color of any vertices added after this method is called.
     * <p>
     * @param color    The color of the next shape. All vertices added until
     *                 this method is called again will use this color.
     * @param alphaMod Multiplies {@code color}'s alpha channel by this number
     *                 (should be between 0 and 1). Useful to avoid creating a
     *                 new Color object every frame just to add fade effects.
     */
    public void setNextColor(Color color, float alphaMod)
    {
        currentColor[0] = color.getRed() / 255f;
        currentColor[1] = color.getGreen() / 255f;
        currentColor[2] = color.getBlue() / 255f;
        currentColor[3] = color.getAlpha() / 255f * alphaMod;
    }

    /**
     * Add vertex data to the current shape. If called on a finished DrawQueue,
     * this will reset it and start a new set of vertex data.
     * <p>
     * @param vertices The vertex x,y pairs to be added.
     */
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

    /**
     * Add vertex data to the current shape. If called on a finished DrawQueue,
     * this will reset it and start a new set of vertex data.
     * <p>
     * @param vertices The vertices to be added.
     */
    public void addVertices(List<Vector2f> vertices)
    {
        if (finished)
        {
            clear();
        }

        final int requiredCapacity = (vertices.size() * 2) + vertexMap.position();
        if (allowResize && requiredCapacity > vertexMap.capacity())
        {
            resize((int) (requiredCapacity * 1.5f / SIZEOF_VERTEX));
        }

        // Individual puts are much faster, but won't check limitations on bounds
        for (Vector2f vertex : vertices)
        {
            vertexMap.put(vertex.x).put(vertex.y);

            // Keep color map updated
            for (int y = 0; y < currentColor.length; y++)
            {
                colorMap.put(currentColor[y]);
            }
        }

        finished = false;
    }

    /**
     * Finalizes the current shape. You <i>must</i> call this after every shape,
     * otherwise rendering errors will occur.
     * <p>
     * @param shapeDrawMode The draw mode for this shape (what you would
     *                      normally call with {@link GL11#glBegin(int)}).
     */
    public void finishShape(int shapeDrawMode)
    {
        batchMarkers.add(new BatchMarker(vertexMap.position() / 2, shapeDrawMode));
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
     * Renders all data in the DrawQueue. {@link DrawQueue#finish()} must be
     * called before using this. This method requires OpenGL client states
     * {@link GL11#GL_VERTEX_ARRAY} and {@link GL11#GL_COLOR_ARRAY} to be
     * enabled.
     */
    public void draw()
    {
        if (!finished)
        {
            throw new RuntimeException("Must call finish() before drawing!");
        }

        // Don't draw if there's nothing to render!
        if (vertexMap.limit() == 0)
        {
            return;
        }

        glVertexPointer(SIZEOF_VERTEX, 0, vertexMap);
        glColorPointer(SIZEOF_COLOR, 0, colorMap);

        int lastIndex = 0;
        for (BatchMarker marker : batchMarkers)
        {
            glDrawArrays(marker.drawMode, lastIndex, marker.resetIndex - lastIndex);
            lastIndex = marker.resetIndex;
        }
    }

    private static class BatchMarker
    {
        private final int resetIndex, drawMode;

        private BatchMarker(int resetIndex, int drawMode)
        {
            this.resetIndex = resetIndex;
            this.drawMode = drawMode;
        }
    }
}
