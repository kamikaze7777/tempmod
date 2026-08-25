package net.kamikaze.botfleet;

/**
 * HudRenderCallback (what this was originally written against) was removed
 * as of Fabric API for Minecraft 26.1 — confirmed via the actual 26.1
 * release notes. Replacement is HudElementRegistry, and the element
 * callback receives a GuiGraphicsExtractor and a DeltaTracker, not a raw
 * GuiGraphics/float.
 *
 * Confirmed against decompiled 26.2 source:
 * - There is no drawTextWithShadow. The method is text(Font, String, int, int, int)
 *   and defaults dropShadow=true internally — same visual as the old shadow-text
 *   call, just under a different name.
 * - Font lives in the same package as GuiGraphicsExtractor:
 *   net.minecraft.client.gui.Font. Accessed here via Minecraft.getInstance().font.
 * - Text color is ARGB as of 1.21.6+. 0xFFFFFF (RGB, alpha=0) renders invisible;
 *   0xFFFFFFFF is opaque white.
 */
public class BotFleetHud {
    private final HubConnection hub;

    public BotFleetHud(HubConnection hub) {
        this.hub = hub;
    }

    public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        String[] lines = hub.recentLogSnapshot();
        if (lines.length == 0) return;

        int x = 4;
        int y = 4;
        int lineHeight = 10;

        for (String line : lines) {
            graphics.text(
                net.minecraft.client.Minecraft.getInstance().font,
                line,
                x, y,
                0xFFFFFFFF
            );
            y += lineHeight;
        }
    }
}
