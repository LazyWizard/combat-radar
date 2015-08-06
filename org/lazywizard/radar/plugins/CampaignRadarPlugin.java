package org.lazywizard.radar.plugins;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lazywizard.radar.CommonRadar;
import org.lazywizard.radar.RadarSettings;
import org.lazywizard.radar.renderers.CampaignRenderer;
import org.lazywizard.radar.util.DrawQueue;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.*;

public class CampaignRadarPlugin implements EveryFrameScript
{
    private final List<CampaignRenderer> renderers = new ArrayList<>();
    private CampaignRadarInfo radarInfo;
    private float timeSinceLastUpdateFrame = 9999f;
    private Vector2f renderCenter;
    private float renderRadius, sightRadius, radarScaling, currentZoom, intendedZoom;
    private int zoomLevel;
    private CampaignFleetAPI player;
    private boolean initialized = false, keyDown = false, enabled = true;

    @Override
    public boolean isDone()
    {
        return false;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

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
            renderRadius = Display.getHeight() / 10f;
            renderCenter = new Vector2f(Display.getWidth() - (renderRadius * 1.2f),
                    renderRadius * 1.2f);
            setZoomLevel(RadarSettings.getNumZoomLevels());
            currentZoom = intendedZoom;

            DrawQueue.releaseDeadQueues();
            renderers.clear(); // Needed due to a .6.2a bug
            radarInfo = new CampaignRadarInfo();
            for (Class<? extends CampaignRenderer> rendererClass
                    : RadarSettings.getCampaignRendererClasses())
            {
                try
                {
                    CampaignRenderer renderer = rendererClass.newInstance();
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
                else
                {
                    if (++newZoom > RadarSettings.getNumZoomLevels())
                    {
                        newZoom = 1;
                    }
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
        sightRadius = RadarSettings.getMaxCampaignSightRange() * currentZoom;
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
            timeSinceLastUpdateFrame = 0;
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
        DrawUtils.drawCircle(renderCenter.x, renderCenter.y, renderRadius, 144, true);
        glColorMask(true, true, true, true);
        radarInfo.disableStencilTest();

        // Draw the radar elements individually
        for (CampaignRenderer renderer : renderers)
        {
            renderer.render(player, amount, isUpdateFrame);
        }

        // Finalize drawing
        //radarInfo.disableStencilTest(); // Minor idiot-proofing
        glDisable(GL_BLEND);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glPopAttrib();
    }

    @Override
    public void advance(float amount)
    {
        SectorAPI sector = Global.getSector();

        // Don't display over menus
        if (sector.getCampaignUI().isShowingDialog())
        {
            return;
        }

        // Don't render if no player is found
        player = sector.getPlayerFleet();
        if (player == null || !player.isAlive())
        {
            return;
        }

        checkInit();
        checkInput();

        // Zoom 0 = radar disabled
        if (enabled && zoomLevel != 0)
        {
            render(amount);
        }
    }

    private class CampaignRadarInfo implements CommonRadar<SectorEntityToken>
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
        public List<SectorEntityToken> filterVisible(List contacts, int maxContacts)
        {
            List<SectorEntityToken> visible = new ArrayList<>();
            for (Object tmp : contacts)
            {
                final SectorEntityToken contact = (SectorEntityToken) tmp;

                // Limit maximum contacts displayed
                if (maxContacts >= 0 && visible.size() >= maxContacts)
                {
                    return visible;
                }

                // Check if any part of the contact is visible
                // Ignore any tokens with the nodraw tag
                if (MathUtils.isWithinRange(contact, player.getLocation(),
                        sightRadius + contact.getRadius()))
                {
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
