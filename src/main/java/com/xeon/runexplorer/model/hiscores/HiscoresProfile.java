package com.xeon.runexplorer.model.hiscores;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HiscoresProfile {

    private final String player;
    private final Map<String, HiscoreEntry> skills = new LinkedHashMap<>();
    private final Map<String, HiscoreEntry> activities = new LinkedHashMap<>();

    public HiscoresProfile(String player) {
        this.player = player;
    }

    public String player() {
        return player;
    }

    public Map<String, HiscoreEntry> skills() {
        return skills;
    }

    public Map<String, HiscoreEntry> activities() {
        return activities;
    }

    public int getSkillLevel(String skillName) {
        HiscoreEntry e = skills.get(skillName);
        return e == null ? 1 : e.level();
    }

    public long getSkillXp(String skillName) {
        HiscoreEntry e = skills.get(skillName);
        return e == null ? 0L : e.xpOrScore();
    }
}
