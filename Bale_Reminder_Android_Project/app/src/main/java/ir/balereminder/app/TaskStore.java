package ir.balereminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class TaskStore {
    private static final String PREFS = "bale_reminder_private";
    private TaskStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<ReminderTask> all(Context context) {
        ArrayList<ReminderTask> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(context).getString("tasks", "[]"));
            for (int i=0; i<a.length(); i++) {
                JSONObject j = a.optJSONObject(i);
                if (j != null) result.add(ReminderTask.fromJson(j));
            }
        } catch (Exception ignored) { }
        return result;
    }

    private static synchronized void write(Context c, List<ReminderTask> tasks) {
        JSONArray a = new JSONArray();
        for (ReminderTask t : tasks) {
            try { a.put(t.toJson()); } catch (Exception ignored) { }
        }
        prefs(c).edit().putString("tasks", a.toString()).commit();
    }

    public static synchronized ReminderTask get(Context c, int id) {
        for (ReminderTask t : all(c)) if (t.id == id) return t;
        return null;
    }

    public static synchronized void save(Context c, ReminderTask task) {
        List<ReminderTask> tasks = all(c);
        if (task.id == 0) {
            int next = prefs(c).getInt("next_task_id", 1000) + 1;
            prefs(c).edit().putInt("next_task_id", next).commit();
            task.id = next;
        }
        boolean replaced = false;
        for (int i=0; i<tasks.size(); i++) {
            if (tasks.get(i).id == task.id) { tasks.set(i, task); replaced = true; break; }
        }
        if (!replaced) tasks.add(task);
        write(c, tasks);
    }

    public static synchronized void delete(Context c, int id) {
        List<ReminderTask> tasks = all(c);
        tasks.removeIf(t -> t.id == id);
        write(c, tasks);
    }

    public static synchronized void skipOld(Context c, int id, long dueAt) {
        ReminderTask task = get(c, id);
        if (task == null || task.dueAt != dueAt || task.sent || task.missed) return;
        if (!"none".equals(task.repeat)) {
            task.dueAt = PersianDates.advance(task.dueAt, task.repeat, task.leadMinutes, System.currentTimeMillis());
            task.lastError = "";
        } else {
            task.missed = true;
            task.lastError = "زمان یادآوری گذشته است.";
        }
        save(c, task);
    }

    public static synchronized boolean markSent(Context c, int id, long dueAt) {
        ReminderTask task = get(c, id);
        if (task == null || task.dueAt != dueAt || task.sent || task.missed) return false;
        task.lastSentDueAt = dueAt;
        task.lastError = "";
        if ("none".equals(task.repeat)) task.sent = true;
        else task.dueAt = PersianDates.advance(task.dueAt, task.repeat, task.leadMinutes, System.currentTimeMillis());
        save(c, task);
        return true;
    }

    public static synchronized void markError(Context c, int id, long dueAt, String error) {
        ReminderTask task = get(c, id);
        if (task != null && task.dueAt == dueAt && !task.sent && !task.missed) {
            task.lastError = error.length() > 160 ? error.substring(0, 160) : error;
            save(c, task);
        }
    }

    public static String getToken(Context c) { return prefs(c).getString("token", ""); }
    public static String getChatId(Context c) { return prefs(c).getString("chat_id", ""); }
    public static void saveCredentials(Context c, String token, String chatId) {
        prefs(c).edit().putString("token", token).putString("chat_id", chatId).commit();
    }
}
