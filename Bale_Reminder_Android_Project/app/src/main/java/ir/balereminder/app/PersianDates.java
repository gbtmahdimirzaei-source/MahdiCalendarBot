package ir.balereminder.app;

import android.icu.util.Calendar;
import android.icu.util.PersianCalendar;
import java.util.Locale;

public final class PersianDates {
    private PersianDates() { }

    public static String ascii(String text) {
        return text.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
            .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
            .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
            .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
            .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    }

    public static String fa(String text) {
        return text.replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳')
            .replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷')
            .replace('8','۸').replace('9','۹');
    }

    public static String date(long millis) {
        PersianCalendar c = new PersianCalendar();
        c.setTimeInMillis(millis);
        return fa(String.format(Locale.US, "%04d/%02d/%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DATE)));
    }

    public static String time(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return fa(String.format(Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)));
    }

    public static String full(long millis) {
        return date(millis) + "  ·  " + time(millis);
    }

    public static long parse(String rawDate, String rawTime) {
        String date = ascii(rawDate).trim().replace('-', '/').replace('.', '/');
        String time = ascii(rawTime).trim();
        if (!date.matches("[0-9]{4}/[0-9]{1,2}/[0-9]{1,2}"))
            throw new IllegalArgumentException("تاریخ شمسی را مانند ۱۴۰۵/۰۸/۲۸ وارد کنید.");
        if (!time.matches("[0-9]{1,2}:[0-9]{2}"))
            throw new IllegalArgumentException("ساعت باید مانند ۱۴:۳۰ باشد.");
        String[] d = date.split("/");
        String[] tm = time.split(":");
        int y = Integer.parseInt(d[0]), m = Integer.parseInt(d[1]), day = Integer.parseInt(d[2]);
        int h = Integer.parseInt(tm[0]), min = Integer.parseInt(tm[1]);
        if (y < 1300 || y > 1600 || m < 1 || m > 12 || day < 1 || day > 31 || h > 23 || min > 59)
            throw new IllegalArgumentException("تاریخ یا ساعت معتبر نیست.");
        PersianCalendar c = new PersianCalendar();
        c.setLenient(false);
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, m - 1);
        c.set(Calendar.DATE, day);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, min);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        try {
            long value = c.getTimeInMillis();
            // Calendar is initialized in the device's local time zone.
            if (!ascii(date(value)).equals(String.format(Locale.US, "%04d/%02d/%02d",y,m,day)) ||
                    !ascii(time(value)).equals(String.format(Locale.US, "%02d:%02d",h,min))) {
                throw new IllegalArgumentException("تاریخ یا ساعت تقویم معتبر نیست.");
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("تاریخ شمسی یا ساعت معتبر نیست.");
        }
    }

    public static long advance(long dueAt, String repeat, int lead, long now) {
        if ("none".equals(repeat)) return dueAt;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(dueAt);
        int interval = "weekly".equals(repeat) ? 7 : 1;
        for (int i = 0; i < 8000; i++) {
            c.add(java.util.Calendar.DAY_OF_YEAR, interval);
            long stamp = c.getTimeInMillis();
            if (stamp - (long) lead * 60000L > now) return stamp;
        }
        throw new IllegalArgumentException("امکان زمان‌بندی تکرار وجود ندارد.");
    }
}
