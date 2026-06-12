package com.xeon.runexplorer.discord;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PaginationManager {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pagination-scheduler");
                t.setDaemon(true);
                return t;
            });

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private PaginationManager() {
    }

    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static List<MessageTopLevelComponent> components(String token, int pageIndex, int pageCount) {
        boolean hasLeft = pageIndex > 0;
        boolean hasRight = pageIndex < (pageCount - 1);

        Button last = Button.primary("pg:last:" + token, "Last").withDisabled(!hasLeft);
        Button next = Button.primary("pg:next:" + token, "Next").withDisabled(!hasRight);

        return List.of(ActionRow.of(last, next));
    }

    public static List<MessageTopLevelComponent> disabledComponents(String token) {
        Button last = Button.primary("pg:last:" + token, "Last").withDisabled(true);
        Button next = Button.primary("pg:next:" + token, "Next").withDisabled(true);
        return List.of(ActionRow.of(last, next));
    }

    public static void register(
            InteractionHook hook,
            String token,
            long ownerUserId,
            List<MessageEmbed> pages,
            int startPage
    ) {
        Session s = new Session(hook, token, ownerUserId, pages, startPage);
        SESSIONS.put(token, s);

        SCHEDULER.schedule(() -> {
            try {
                disableSessionButtons(token);
            } catch (Exception ignored) {
            } finally {
                SESSIONS.remove(token);
            }
        }, 3, TimeUnit.MINUTES);
    }

    public static void onButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id == null || !id.startsWith("pg:")) {
            return;
        }

        String[] parts = id.split(":", 3);
        if (parts.length != 3) {
            event.deferEdit().queue();
            return;
        }

        String action = parts[1];
        String token = parts[2];

        Session s = SESSIONS.get(token);
        if (s == null) {
            event.reply("These buttons have expired. Run the command again.").setEphemeral(true).queue();
            return;
        }

        long clicker = event.getUser().getIdLong();
        if (clicker != s.ownerUserId) {
            event.reply("Only the user who ran this command can use these buttons.").setEphemeral(true).queue();
            return;
        }

        int newPage = s.pageIndex;
        if ("next".equals(action)) {
            newPage = Math.min(s.pages.size() - 1, s.pageIndex + 1);
        } else if ("last".equals(action)) {
            newPage = Math.max(0, s.pageIndex - 1);
        } else {
            event.deferEdit().queue();
            return;
        }

        if (newPage == s.pageIndex) {
            event.deferEdit().queue();
            return;
        }

        s.pageIndex = newPage;

        event.editMessageEmbeds(s.pages.get(s.pageIndex))
                .setComponents(components(token, s.pageIndex, s.pages.size()))
                .queue();
    }

    private static void disableSessionButtons(String token) {
        Session s = SESSIONS.get(token);
        if (s == null) return;

        // Disable fully after 3 minutes
        s.hook.editOriginalComponents(disabledComponents(token)).queue(
                ok -> {},
                fail -> {}
        );
    }

    private static final class Session {
        final InteractionHook hook;
        final String token;
        final long ownerUserId;
        final List<MessageEmbed> pages;
        volatile int pageIndex;

        Session(InteractionHook hook, String token, long ownerUserId, List<MessageEmbed> pages, int pageIndex) {
            this.hook = hook;
            this.token = token;
            this.ownerUserId = ownerUserId;
            this.pages = pages;
            this.pageIndex = pageIndex;
        }
    }
}
