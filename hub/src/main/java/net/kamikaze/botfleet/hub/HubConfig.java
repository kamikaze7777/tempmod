package net.kamikaze.botfleet.hub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HubConfig {
    public int port = 47321;
    public List<BotConfig> bots;

    public static class BotConfig {
        public String id;
        public List<String> launchCommand; // e.g. ["java", "-jar", "headlessmc-launcher.jar", "launch", "fabric:26.2", "-lwjgl", "-memory", "1024M", "-commands"]
        public String workingDir;
    }

    public static HubConfig load(Path path) throws IOException {
        String json = Files.readString(path);
        Gson gson = new GsonBuilder().create();
        HubConfig config = gson.fromJson(json, HubConfig.class);
        if (config.bots == null || config.bots.isEmpty()) {
            throw new IllegalArgumentException("config has no bots defined");
        }
        return config;
    }
}
