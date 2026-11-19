package ir.balereminder.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;
import java.util.List;

public final class ReminderScheduler {
    private ReminderScheduler() { }
    public static final long MAX_LATE_MS = 24L * 60L * 60L * 1000L;

    private static PendingIntent pending(Context c, int id) {
        Intent intent = new Intent(c, AlarmReceiver.class);
        intent.putExtra("task_id", id);
        return PendingIntent.getBroadcast(c, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c, id));
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(15000 + id);
    }

    public static void rebuild(Context c) {
        List<ReminderTask> list = TaskStore.all(c);
        for (ReminderTask t : list) schedule(c, t);
    }

    public static void schedule(Context c, ReminderTask initial) {
        cancel(c, initial.id);
        ReminderTask t = TaskStore.get(c, initial.id);
        if (t == null || t.sent || t.missed) return;
        long now = System.currentTimeMillis();
        long when = t.remindAt();
        if (when < now - MAX_LATE_MS) {
            TaskStore.skipOld(c, t.id, t.dueAt);
            ReminderTask updated = TaskStore.get(c, t.id);
            if (updated != null && !updated.sent && !updated.missed) schedule(c, updated);
            return;
        }
        if (when <= now) {
            enqueueSend(c, t.id, t.dueAt);
            return;
        }
        armAlarm(c, t, when);
    }

    // Called by the running JobService only after its send succeeds. Do not cancel
    // the currently running job (or other scheduled jobs) while it is finishing.
    public static void armAfterSuccessfulSend(Context c, int id) {
        ReminderTask task = TaskStore.get(c, id);
        if (task == null || task.sent || task.missed) return;
        long when = task.remindAt();
        if (when > System.currentTimeMillis()) armAlarm(c, task, when);
    }

    private static void armAlarm(Context c, ReminderTask t, long when) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, t.id));
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, t.id));
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, t.id));
        }
    }

    public static void enqueueSend(Context c, int taskId, long dueAt) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putInt("task_id", taskId);
        extras.putLong("due_at", dueAt);
        JobInfo info = new JobInfo.Builder(15000 + taskId, new ComponentName(c, SendJobService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(extras)
            .build();
        js.schedule(info);
    }
}
