package net.kamikaze.botfleet;

import net.minecraft.client.gui.GuiGraphics;

/**
 * TODO-VERIFY: GuiGraphics is the modern Mojang-mapping name for the draw-context
 * object passed into HUD render callbacks (this has been the official name since
 * 1.20+; should still hold for 26.2, but confirm against the actual Fabric API
 * source for this version before trusting it blind).
 *
 * Renders the last few log lines received from the hub in the top-left corner.
 * Purely client-side visual — nothing here is sent anywhere.
 */
public class BotFleetHud {
    private final HubConnection hub;

    public BotFleetHud(HubConnection hub) {
        this.hub = hub;
    }

    public void render(GuiGraphics graphics, float tickDelta) {
        String[] lines = hub.recentLogSnapshot();
        if (lines.length == 0) return;

        int x = 4;
        int y = 4;
        int lineHeight = 10;

        for (String line : lines) {
            // TODO-VERIFY: drawString's exact overload/argument order can differ
            // slightly by version — check against 26.2 GuiGraphics if this doesn't
            // compile as written.
            graphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                line,
                x, y,
                0xFFFFFF,
                true
            );
            y += lineHeight;
        }
    }
}
