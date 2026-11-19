package ir.balereminder.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int DARK = Color.rgb(22, 51, 58);
    private static final int GRAY = Color.rgb(92, 115, 120);
    private static final int TEAL = Color.rgb(8, 124, 128);
    private LinearLayout content;
    private Button tabTasks, tabSettings;
    private boolean settingsTab = false;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.content);
        tabTasks = findViewById(R.id.tabTasks);
        tabSettings = findViewById(R.id.tabSettings);
        tabTasks.setOnClickListener(v -> showPage(false));
        tabSettings.setOnClickListener(v -> showPage(true));
        NotificationHelper.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        ReminderScheduler.rebuild(this);
        showPage(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) {
            ReminderScheduler.rebuild(this);
            if (settingsTab) showPage(true);
        }
    }

    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }
    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        t.setGravity(Gravity.RIGHT); t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private Button button(String title, boolean primary) {
        Button b = new Button(this);
        b.setText(title); b.setAllCaps(false); b.setTextSize(14);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(2), dp(12), dp(2));
        b.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_secondary);
        b.setTextColor(primary ? Color.WHITE : TEAL);
        return b;
    }
    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        parent.addView(view, lp);
    }
    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setBackgroundResource(R.drawable.bg_card);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setElevation(dp(1));
        return l;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void showPage(boolean settings) {
        settingsTab = settings;
        tabTasks.setBackgroundResource(settings ? R.drawable.bg_secondary : R.drawable.bg_primary);
        tabSettings.setBackgroundResource(settings ? R.drawable.bg_primary : R.drawable.bg_secondary);
        tabTasks.setTextColor(settings ? TEAL : Color.WHITE);
        tabSettings.setTextColor(settings ? Color.WHITE : TEAL);
        content.removeAllViews();
        if (settings) renderSettings(); else renderTasks();
    }

    private void renderTasks() {
        List<ReminderTask> tasks = TaskStore.all(this);
        tasks.sort(Comparator.comparingInt((ReminderTask t) -> t.sent || t.missed ? 1 : 0)
            .thenComparingLong(t -> t.dueAt));
        int active = 0;
        long next = Long.MAX_VALUE;
        for (ReminderTask t : tasks) {
            if (!t.sent && !t.missed) { active++; next = Math.min(next, t.remindAt()); }
        }
        LinearLayout summary = card();
        add(summary, text("برنامه‌های فعال", 14, GRAY, false), 0);
        add(summary, text(PersianDates.fa(String.valueOf(active)) + " برنامه", 27, TEAL, true), 6);
        add(summary, text(next == Long.MAX_VALUE ? "هنوز یادآوری فعالی ندارید." : "نزدیک‌ترین یادآوری: " + PersianDates.full(next), 12, GRAY, false), 8);
        add(content, summary, 4);

        Button addButton = button("+  ثبت برنامه جدید", true);
        addButton.setMinHeight(dp(52));
        addButton.setOnClickListener(v -> editTask(null));
        add(content, addButton, 14);

        add(content, text("فهرست برنامه‌ها", 18, DARK, true), 22);
        if (tasks.isEmpty()) {
            LinearLayout empty = card();
            add(empty, text("هنوز برنامه‌ای ثبت نشده است.", 16, DARK, true), 0);
            add(empty, text("برای ایجاد نخستین یادآوری، دکمه ثبت برنامه جدید را انتخاب کنید.", 13, GRAY, false), 9);
            add(content, empty, 12);
            return;
        }
        for (ReminderTask t : tasks) add(content, taskCard(t), 10);
        add(content, text("زمان‌بندی با ساعت گوشی انجام می‌شود. برای ارسال در بله، اینترنت و اتصال معتبر بازو لازم است.", 12, GRAY, false), 16);
    }

    private View taskCard(ReminderTask t) {
        LinearLayout c = card();
        add(c, text(t.title, 17, DARK, true), 0);
        String status = t.sent ? "یادآوری ارسال شد" : t.missed ? "موعد از دست رفت" :
            !t.lastError.isEmpty() ? "ارسال ناموفق؛ تلاش مجدد" : "در انتظار یادآوری";
        add(c, text(status, 12, t.missed || !t.lastError.isEmpty() ? 0xFFB05C32 : TEAL, true), 6);
        add(c, text("📅  " + PersianDates.full(t.dueAt), 14, DARK, false), 12);
        String repeat = "daily".equals(t.repeat) ? "روزانه" : "weekly".equals(t.repeat) ? "هفتگی" : "یک‌بار";
        String lead = t.leadMinutes == 0 ? "سر موعد" : PersianDates.fa(String.valueOf(t.leadMinutes)) + " دقیقه قبل";
        add(c, text("⏰  " + lead + "   |   " + repeat, 13, GRAY, false), 6);
        if (!t.notes.isEmpty()) add(c, text("توضیحات: " + t.notes, 13, GRAY, false), 10);
        if (!t.lastError.isEmpty()) add(c, text("وضعیت: " + t.lastError, 12, 0xFFAF502F, false), 8);
        LinearLayout actions = horizontal();
        Button edit = button("ویرایش", false);
        Button delete = button("حذف", false);
        edit.setOnClickListener(v -> editTask(t));
        delete.setOnClickListener(v -> confirmDelete(t));
        LinearLayout.LayoutParams ea = new LinearLayout.LayoutParams(0, dp(42), 1f);
        LinearLayout.LayoutParams da = new LinearLayout.LayoutParams(0, dp(42), 1f);
        da.leftMargin = dp(8);
        actions.addView(edit, ea); actions.addView(delete, da);
        add(c, actions, 14);
        return c;
    }

    private EditText field(String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setTextSize(15); e.setSingleLine(true);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setInputType(inputType);
        e.setBackgroundResource(R.drawable.bg_field);
        return e;
    }
    private Spinner spinner(String[] values, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(adapter);
        s.setSelection(selected);
        return s;
    }
    private void labelField(LinearLayout form, String name, View view, int top) {
        add(form, text(name, 13, DARK, true), top);
        add(form, view, 5);
    }

    private void editTask(ReminderTask original) {
        LinearLayout form = vertical();
        form.setPadding(dp(6), dp(5), dp(6), dp(10));
        long when = original == null ? System.currentTimeMillis() + 7200000L : original.dueAt;
        EditText title = field("مثلاً جلسه با مدیر", original == null ? "" : original.title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText notes = field("شرح کار (اختیاری)", original == null ? "" : original.notes, InputType.TYPE_CLASS_TEXT);
        EditText date = field("۱۴۰۵/۰۸/۲۸", PersianDates.date(when), InputType.TYPE_CLASS_TEXT);
        EditText time = field("۱۴:۳۰", PersianDates.time(when), InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        String[] leads = {"سر موعد", "۵ دقیقه قبل", "۱۵ دقیقه قبل", "۳۰ دقیقه قبل", "۶۰ دقیقه قبل"};
        int[] minutes = {0,5,15,30,60};
        int leadIndex = 2;
        if (original != null) for (int i=0;i<minutes.length;i++) if (minutes[i] == original.leadMinutes) leadIndex = i;
        Spinner lead = spinner(leads, leadIndex);
        String[] repeats = {"یک‌بار", "روزانه", "هفتگی"};
        int repeatIndex = original == null ? 0 : "daily".equals(original.repeat) ? 1 : "weekly".equals(original.repeat) ? 2 : 0;
        Spinner repeat = spinner(repeats, repeatIndex);
        labelField(form, "عنوان برنامه *", title, 6);
        labelField(form, "تاریخ شمسی *", date, 13);
        labelField(form, "ساعت برنامه *", time, 13);
        labelField(form, "زمان یادآوری", lead, 13);
        labelField(form, "تکرار", repeat, 13);
        labelField(form, "توضیحات", notes, 13);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(form);
        AlertDialog d = new AlertDialog.Builder(this)
            .setTitle(original == null ? "ثبت برنامه جدید" : "ویرایش برنامه")
            .setView(scroll)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", null)
            .create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(click -> {
            try {
                String taskTitle = title.getText().toString().trim();
                if (taskTitle.isEmpty()) throw new IllegalArgumentException("عنوان برنامه را وارد کنید.");
                if (taskTitle.length() > 120) throw new IllegalArgumentException("عنوان حداکثر ۱۲۰ حرف باشد.");
                String taskNotes = notes.getText().toString().trim();
                if (taskNotes.length() > 1500) throw new IllegalArgumentException("توضیحات حداکثر ۱۵۰۰ حرف باشد.");
                long dueAt = PersianDates.parse(date.getText().toString(), time.getText().toString());
                int leadMinutes = minutes[lead.getSelectedItemPosition()];
                if (dueAt - (long) leadMinutes * 60000L <= System.currentTimeMillis() + 3000L)
                    throw new IllegalArgumentException("زمان یادآوری باید در آینده باشد.");
                ReminderTask t = new ReminderTask();
                if (original != null) { t.id = original.id; ReminderScheduler.cancel(this, t.id); }
                t.title = taskTitle; t.notes = taskNotes; t.dueAt = dueAt;
                t.leadMinutes = leadMinutes;
                t.repeat = new String[]{"none", "daily", "weekly"}[repeat.getSelectedItemPosition()];
                TaskStore.save(this, t);
                ReminderScheduler.schedule(this, t);
                d.dismiss();
                showPage(false);
                toast("برنامه با موفقیت ذخیره شد.");
            } catch (Exception e) { toast(e.getMessage() == null ? "خطا در ثبت برنامه" : e.getMessage()); }
        }));
        d.show();
    }

    private void confirmDelete(ReminderTask t) {
        new AlertDialog.Builder(this).setTitle("حذف برنامه")
            .setMessage("برنامه «" + t.title + "» حذف شود؟")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف", (dialog, which) -> {
                ReminderScheduler.cancel(this, t.id);
                TaskStore.delete(this, t.id);
                showPage(false);
            }).show();
    }

    private void renderSettings() {
        LinearLayout c = card();
        add(c, text("اتصال به بازوی بله", 19, DARK, true), 0);
        add(c, text("بازوی اختصاصی یادآوری بسازید و در گفتگوی خصوصی آن /start را ارسال کنید.", 13, GRAY, false), 9);
        EditText token = field("توکن بازو", TaskStore.getToken(this), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText chat = field("شناسه عددی گفتگو", TaskStore.getChatId(this), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        labelField(c, "توکن ربات *", token, 17);
        labelField(c, "شناسه گفتگو (Chat ID) *", chat, 14);
        add(c, text("شناسه خصوصی یک عدد است؛ @نام‌کاربری را وارد نکنید.", 12, GRAY, false), 6);
        Button lookup = button("پیدا کردن شناسه از پیام‌های بازو", false);
        lookup.setMinHeight(dp(48));
        lookup.setOnClickListener(v -> {
            String tk = token.getText().toString().trim();
            if (tk.isEmpty()) { toast("ابتدا توکن را وارد کنید."); return; }
            lookup.setEnabled(false);
            io.execute(() -> {
                try {
                    Map<String,String> chats = BaleApi.findPrivateChats(tk);
                    runOnUiThread(() -> {
                        lookup.setEnabled(true);
                        if (chats.isEmpty()) {
                            new AlertDialog.Builder(this).setTitle("شناسه پیدا نشد")
                                .setMessage("در ۲۴ ساعت گذشته در گفتگوی خصوصی بازوی جدید پیام /start یا «سلام» بفرستید و دوباره امتحان کنید. اگر بازو به سرویس دیگری متصل است، بازوی جداگانه بسازید.")
                                .setPositiveButton("متوجه شدم", null).show();
                            return;
                        }
                        List<String> ids = new ArrayList<>(chats.keySet());
                        String[] entries = new String[ids.size()];
                        for (int i=0;i<ids.size();i++) entries[i] = chats.get(ids.get(i)) + "  —  " + ids.get(i);
                        new AlertDialog.Builder(this).setTitle("گفتگوی خود را انتخاب کنید")
                            .setItems(entries, (dlg, which) -> chat.setText(ids.get(which)))
                            .setNegativeButton("انصراف", null).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { lookup.setEnabled(true); showError(e); });
                }
            });
        });
        add(c, lookup, 16);
        Button save = button("ذخیره اتصال", true);
        save.setMinHeight(dp(48));
        save.setOnClickListener(v -> {
            String tk = token.getText().toString().trim();
            String id = PersianDates.ascii(chat.getText().toString()).trim();
            if (!tk.matches("[A-Za-z0-9:_-]{15,250}")) { toast("فرمت توکن معتبر نیست."); return; }
            if (!id.matches("-?[0-9]+")) { toast("شناسه گفتگو باید عددی باشد، نه نام کاربری."); return; }
            TaskStore.saveCredentials(this, tk, id);
            ReminderScheduler.rebuild(this);
            toast("تنظیمات اتصال ذخیره شد.");
        });
        add(c, save, 10);
        Button test = button("ارسال پیام آزمایشی", false);
        test.setMinHeight(dp(48));
        test.setOnClickListener(v -> {
            String tk = token.getText().toString().trim();
            String id = PersianDates.ascii(chat.getText().toString()).trim();
            if (!id.matches("-?[0-9]+")) { toast("ابتدا شناسه عددی گفتگو را وارد کنید."); return; }
            test.setEnabled(false);
            io.execute(() -> {
                try {
                    BaleApi.sendMessage(tk, id, "✅ آزمایش اتصال یادآور کاری اندروید به بله موفق بود.\nبا ثبت برنامه، یادآوری‌ها در گوشی زمان‌بندی می‌شوند.");
                    runOnUiThread(() -> {
                        test.setEnabled(true);
                        new AlertDialog.Builder(this).setTitle("ارسال موفق")
                            .setMessage("پیام آزمایشی به گفتگوی انتخاب‌شده در بله ارسال شد.")
                            .setPositiveButton("بستن", null).show();
                    });
                } catch (Exception e) { runOnUiThread(() -> { test.setEnabled(true); showError(e); }); }
            });
        });
        add(c, test, 10);
        add(content, c, 4);

        LinearLayout permissions = card();
        add(permissions, text("مجوزهای یادآوری", 17, DARK, true), 0);
        add(permissions, text("برای نزدیک‌تر بودن ساعت اعلان‌ها به زمان تعیین‌شده، مجوز Alarm & reminders را فعال کنید.", 13, GRAY, false), 9);
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        boolean exact = Build.VERSION.SDK_INT < 31 || (alarm != null && alarm.canScheduleExactAlarms());
        add(permissions, text(exact ? "✓ امکان زمان‌بندی دقیق فعال است." : "⚠ زمان‌بندی دقیق هنوز فعال نشده؛ اندروید ممکن است تأخیر ایجاد کند.", 13, exact ? TEAL : 0xFFB05C32, true), 12);
        if (!exact) {
            Button allow = button("فعال‌سازی زمان‌بندی دقیق", false);
            allow.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) { toast("مجوز را از تنظیمات برنامه فعال کنید."); }
            });
            add(permissions, allow, 12);
        }
        add(permissions, text("اندروید ۱۳ و جدیدتر: نمایش اعلان محلی نیازمند مجوز اعلان‌هاست. ارسال به بله نیز به اینترنت، روشن بودن گوشی و محدودیت‌های باتری وابسته است.", 12, GRAY, false), 14);
        add(content, permissions, 14);

        LinearLayout privacy = card();
        add(privacy, text("حریم خصوصی", 17, DARK, true), 0);
        add(privacy, text("برنامه‌ها و توکن در حافظه خصوصی همین گوشی نگهداری می‌شوند. اطلاعات فقط برای ارسال پیام به API رسمی بله منتقل می‌شوند. از نصب روی گوشی مشترک و انتشار توکن خودداری کنید.", 13, GRAY, false), 9);
        add(content, privacy, 14);
    }

    private void showError(Exception error) {
        String msg = error.getMessage() == null ? "خطای ناشناخته" : error.getMessage();
        new AlertDialog.Builder(this).setTitle("خطا در اتصال")
            .setMessage(msg).setPositiveButton("متوجه شدم", null).show();
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdown(); }
}
