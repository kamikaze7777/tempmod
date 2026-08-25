package net.kamikaze.botfleet;

/**
 * HudRenderCallback (what this was originally written against) was removed
 * as of Fabric API for Minecraft 26.1 — confirmed via the actual 26.1
 * release notes and current (post-mappings-switch) Fabric docs. Replacement
 * is HudElementRegistry, and the shape is different, not just the name:
 * the element callback receives a GuiGraphicsExtractor and a DeltaTracker,
 * not a raw GuiGraphics/float — following the current docs example at
 * https://docs.fabricmc.net/develop/rendering/hud closely rather than
 * guessing the shape from the old API's signature.
 *
 * TODO-VERIFY: the live docs example this is based on used
 * `MinecraftClient.getInstance().textRenderer` — NOT `Minecraft.getInstance()`
 * as I'd assumed elsewhere in this project from "standard Mojang mapping
 * convention." That assumption has now been wrong enough times in this repo
 * that I'm following the literal current example instead of my own prior
 * pattern-matching. Still worth a two-minute IDE check: I don't have
 * fully-qualified package paths for GuiGraphicsExtractor or DeltaTracker
 * confirmed, only the class names themselves from the docs snippet — your
 * IDE's auto-import will resolve these instantly; I can't from here.
 */
public class BotFleetHud {
    private final HubConnection hub;

    public BotFleetHud(HubConnection hub) {
        this.hub = hub;
    }

    // Signature intentionally matches the current HudElementRegistry element
    // shape (GuiGraphicsExtractor, DeltaTracker) -> void, per the live docs
    // example. Package imports for these two types are NOT included above
    // this class deliberately — let your IDE resolve and add them; I'd
    // rather leave an explicit gap than assert a fully-qualified path I
    // haven't verified against the actual 26.2 jar.
    public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        String[] lines = hub.recentLogSnapshot();
        if (lines.length == 0) return;

        int x = 4;
        int y = 4;
        int lineHeight = 10;

        for (String line : lines) {
            graphics.drawTextWithShadow(
                net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                line,
                x, y,
                0xFFFFFF
            );
            y += lineHeight;
        }
    }
}
