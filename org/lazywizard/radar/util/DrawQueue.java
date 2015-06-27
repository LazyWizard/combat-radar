package org.lazywizard.radar.util;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import static org.lwjgl.opengl.GL11.*;

/**
 * Step 1: Call {@link DrawQueue#setNextColor(java.awt.Color, float)} to set the color of the following vertices. Not calling this will use a default color of solid white.
 * Step 2: Add the vertices of your shape with {@link DrawQueue#addVertices(float[])}. Once you are done setting up that shape, call {@link DrawQueue#finishShape()}. You <i>must</i> finish a shape after adding all vertices or graphical errors will result!
 * Step 3: Once all shapes have been added to the DrawQueue, call {@link DrawQueue#finish()} to finalize the contents and ready it for drawing.
 * Step 4: Call {@link DrawQueue#draw()} to draw the DrawQueue's contents. The OpenGL client states {@link GL11#GL_VERTEX_ARRAY} and {@link GL11#GL_COLOR_ARRAY} must be enabled for this method to function correctly.
 * Step 5: When you need to recreate a DrawQueue, just return to Step 1. After finishing a DrawQueue it is ready for writing again.
 *
 * @author LazyWizard
 */
public class DrawQueue
{
    private final int drawMode;
    private final FloatBuffer vertexMap, colorMap;
    private final float[] currentColor = new float[]
    {
        1f, 1f, 1f, 1f
    };
    private final List<Integer> resetIndices = new ArrayList<>();
    private boolean finished = false;

    public DrawQueue(int drawMode, int maxVertices)
    {
        this.drawMode = drawMode;
        vertexMap = BufferUtils.createFloatBuffer(maxVertices * 2);
        colorMap = BufferUtils.createFloatBuffer(maxVertices * 4);
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
        if (finished)
        {
            clear();
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

    public void finishShape()
    {
        resetIndices.add(vertexMap.position() / 2);
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

        glVertexPointer(2, 0, vertexMap);
        glColorPointer(4, 0, colorMap);

        int lastIndex = 0;
        for (Integer resetIndex : resetIndices)
        {
            glDrawArrays(drawMode, lastIndex, resetIndex - lastIndex);
            lastIndex = resetIndex;
        }
    }

    private static void resetDrawQueue(DrawQueue testQueue)
    {
        testQueue.setNextColor(new Color((int) (Math.random() * 255),
                (int) (Math.random() * 255), (int) (Math.random() * 255)),
                (float) Math.random());
        testQueue.addVertices(new float[]
        {
            0f, 0f,
            -100f, -100f,
            -100f, 100f,
            100f, 100f,
            100f, -100f,
            -100f, -100f
        });
        testQueue.finishShape();

        testQueue.setNextColor(new Color((int) (Math.random() * 255),
                (int) (Math.random() * 255), (int) (Math.random() * 255)),
                (float) Math.random());
        testQueue.addVertices(new float[]
        {
            150f, 50f,
            50f, -50f,
            50f, 150f,
            250f, 150f,
            250f, -50f,
            50f, -50f
        });
        testQueue.finishShape();
        testQueue.finish();
    }

    public static void main(String[] args)
    {
        final int SCREEN_WIDTH = 800, SCREEN_HEIGHT = 600;
        try
        {
            Display.setDisplayMode(new DisplayMode(SCREEN_WIDTH, SCREEN_HEIGHT));
            Display.create();
        }
        catch (LWJGLException e)
        {
            e.printStackTrace();
            System.exit(1);
        }

        int halfWidth = SCREEN_WIDTH / 2, halfHeight = SCREEN_HEIGHT / 2;

        // init OpenGL
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, 1, -1);
        glMatrixMode(GL_MODELVIEW);

        DrawQueue testQueue = new DrawQueue(GL_TRIANGLE_FAN, 12);
        resetDrawQueue(testQueue);

        while (!Display.isCloseRequested())
        {
            // Clear the screen and depth buffer
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glClearColor(.1f, .1f, .1f, 0f);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            if (Keyboard.isKeyDown(Keyboard.KEY_SPACE))
            {
                resetDrawQueue(testQueue);
            }

            glEnableClientState(GL_VERTEX_ARRAY);
            glEnableClientState(GL_COLOR_ARRAY);
            testQueue.draw();
            glDisableClientState(GL_VERTEX_ARRAY);
            glDisableClientState(GL_COLOR_ARRAY);

            Display.update();
            Display.sync(60);
        }

        Display.destroy();
    }
}
