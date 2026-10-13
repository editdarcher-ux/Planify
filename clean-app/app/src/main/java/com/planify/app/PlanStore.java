package com.planify.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class PlanStore {
    private final SharedPreferences prefs;
    private final Object lock = new Object();
    private final ArrayList<PlanItem> items = new ArrayList<>();

    public PlanStore(Context context) {
        prefs = context.getSharedPreferences("planify_clean_v2", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        synchronized (lock) {
            items.clear();
            String raw = prefs.getString("items", "[]");
            try {
                JSONArray array = new JSONArray(raw == null ? "[]" : raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.optJSONObject(i);
                    if (o != null) {
                        PlanItem item = PlanItem.fromJson(o);
                        if (item.id <= 0) item.id = System.currentTimeMillis() + i;
                        items.add(item);
                    }
                }
            } catch (Exception ignored) {
                prefs.edit().putString("items", "[]").apply();
            }
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        synchronized (lock) {
            for (PlanItem item : items) array.put(item.toJson());
        }
        prefs.edit().putString("items", array.toString()).apply();
    }

    public List<PlanItem> all() {
        synchronized (lock) {
            ArrayList<PlanItem> copy = new ArrayList<>(items);
            Collections.sort(copy, Comparator.comparing((PlanItem i) -> i.date == null ? "" : i.date)
                    .thenComparing(i -> i.time == null ? "" : i.time));
            return copy;
        }
    }

    public PlanItem get(long id) {
        synchronized (lock) {
            for (PlanItem item : items) if (item.id == id) return item;
        }
        return null;
    }

    public void upsert(PlanItem item) {
        if (item == null) return;
        synchronized (lock) {
            if (item.id <= 0) item.id = System.currentTimeMillis();
            int index = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id == item.id) { index = i; break; }
            }
            if (index >= 0) items.set(index, item); else items.add(item);
        }
        persist();
    }

    public void delete(long id) {
        synchronized (lock) { items.removeIf(i -> i.id == id); }
        persist();
    }

    public int colorFor(String type) {
        String safe = PlanItem.safeType(type);
        return prefs.getInt("color_" + safe, defaultColor(safe));
    }

    public void setColor(String type, int color) {
        String safe = PlanItem.safeType(type);
        prefs.edit().putInt("color_" + safe, color).apply();
        synchronized (lock) {
            for (PlanItem item : items) if (safe.equals(item.type)) item.color = color;
        }
        persist();
    }

    public String getScheduleImage() { return prefs.getString("schedule_image", null); }
    public void setScheduleImage(String uri) {
        SharedPreferences.Editor e = prefs.edit();
        if (uri == null || uri.isEmpty()) e.remove("schedule_image"); else e.putString("schedule_image", uri);
        e.apply();
    }

    public String getSoundUri() { return prefs.getString("sound_uri", null); }
    public void setSoundUri(String uri) {
        SharedPreferences.Editor e = prefs.edit();
        if (uri == null || uri.isEmpty()) e.remove("sound_uri"); else e.putString("sound_uri", uri);
        e.apply();
    }

    public boolean soundEnabled() { return prefs.getBoolean("sound_enabled", true); }
    public void setSoundEnabled(boolean v) { prefs.edit().putBoolean("sound_enabled", v).apply(); }
    public boolean vibrationEnabled() { return prefs.getBoolean("vibration_enabled", true); }
    public void setVibrationEnabled(boolean v) { prefs.edit().putBoolean("vibration_enabled", v).apply(); }

    public static int defaultColor(String type) {
        switch (type) {
            case "CLASS": return 0xFF4E79A7;
            case "EXAM": return 0xFFE15759;
            case "DELIVERY": return 0xFFF28E2B;
            case "TASK": return 0xFF59A14F;
            case "REMINDER": return 0xFF76B7B2;
            case "ALARM": return 0xFFEDC948;
            case "EVENT": return 0xFF7A6FF0;
            default: return 0xFF9C755F;
        }
    }
}
