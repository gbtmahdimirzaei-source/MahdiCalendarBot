package ir.balereminder.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BaleApi {
    private BaleApi() { }

    public static JSONObject call(String token, String method, JSONObject params) throws Exception {
        if (token == null || !token.matches("[A-Za-z0-9:_-]{15,250}"))
            throw new Exception("فرمت توکن بازو معتبر نیست.");
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://tapi.bale.ai/bot" + token + "/" + method);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(12000); conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] payload = params.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) throw new Exception("پاسخی از سرور بله دریافت نشد.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            try (InputStream read = in) {
                while ((n = read.read(buf)) != -1) {
                    if (out.size() + n > 500000) throw new Exception("پاسخ سرور بیش از حد بزرگ است.");
                    out.write(buf, 0, n);
                }
            }
            JSONObject response = new JSONObject(out.toString("UTF-8"));
            if (code < 200 || code >= 300 || !response.optBoolean("ok")) {
                String reason = response.optString("description", "خطای HTTP " + code);
                if (reason.contains("no such group or user"))
                    throw new Exception("شناسه گفتگو اشتباه است. باید Chat ID عددی را وارد کنید، نه @نام‌کاربری.");
                throw new Exception("خطای بله: " + reason);
            }
            return response;
        } catch (java.net.UnknownHostException e) {
            throw new Exception("اینترنت یا دسترسی به سرور بله برقرار نیست.");
        } catch (java.net.SocketTimeoutException e) {
            throw new Exception("اتصال به بله طول کشید؛ بعداً دوباره آزمایش کنید.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void sendMessage(String token, String chatId, String text) throws Exception {
        JSONObject params = new JSONObject();
        params.put("chat_id", chatId); params.put("text", text);
        call(token, "sendMessage", params);
    }

    public static Map<String, String> findPrivateChats(String token) throws Exception {
        JSONObject args = new JSONObject();
        args.put("limit",100); args.put("timeout",0);
        JSONArray updates = call(token, "getUpdates", args).optJSONArray("result");
        Map<String, String> chats = new LinkedHashMap<>();
        if (updates != null) {
            for (int i=0; i<updates.length(); i++) {
                JSONObject update = updates.optJSONObject(i);
                if (update == null) continue;
                JSONObject message = update.optJSONObject("message");
                if (message == null) message = update.optJSONObject("edited_message");
                if (message == null) continue;
                JSONObject chat = message.optJSONObject("chat");
                if (chat == null || !"private".equals(chat.optString("type"))) continue;
                String id = chat.optString("id", "");
                if (!id.matches("[0-9]+")) continue;
                String name = (chat.optString("first_name") + " " + chat.optString("last_name")).trim();
                if (name.isEmpty()) name = chat.optString("username", "گفتگوی خصوصی");
                chats.put(id, name);
            }
        }
        return chats;
    }

    public static String reminderText(ReminderTask t, boolean late) {
        StringBuilder b = new StringBuilder();
        b.append("🔔 یادآوری برنامه کاری\n\n📌 ").append(t.title);
        b.append("\n🗓 زمان برنامه: ").append(PersianDates.full(t.dueAt));
        b.append("\n⏰ ").append(t.leadMinutes == 0 ? "سر موعد" : PersianDates.fa(t.leadMinutes + "") + " دقیقه قبل");
        if (!"none".equals(t.repeat)) b.append("\n🔁 ").append("daily".equals(t.repeat) ? "روزانه" : "هفتگی");
        if (!t.notes.trim().isEmpty()) b.append("\n📝 ").append(t.notes);
        if (late) b.append("\n\n⚠ این یادآوری با تأخیر ارسال شد.");
        String text = b.toString();
        return text.length() > 3950 ? text.substring(0, 3950) : text;
    }
}
