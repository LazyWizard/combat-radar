package org.lazywizard.radar.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import static org.lwjgl.opengl.GL11.*;

/**
 * Used to efficiently render the same sprite many times. Each instance only
 * handles one unique sprite, and does not modify the underlying
 * {@link SpriteAPI}.
 *
 * @author LazyWizard
 * @since 2.2
 */
// TODO: Rewrite to use buffers (clone of DrawQueue?)
public class SpriteBatch
{
    private static final Logger Log = Logger.getLogger(SpriteBatch.class);
    private final SpriteAPI sprite;
    private final int blendSrc, blendDest;
    private final float textureWidth, textureHeight, offsetScaleX, offsetScaleY, hScale;
    private final List<DrawCall> toDraw = new ArrayList<>();
    private boolean finished = false;

    public SpriteBatch(SpriteAPI sprite)
    {
        this(sprite, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public SpriteBatch(SpriteAPI sprite, int blendSrc, int blendDest)
    {
        this.sprite = sprite;
        this.blendSrc = blendSrc;
        this.blendDest = blendDest;
        textureWidth = sprite.getTextureWidth();
        textureHeight = sprite.getTextureHeight();
        hScale = sprite.getWidth() / sprite.getHeight();
        offsetScaleX = sprite.getCenterX() / (sprite.getWidth() * .5f);
        offsetScaleY = sprite.getCenterY() / (sprite.getHeight() * .5f);
    }

    public void add(float x, float y, float angle, float size, Color color, float alphaMod)
    {
        add(x, y, angle, size * hScale, size, color, alphaMod);
    }

    public void add(float x, float y, float angle, float width, float height, Color color, float alphaMod)
    {
        if (finished)
        {
            clear();
        }

        toDraw.add(new DrawCall(x, y, angle, width, height, new byte[]
        {
            (byte) color.getRed(),
            (byte) color.getGreen(),
            (byte) color.getBlue(),
            (byte) Math.round(color.getAlpha() * alphaMod)
        }));

        finished = false;
    }

    public int size()
    {
        return toDraw.size();
    }

    public void clear()
    {
        toDraw.clear();
        finished = false;
    }

    public boolean isEmpty()
    {
        return toDraw.isEmpty();
    }

    // Does nothing for now, usage is enforced for later move to buffers
    public void finish()
    {
        if (finished)
        {
            throw new RuntimeException("SpriteBatch is already finished!");
        }

        finished = true;
    }

    public void draw()
    {
        if (!finished)
        {
            throw new RuntimeException("Must call finish() before drawing!");
        }

        if (toDraw.isEmpty())
        {
            return;
        }

        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(blendSrc, blendDest);

        sprite.bindTexture();
        for (DrawCall call : toDraw)
        {
            //final float width = call.size, height = call.size;

            glPushMatrix();
            glTranslatef(call.x, call.y, 0f);
            glRotatef(call.angle, 0f, 0f, 1f);
            glTranslatef((-call.width * 0.5f) * offsetScaleX, (-call.height * 0.5f) * offsetScaleY, 0f);

            glColor4ub(call.color[0], call.color[1], call.color[2], call.color[3]);
            glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f);
            glVertex2f(0f, 0f);
            glTexCoord2f(textureWidth, 0f);
            glVertex2f(call.width, 0f);
            glTexCoord2f(textureWidth, textureHeight);
            glVertex2f(call.width, call.height);
            glTexCoord2f(0f, textureHeight);
            glVertex2f(0f, call.height);
            glEnd();
            glPopMatrix();
        }

        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
    }

    private static class DrawCall
    {
        private final float x, y, angle, width, height;
        private final byte[] color;

        private DrawCall(float x, float y, float angle, float width, float height, byte[] color)
        {
            this.x = x;
            this.y = y;
            this.angle = angle - 90f;
            this.width = width;
            this.height = height;
            this.color = color;
        }
    }
}
