package net.kamikaze.botfleet.hub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local-only control socket. Binds to loopback specifically — this must never
 * accept a connection from anywhere but the same machine, since the protocol
 * has zero authentication by design (it doesn't need any, as long as it's
 * genuinely unreachable off-box). Do not change the bind address to 0.0.0.0
 * without adding real auth first.
 */
public class ControlServer {
    private final int port;
    private final Map<String, Bot> bots;
    private final List<OutputStream> clientWriters = new CopyOnWriteArrayList<>();

    public ControlServer(int port, Map<String, Bot> bots) {
        this.port = port;
        this.bots = bots;
    }

    public void start() throws IOException {
        ServerSocket server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        System.out.println("[hub] control server listening on 127.0.0.1:" + port);
        Thread acceptThread = new Thread(() -> acceptLoop(server), "hub-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop(ServerSocket server) {
        while (true) {
            try {
                Socket client = server.accept();
                Thread t = new Thread(() -> handleClient(client), "hub-client-" + client.getPort());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                System.err.println("[hub] accept failed: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        OutputStream out;
        try {
            out = client.getOutputStream();
        } catch (IOException e) {
            return;
        }
        clientWriters.add(out);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line, out);
            }
        } catch (IOException ignored) {
        } finally {
            clientWriters.remove(out);
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleLine(String line, OutputStream replyTo) {
        if (line.equals("LIST")) {
            String roster = String.join(",", bots.keySet());
            writeLine(replyTo, "ROSTER " + roster);
            return;
        }
        if (line.startsWith("CMD ")) {
            String rest = line.substring(4);
            int sp = rest.indexOf(' ');
            if (sp < 0) {
                writeLine(replyTo, "ERR malformed CMD");
                return;
            }
            String botId = rest.substring(0, sp);
            String command = rest.substring(sp + 1);
            Bot bot = bots.get(botId);
            if (bot == null) {
                writeLine(replyTo, "ERR unknown bot " + botId);
                return;
            }
            boolean ok = bot.sendCommand(command);
            if (!ok) {
                writeLine(replyTo, "ERR failed to send to " + botId);
            }
            return;
        }
        writeLine(replyTo, "ERR unrecognized: " + line);
    }

    /** Called by Hub when a bot produces a log line — fans it out to every connected client-mod. */
    public void broadcastLog(String botId, String line) {
        String payload = "LOG " + botId + " " + line;
        for (OutputStream out : clientWriters) {
            writeLine(out, payload);
        }
    }

    private void writeLine(OutputStream out, String line) {
        try {
            synchronized (out) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            // dead client, will be cleaned up when its read loop notices the close
        }
    }
}
