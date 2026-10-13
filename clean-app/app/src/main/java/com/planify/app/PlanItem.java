package com.planify.app;

import org.json.JSONObject;

public final class PlanItem {
    public long id;
    public String type = "EVENT";
    public String title = "";
    public String description = "";
    public String date = "";
    public String time = "09:00";
    public int color = 0xFF7A6FF0;
    public boolean done = false;
    public boolean notify = false;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("type", type);
            o.put("title", title);
            o.put("description", description);
            o.put("date", date);
            o.put("time", time);
            o.put("color", color);
            o.put("done", done);
            o.put("notify", notify);
        } catch (Exception ignored) {}
        return o;
    }

    public static PlanItem fromJson(JSONObject o) {
        PlanItem item = new PlanItem();
        if (o == null) return item;
        item.id = o.optLong("id", System.currentTimeMillis());
        item.type = safeType(o.optString("type", "EVENT"));
        item.title = o.optString("title", "");
        item.description = o.optString("description", "");
        item.date = o.optString("date", "");
        item.time = o.optString("time", "09:00");
        item.color = o.optInt("color", 0xFF7A6FF0);
        item.done = o.optBoolean("done", false);
        item.notify = o.optBoolean("notify", false);
        return item;
    }

    public static String safeType(String type) {
        if (type == null) return "EVENT";
        switch (type) {
            case "CLASS": case "EXAM": case "DELIVERY": case "TASK":
            case "EVENT": case "REMINDER": case "ALARM": case "OTHER":
                return type;
            default: return "EVENT";
        }
    }
}
