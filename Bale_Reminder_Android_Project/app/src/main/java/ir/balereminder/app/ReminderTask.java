package ir.balereminder.app;

import org.json.JSONException;
import org.json.JSONObject;

public class ReminderTask {
    public int id;
    public String title = "";
    public String notes = "";
    public long dueAt;
    public int leadMinutes = 15;
    public String repeat = "none";
    public boolean sent = false;
    public boolean missed = false;
    public String lastError = "";
    public long lastSentDueAt = 0;

    public long remindAt() { return dueAt - (long) leadMinutes * 60000L; }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id); j.put("title", title); j.put("notes", notes);
        j.put("dueAt", dueAt); j.put("leadMinutes", leadMinutes);
        j.put("repeat", repeat); j.put("sent", sent); j.put("missed", missed);
        j.put("lastError", lastError); j.put("lastSentDueAt", lastSentDueAt);
        return j;
    }

    public static ReminderTask fromJson(JSONObject j) {
        ReminderTask t = new ReminderTask();
        t.id = j.optInt("id"); t.title = j.optString("title", "");
        t.notes = j.optString("notes", ""); t.dueAt = j.optLong("dueAt");
        t.leadMinutes = j.optInt("leadMinutes", 15);
        t.repeat = j.optString("repeat", "none"); t.sent = j.optBoolean("sent", false);
        t.missed = j.optBoolean("missed", false);
        t.lastError = j.optString("lastError", "");
        t.lastSentDueAt = j.optLong("lastSentDueAt");
        return t;
    }
}
