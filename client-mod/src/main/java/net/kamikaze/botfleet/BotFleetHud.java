package net.kamikaze.botfleet;

/**
 * HudRenderCallback (what this was originally written against) was removed
 * as of Fabric API for Minecraft 26.1 — confirmed via the actual 26.1
 * release notes. Replacement is HudElementRegistry, and the element
 * callback receives a GuiGraphicsExtractor and a DeltaTracker, not a raw
 * GuiGraphics/float.
 *
 * Correction from the previous round: `MinecraftClient.getInstance().
 * textRenderer` (what this used last round) was based on a Fabric docs
 * example that turned out wrong on that point. Confirmed this round with
 * harder, independent evidence (official Mojang mapping dumps, matching
 * NeoForge/Forge javadoc): the real official names are
 * `net.minecraft.client.Minecraft` and — per the same long-standing
 * mapping convention, unchanged since 1.14 — the field is `font`, not
 * `textRenderer` (`textRenderer` is Yarn's name for the same field).
 *
 * TODO-VERIFY (real, remaining): GuiGraphicsExtractor's and DeltaTracker's
 * exact package paths, and drawTextWithShadow's precise parameter order,
 * aren't independently confirmed — only the class names themselves, from
 * a docs example whose other details have already proven unreliable once.
 * Your IDE's auto-import will resolve or correct these instantly.
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
            graphics.drawTextWithShadow(
                net.minecraft.client.Minecraft.getInstance().font,
                line,
                x, y,
                0xFFFFFF
            );
            y += lineHeight;
        }
    }
}
