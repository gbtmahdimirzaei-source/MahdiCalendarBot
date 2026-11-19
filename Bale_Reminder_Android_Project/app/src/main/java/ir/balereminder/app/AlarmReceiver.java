package ir.balereminder.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("task_id", -1);
        ReminderTask t = TaskStore.get(context, id);
        if (t == null || t.sent || t.missed) return;
        long now = System.currentTimeMillis();
        if (t.remindAt() > now + 60000L) return;
        if (t.remindAt() < now - ReminderScheduler.MAX_LATE_MS) {
            TaskStore.skipOld(context, t.id, t.dueAt);
            ReminderScheduler.rebuild(context);
            return;
        }
        NotificationHelper.show(context, t);
        ReminderScheduler.enqueueSend(context, t.id, t.dueAt);
    }
}
