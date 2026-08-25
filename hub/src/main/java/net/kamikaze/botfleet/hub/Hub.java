package net.kamikaze.botfleet.hub;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Hub {
    public static void main(String[] args) throws IOException {
        Path configPath = Path.of("hub-config.json");
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--config")) {
                configPath = Path.of(args[i + 1]);
            }
        }

        System.out.println("[hub] loading config from " + configPath.toAbsolutePath());
        HubConfig config = HubConfig.load(configPath);

        Map<String, Bot> bots = new LinkedHashMap<>();
        ControlServer[] serverHolder = new ControlServer[1];

        for (HubConfig.BotConfig bc : config.bots) {
            Bot bot = new Bot(bc.id, bc.launchCommand, bc.workingDir);
            bots.put(bc.id, bot);
        }

        ControlServer server = new ControlServer(config.port, bots);
        serverHolder[0] = server;
        server.start();

        for (Bot bot : bots.values()) {
            System.out.println("[hub] starting bot " + bot.id);
            bot.start((id, line) -> {
                System.out.println("[" + id + "] " + line);
                serverHolder[0].broadcastLog(id, line);
            });
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[hub] shutting down, stopping all bots...");
            for (Bot bot : bots.values()) {
                bot.stop();
            }
        }));

        System.out.println("[hub] " + bots.size() + " bot(s) started. Control server on port " + config.port + ".");
        // Keep the main thread alive; everything else runs on daemon threads.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[hub] interrupted, shutting down.");
        }
    }
}
