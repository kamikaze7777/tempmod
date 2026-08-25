package net.kamikaze.botfleet;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Corrected against the actual current (post-26.1-mappings-switch) Fabric
 * docs rather than the older API this was first written against:
 *
 * - HudRenderCallback was removed in Fabric API for 26.1 — replaced by
 *   HudElementRegistry. Registration moved into onInitializeClient below
 *   accordingly; see BotFleetHud for the render-side change.
 * - The client command builder class itself changed name too: the current
 *   official docs example (docs.fabricmc.net/develop/commands/basics, dated
 *   after the mappings switch) uses `ClientCommands.literal(...)`, not
 *   `ClientCommandManager.literal(...)` — the latter spelling only turned up
 *   in an older, pre-2026 community wiki page, which is stale. Using the
 *   current official spelling here. Package confirmed via Fabric docs
 *   reference source: net.fabricmc.fabric.api.client.command.v2.ClientCommands.
 *
 * Confirmed against decompiled 26.2 source: Identifier was never renamed to
 * ResourceLocation — the class is still Identifier, just relocated. Package
 * moved from the old net.minecraft.util to net.minecraft.resources. Static
 * factory is Identifier.fromNamespaceAndPath(namespace, path).
 *
 * Official name for the client singleton is net.minecraft.client.Minecraft
 * (`MinecraftClient` is Yarn-only). Field is `font`, not `textRenderer`.
 *
 * Registers a CLIENT-SIDE ONLY command. Client-side commands are
 * intercepted and executed entirely inside your own client BEFORE anything
 * becomes a chat/command packet sent to the server — this is what keeps it
 * invisible to other players and structurally non-colliding with Meteor's
 * own (also client-side, also chat-box-driven) command parsing.
 */
public class BotFleetControlClient implements ClientModInitializer {

    private final HubConnection hub = new HubConnection();
    private final BotFleetHud hud = new BotFleetHud(hub);

    @Override
    public void onInitializeClient() {
        hub.start();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("bc")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("list")
                        .executes(ctx -> {
                            hub.sendListRequest();
                            feedback(ctx, "Requested bot roster from hub.");
                            return 1;
                        }))
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("bot", StringArgumentType.word())
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("command", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String bot = StringArgumentType.getString(ctx, "bot");
                                String command = StringArgumentType.getString(ctx, "command");
                                boolean sent = hub.sendCommand(bot, command);
                                feedback(ctx, sent
                                    ? ("Sent to " + bot + ": " + command)
                                    : ("Hub not connected — command to " + bot + " NOT sent."));
                                return sent ? 1 : 0;
                            })))
            );
        });

        // HudElementRegistry replaces the removed HudRenderCallback. Per the
        // current docs example, elements attach relative to a vanilla layer
        // (here: right before chat) rather than subscribing to a generic
        // "after everything" event the old API used.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("botfleet-control", "bot_log"),
            hud::render
        );
    }

    private void feedback(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx, String message) {
        // Client-side-only feedback, never sent to the server as chat.
        // Component.literal confirmed against the current official docs
        // example (same page as the ClientCommands rename above).
        ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("[BotFleet] " + message));
    }
}
