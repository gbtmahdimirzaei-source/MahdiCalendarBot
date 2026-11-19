package ir.balereminder.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SendJobService extends JobService {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            boolean retry = false;
            try {
                int id = params.getExtras().getInt("task_id", -1);
                long dueAt = params.getExtras().getLong("due_at", 0);
                ReminderTask task = TaskStore.get(getApplicationContext(), id);
                if (task != null && task.dueAt == dueAt && !task.sent && !task.missed) {
                    long now = System.currentTimeMillis();
                    if (task.remindAt() < now - ReminderScheduler.MAX_LATE_MS) {
                        TaskStore.skipOld(this, task.id, dueAt);
                    } else {
                        String token = TaskStore.getToken(this);
                        String chat = TaskStore.getChatId(this);
                        if (token.trim().isEmpty() || !chat.matches("-?[0-9]+")) {
                            TaskStore.markError(this, id, dueAt, "تنظیمات بازو یا شناسه گفتگو کامل نیست.");
                            retry = true;
                        } else {
                            try {
                                BaleApi.sendMessage(token, chat, BaleApi.reminderText(task, now - task.remindAt() > 600000L));
                                TaskStore.markSent(this, id, dueAt);
                            } catch (Exception e) {
                                TaskStore.markError(this, id, dueAt, e.getMessage() == null ? "خطا در ارسال" : e.getMessage());
                                retry = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                retry = true;
            }
            if (!retry) {
                int id = params.getExtras().getInt("task_id", -1);
                ReminderScheduler.armAfterSuccessfulSend(getApplicationContext(), id);
            }
            jobFinished(params, retry);
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return true;
    }
}
