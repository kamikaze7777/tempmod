package net.kamikaze.botfleet;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * IMPORTANT — read before assuming this compiles as-is:
 *
 * The three lines below marked TODO-VERIFY reference Mojang-mapping class/method
 * names by long-standing convention (these names have been stable in official
 * mappings since 1.14, well before 26.1's obfuscation removal). I have not checked
 * them against the actual 26.2 Fabric API jar. Open this project with
 * fabric-api 0.158.0+26.2 on the classpath, let your IDE flag any mismatches — this
 * should be a couple of renames at most, not a rewrite, but flagging honestly rather
 * than asserting certainty on API surface this new.
 *
 * Registers a CLIENT-SIDE ONLY command via Fabric API's ClientCommandManager.
 * Client-side commands are intercepted and executed entirely inside your own
 * client BEFORE anything becomes a chat/command packet sent to the server — this
 * is what keeps it invisible to other players and structurally non-colliding with
 * Meteor's own (also client-side, also chat-box-driven) command parsing: two
 * client-side command registries watching the same text box for different
 * prefixes don't conflict, since Fabric's dispatcher and Meteor's each just claim
 * the literal prefixes they register and ignore everything else.
 */
public class BotFleetControlClient implements ClientModInitializer {

    private final HubConnection hub = new HubConnection();
    private final BotFleetHud hud = new BotFleetHud(hub);

    @Override
    public void onInitializeClient() {
        hub.start();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("bc")
                    .then(ClientCommandManager.literal("list")
                        .executes(ctx -> {
                            hub.sendListRequest();
                            feedback(ctx, "Requested bot roster from hub.");
                            return 1;
                        }))
                    .then(ClientCommandManager.argument("bot", StringArgumentType.word())
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
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

        // TODO-VERIFY: HudRenderCallback's exact functional signature (GuiGraphics vs
        // DrawContext parameter type, tick-delta parameter) can shift slightly between
        // Fabric API minor versions — check against 0.158.0+26.2 if this doesn't
        // compile straight away.
        HudRenderCallback.EVENT.register(hud::render);
    }

    private void feedback(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx, String message) {
        // Client-side-only feedback, never sent to the server as chat.
        ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("[BotFleet] " + message));
    }
}
