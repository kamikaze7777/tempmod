package net.kamikaze.botfleet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Local-only TCP client to the hub process. Line-delimited text protocol,
 * intentionally dumb — this never leaves the local machine, so no auth/TLS.
 *
 * Wire protocol (both directions, newline-delimited UTF-8):
 *   Client -> hub:  "CMD <botId> <raw command text>"
 *                   "LIST"
 *   Hub -> client:  "LOG <botId> <line>"
 *                   "ROSTER <botId1>,<botId2>,..."
 *                   "ERR <message>"
 */
public class HubConnection {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 47321; // arbitrary, keep in sync with hub's default

    private final Deque<String> recentLog = new ArrayDeque<>();
    private static final int MAX_LOG_LINES = 12;

    private volatile Socket socket;
    private volatile OutputStream out;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Consumer<String> onLogLine = line -> {};

    public void setOnLogLine(Consumer<String> callback) {
        this.onLogLine = callback;
    }

    public synchronized void start() {
        if (running.getAndSet(true)) return;
        Thread t = new Thread(this::connectLoop, "botfleet-hub-connection");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        running.set(false);
        closeQuietly();
    }

    private void connectLoop() {
        while (running.get()) {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(HOST, PORT), 2000);
                out = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    handleIncoming(line);
                }
            } catch (IOException e) {
                // hub not up yet, or dropped — back off and retry, this is a local
                // dev tool, not a service worth being clever about
            } finally {
                closeQuietly();
            }
            if (running.get()) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handleIncoming(String line) {
        if (line.startsWith("LOG ")) {
            String rest = line.substring(4);
            int sp = rest.indexOf(' ');
            String display = sp >= 0 ? rest.substring(0, sp) + ": " + rest.substring(sp + 1) : rest;
            synchronized (recentLog) {
                recentLog.addLast(display);
                while (recentLog.size() > MAX_LOG_LINES) recentLog.removeFirst();
            }
            onLogLine.accept(display);
        }
        // ROSTER / ERR handled by caller inspecting sendAndAwait responses if needed;
        // kept minimal here since /bc list just needs a best-effort snapshot.
    }

    public boolean sendCommand(String botId, String command) {
        return sendRaw("CMD " + botId + " " + command);
    }

    public boolean sendListRequest() {
        return sendRaw("LIST");
    }

    private boolean sendRaw(String line) {
        OutputStream o = out;
        if (o == null) return false;
        try {
            o.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public String[] recentLogSnapshot() {
        synchronized (recentLog) {
            return recentLog.toArray(new String[0]);
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        out = null;
    }
}
