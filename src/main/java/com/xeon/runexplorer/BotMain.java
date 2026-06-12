package com.xeon.runexplorer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.xeon.runexplorer.config.BotConfig;
import com.xeon.runexplorer.discord.CommandRegistrar;
import com.xeon.runexplorer.discord.SlashCommandRouter;
import com.xeon.runexplorer.discord.commands.*;
import com.xeon.runexplorer.persistence.TrackerRepository;
import com.xeon.runexplorer.services.*;
import com.xeon.runexplorer.ui.ConsoleWindow;
import com.xeon.runexplorer.ui.Ansi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BotMain {

    static {
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "true");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
    }

    private static final Logger log = LoggerFactory.getLogger(BotMain.class);

    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        boolean ui = true;

        if (args != null) {
            for (String a : args) {
                if (a != null && a.trim().equalsIgnoreCase("nogui")) {
                    ui = false;
                    break;
                }
            }
        }

        if (!ui) {
            try {
                AnsiConsole.systemInstall();
            } catch (Throwable ignored) {}
        }

        if (ui) {
            try {
                FlatDarkLaf.setup();
            } catch (Throwable ignored) {}
        }

        final ConsoleWindow console;
        if (ui) {
            console = new ConsoleWindow(
                    "Runexplorer Console",
                    () -> shutdown(0, "UI closed", null)
            );

            console.showWindow();
            console.startCapturingStdout();
            console.setStatus("Starting...");
        } else {
            console = null;
        }

        final Holder holder = new Holder();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown(0, "Shutdown hook", holder);
        }, "shutdown-hook"));

        try {
            BotConfig config = BotConfig.load();
            System.out.println(Ansi.fgCyan("[BOOT] ") + "Loaded Config");
            log.info("Config loaded. DB path={}", config.databasePath());

            HttpService http = new HttpService(config.httpUserAgent(), config.httpTimeoutMs());
            WikiService wiki = new WikiService(http);
            HiscoresService hiscores = new HiscoresService(http);
            ItemPriceService prices = new ItemPriceService(http, wiki);
            NewsService news = new NewsService(http);

            TrackerRepository trackerRepository = new TrackerRepository(config.databasePath());
            trackerRepository.init();

            SlashCommandRouter router = new SlashCommandRouter();

            router.register(new PlayerCommand(hiscores));
            router.register(new CompareCommand(hiscores));
            router.register(new PriceCommand(prices));
            router.register(new WikiCommand(wiki));
            router.register(new NewsCommand(news));
            router.register(new TrackCommand(hiscores, trackerRepository));
            router.register(new ServersCommand(config));
            router.register(new ReloadCommand(config, router));

            System.out.println(Ansi.fgGreen("[BOOT] ") + "Loaded Commands");
            log.info("Commands loaded.");

            if (console != null) console.setStatus("Connecting to Discord...");

            EnumSet<GatewayIntent> intents = EnumSet.noneOf(GatewayIntent.class);

            JDA jda = JDABuilder.createDefault(config.discordToken(), intents).addEventListeners(router).build();

            holder.jda = jda;

            jda.awaitReady();

            CommandRegistrar.registerGlobalCommands(jda, router);

            System.out.println(Ansi.fgGreen("[BOOT] ") + "Bot ready.");
            log.info("Bot ready. Registered {} slash commands.", router.commandCount());

            if (console != null) console.setStatus("Bot ready.");

        } catch (Exception e) {
            if (console != null) console.setStatus("Error: " + e.getMessage());
            System.err.println(Ansi.fgRed("[BOOT] ") + "Startup error: " + e.getMessage());
            e.printStackTrace(System.err);

            shutdown(1, "Startup error", holder);
            throw e;
        }
    }

    private static void shutdown(int code, String reason, Holder holder) {
        if (!SHUTTING_DOWN.compareAndSet(false, true)) {
            return;
        }

        try {
            System.out.println(Ansi.fgYellow("[SHUTDOWN] ") + "Reason: " + reason);
        } catch (Throwable ignored) {}

        try {
            if (holder != null && holder.jda != null) {
                holder.jda.shutdownNow();
            }
        } catch (Throwable ignored) {}

        try {
            AnsiConsole.systemUninstall();
        } catch (Throwable ignored) {}

        try {
            SwingUtilities.invokeLater(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    try {
                        w.dispose();
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}

        try {
            System.exit(code);
        } catch (Throwable ignored) {}
    }

    private static final class Holder {
        volatile JDA jda;
    }
}
