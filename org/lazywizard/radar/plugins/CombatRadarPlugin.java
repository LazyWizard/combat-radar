package org.lazywizard.radar.plugins;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.RadarSettings;
import org.lazywizard.radar.renderers.CombatRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class CombatRadarPlugin extends BaseEveryFrameCombatPlugin
{
    private final List<CombatRenderer> renderers = new ArrayList<>();
    private CombatRadarInfo radarInfo;
    private float timeSinceLastUpdateFrame = 9999f;
    private Vector2f renderCenter;
    private float renderRadius, sightRadius, radarScaling, currentZoom, intendedZoom;
    private int zoomLevel;
    private ShipAPI player;
    private boolean initialized = false, keyDown = false, enabled = true;

    private void setZoomLevel(int zoom)
    {
        intendedZoom = (zoom / (float) RadarSettings.getNumZoomLevels());

        if (zoomLevel == 0)
        {
            currentZoom = intendedZoom;
        }

        zoomLevel = zoom;
    }

    private void checkInit()
    {
        if (!initialized)
        {
            initialized = true;
            renderRadius = RadarSettings.getRadarRenderRadius();
            renderCenter = new Vector2f(Display.getWidth() - (renderRadius * 1.2f),
                    renderRadius * 1.2f);
            setZoomLevel(RadarSettings.getNumZoomLevels());
            currentZoom = intendedZoom;

            DrawQueue.releaseDeadQueues();
            renderers.clear(); // Needed due to a .6.2a bug
            radarInfo = new CombatRadarInfo();
            for (Class<? extends CombatRenderer> rendererClass
                    : RadarSettings.getCombatRendererClasses())
            {
                try
                {
                    CombatRenderer renderer = rendererClass.newInstance();
                    renderers.add(renderer);
                    renderer.init(radarInfo);
                }
                catch (InstantiationException | IllegalAccessException ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private void checkInput()
    {
        final boolean zoomIn = Keyboard.isKeyDown(RadarSettings.getZoomInKey()),
                zoomOut = Keyboard.isKeyDown(RadarSettings.getZoomOutKey()),
                toggle = Keyboard.isKeyDown(RadarSettings.getRadarToggleKey());
        if (zoomIn || zoomOut || toggle)
        {
            if (keyDown == true)
            {
                return;
            }

            // Radar on/off toggle
            if (toggle)
            {
                enabled = !enabled;
            }
            // Radar zoom levels
            else
            {
                int newZoom = zoomLevel;
                if (zoomIn)
                {
                    if (--newZoom <= 0)
                    {
                        newZoom = RadarSettings.getNumZoomLevels();
                    }
                }
                else if (++newZoom > RadarSettings.getNumZoomLevels())
                {
                    newZoom = 1;
                }

                setZoomLevel(newZoom);
            }

            keyDown = true;
        }
        else
        {
            keyDown = false;
        }
    }

    private void advanceZoom(float amount)
    {
        // Gradually zoom towards actual zoom level
        final float animationSpeed = RadarSettings.getZoomAnimationDuration()
                * RadarSettings.getNumZoomLevels() * amount;
        if (currentZoom < intendedZoom)
        {
            currentZoom = Math.min(intendedZoom, currentZoom + animationSpeed);
        }
        else if (currentZoom > intendedZoom)
        {
            currentZoom = Math.max(intendedZoom, currentZoom - animationSpeed);
        }

        // Calculate zoom effect on radar elements
        sightRadius = RadarSettings.getMaxCombatSightRange() * currentZoom;
        radarScaling = renderRadius / sightRadius;
    }

    private void render(float amount)
    {
        boolean isUpdateFrame = false;
        timeSinceLastUpdateFrame += amount;
        if (timeSinceLastUpdateFrame > RadarSettings.getTimeBetweenUpdateFrames())
        {
            isUpdateFrame = true;
            advanceZoom(timeSinceLastUpdateFrame);
            timeSinceLastUpdateFrame = 0f;
        }

        // Set OpenGL flags
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        radarInfo.resetView();
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glTranslatef(0.01f, 0.01f, 0);

        // Set up the stencil test
        glClear(GL_STENCIL_BUFFER_BIT);
        glEnable(GL_STENCIL_TEST);
        glColorMask(false, false, false, false);
        glStencilFunc(GL_ALWAYS, 1, 1);
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
        DrawUtils.drawCircle(renderCenter.x, renderCenter.y, renderRadius,
                RadarSettings.getVerticesPerCircle(), true);
        glColorMask(true, true, true, true);
        radarInfo.disableStencilTest();

        // Draw the radar elements individually
        for (CombatRenderer renderer : renderers)
        {
            renderer.render(player, amount, isUpdateFrame);
        }

        // Finalize drawing
        glDisable(GL_BLEND);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glPopAttrib();
    }

    @Override
    public void renderInUICoords(ViewportAPI view)
    {
        // Don't display over menus
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || player == null || !engine.isEntityInPlay(player)
                || !engine.isUIShowingHUD())
        {
            return;
        }

        // Zoom 0 = radar disabled
        if (enabled && zoomLevel != 0)
        {
            float amount = engine.getElapsedInLastFrame()
                    / engine.getTimeMult().getModifiedValue();
            render(amount);
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
        final CombatEngineAPI engine = Global.getCombatEngine();

        // This also acts as a main menu check
        player = engine.getPlayerShip();
        if (player == null || !engine.isEntityInPlay(player))
        {
            return;
        }

        checkInit();
        checkInput();
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        initialized = false;
    }

    private class CombatRadarInfo implements CommonRadar<CombatEntityAPI>
    {
        @Override
        public void resetView()
        {
            // Retina display fix
            int width = (int) (Display.getWidth() * Display.getPixelScaleFactor()),
                    height = (int) (Display.getHeight() * Display.getPixelScaleFactor());
            glViewport(0, 0, width, height);
            glOrtho(0, width, 0, height, -1, 1);
        }

        @Override
        public void enableStencilTest()
        {
            glEnable(GL_STENCIL_TEST);
            glStencilFunc(GL_EQUAL, 1, 1);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        }

        @Override
        public void disableStencilTest()
        {
            glDisable(GL_STENCIL_TEST);
        }

        @Override
        public Vector2f getRenderCenter()
        {
            return renderCenter;
        }

        @Override
        public float getRenderRadius()
        {
            return renderRadius;
        }

        @Override
        public float getCurrentPixelsPerSU()
        {
            return radarScaling;
        }

        @Override
        public float getCurrentZoomLevel()
        {
            return RadarSettings.getNumZoomLevels() / (float) zoomLevel;
        }

        @Override
        public float getCurrentSightRadius()
        {
            return sightRadius;
        }

        @Override
        public float getRadarAlpha()
        {
            return RadarSettings.getRadarUIAlpha();
        }

        @Override
        public float getContactAlpha()
        {
            return RadarSettings.getRadarContactAlpha();
        }

        @Override
        public Color getFriendlyContactColor()
        {
            return RadarSettings.getFriendlyContactColor();
        }

        @Override
        public Color getEnemyContactColor()
        {
            return RadarSettings.getEnemyContactColor();
        }

        @Override
        public Color getNeutralContactColor()
        {
            return RadarSettings.getNeutralContactColor();
        }

        @Override
        public Color getAlliedContactColor()
        {
            return RadarSettings.getAlliedContactColor();
        }

        @Override
        public boolean isPointOnRadar(Vector2f worldLoc, float padding)
        {
            return MathUtils.isWithinRange(
                    worldLoc, player.getLocation(), sightRadius + padding);
        }

        @Override
        public boolean isPointOnRadar(float worldLocX, float worldLocY, float padding)
        {
            padding += 250f;
            final float a = worldLocX - player.getLocation().x,
                    b = worldLocY - player.getLocation().y;
            return (a * a) + (b * b) <= (sightRadius * sightRadius + padding * padding);
        }

        @Override
        public Vector2f getPointOnRadar(Vector2f worldLoc)
        {
            float[] loc = getRawPointOnRadar(worldLoc);
            return new Vector2f(loc[0], loc[1]);
        }

        @Override
        public float[] getRawPointOnRadar(Vector2f worldLoc)
        {
            return getRawPointOnRadar(worldLoc.x, worldLoc.y);
        }

        @Override
        public float[] getRawPointOnRadar(float worldX, float worldY)
        {
            float[] loc = new float[2];

            // Get position relative to {0,0}
            // Scale point to fit within the radar properly
            // Translate point to inside the radar box
            loc[0] = ((worldX - player.getLocation().x) * radarScaling) + renderCenter.x;
            loc[1] = ((worldY - player.getLocation().y) * radarScaling) + renderCenter.y;

            return loc;
        }

        @Override
        public float[] getRawPointsOnRadar(float[] worldCoords)
        {
            if ((worldCoords.length & 1) != 0)
            {
                throw new RuntimeException("Coordinates must be in x,y pairs!");
            }

            float[] coords = new float[worldCoords.length];
            float playerX = player.getLocation().x, playerY = player.getLocation().y;
            for (int x = 0; x < worldCoords.length; x += 2)
            {
                // Get position relative to {0,0}
                // Scale point to fit within the radar properly
                // Translate point to inside the radar box
                coords[x] = ((worldCoords[x] - playerX) * radarScaling) + renderCenter.x;
                coords[x + 1] = ((worldCoords[x + 1] - playerY) * radarScaling) + renderCenter.y;
            }

            return coords;
        }

        @Override
        public List<CombatEntityAPI> filterVisible(List contacts, int maxContacts)
        {
            List<CombatEntityAPI> visible = new ArrayList<>();
            for (Object tmp : contacts)
            {
                CombatEntityAPI contact = (CombatEntityAPI) tmp;

                // Limit maximum contacts displayed
                if (maxContacts >= 0 && visible.size() >= maxContacts)
                {
                    return visible;
                }

                // Check if any part of the contact is visible
                if (MathUtils.isWithinRange(contact, player.getLocation(),
                        sightRadius + contact.getCollisionRadius() + 250f))
                {
                    if (RadarSettings.isRespectingFogOfWar() && !CombatUtils.isVisibleToSide(
                            contact, player.getOwner()))
                    {
                        continue;
                    }

                    if (RadarSettings.isFilteredOut(contact))
                    {
                        continue;
                    }

                    visible.add(contact);
                }
            }

            return visible;
        }
    }
}
