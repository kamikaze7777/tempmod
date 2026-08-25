package net.kamikaze.botfleet.hub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Wraps one HeadlessMc subprocess. Commands are written to its stdin (the same
 * "msg"/"." /"/" REPL commands you'd type interactively per the HeadlessMc +
 * hmc-specifics docs); stdout is tailed line-by-line and handed to a callback
 * so the hub can broadcast it to connected control clients.
 *
 * This is intentionally a thin wrapper — it doesn't try to parse HeadlessMc's
 * own output into structured state (inventory, position, etc). That's a real
 * gap flagged back in the design discussion: for now this gives you raw log
 * visibility, not a clean event API. Build a real parser on top of onLogLine
 * once you know which log lines you actually care about matching against.
 */
public class Bot {
    public final String id;
    private final List<String> launchCommand;
    private final String workingDir;

    private Process process;
    private OutputStream stdin;
    private Thread stdoutThread;
    private volatile boolean shuttingDown = false;

    public Bot(String id, List<String> launchCommand, String workingDir) {
        this.id = id;
        this.launchCommand = launchCommand;
        this.workingDir = workingDir;
    }

    public synchronized void start(BiConsumer<String, String> onLogLine) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(launchCommand);
        if (workingDir != null) pb.directory(new java.io.File(workingDir));
        pb.redirectErrorStream(true);
        process = pb.start();
        stdin = process.getOutputStream();

        stdoutThread = new Thread(() -> tailStdout(onLogLine), "bot-" + id + "-stdout");
        stdoutThread.setDaemon(true);
        stdoutThread.start();

        Thread watchdog = new Thread(() -> {
            try {
                int code = process.waitFor();
                if (!shuttingDown) {
                    onLogLine.accept(id, "[hub] process exited unexpectedly, code=" + code);
                    // Restart-on-crash is deliberately NOT automatic here — an
                    // unattended mining bot that keeps respawning into an unknown
                    // state (stuck, dead, wrong location) is worse than one that
                    // stays down until you look at it. Wire up your own restart
                    // policy in Hub.java once you've seen what your actual
                    // crash/wedge modes look like.
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "bot-" + id + "-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void tailStdout(BiConsumer<String, String> onLogLine) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onLogLine.accept(id, line);
            }
        } catch (IOException e) {
            if (!shuttingDown) {
                onLogLine.accept(id, "[hub] stdout read error: " + e.getMessage());
            }
        }
    }

    public synchronized boolean sendCommand(String command) {
        if (stdin == null) return false;
        try {
            stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized void stop() {
        shuttingDown = true;
        if (process != null) {
            sendCommand("quit");
            process.destroy();
        }
    }
}
