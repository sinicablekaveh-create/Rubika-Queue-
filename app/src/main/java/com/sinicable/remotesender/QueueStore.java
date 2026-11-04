package com.sinicable.remotesender;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class QueueStore {
    public static final String BALE = "BALE";
    public static final String EITAA = "EITAA";
    public static final String RUBIKA = "RUBIKA";

    private final SharedPreferences prefs;

    public QueueStore(Context context) {
        prefs = context.getSharedPreferences("remote_sender", Context.MODE_PRIVATE);
    }

    public synchronized void setNumbers(List<String> numbers) {
        JSONArray array = new JSONArray();
        for (String number : numbers) array.put(number);
        prefs.edit().putString("numbers", array.toString()).apply();
    }

    public synchronized List<String> numbers() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("numbers", "[]"));
            for (int i = 0; i < array.length(); i++) out.add(array.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public synchronized void add(String number) {
        List<String> all = numbers();
        if (!all.contains(number)) all.add(number);
        setNumbers(all);
    }

    public synchronized void saveConfig(
            String message,
            int baleMinutes,
            int eitaaMinutes,
            int rubikaMinutes,
            Set<String> apps,
            Uri imageUri) {
        SharedPreferences.Editor edit = prefs.edit()
                .putString("message", message)
                .putInt("mins_" + BALE, clamp(baleMinutes))
                .putInt("mins_" + EITAA, clamp(eitaaMinutes))
                .putInt("mins_" + RUBIKA, clamp(rubikaMinutes))
                .putStringSet("apps", new HashSet<>(apps));

        if (imageUri == null) edit.remove("image_uri");
        else edit.putString("image_uri", imageUri.toString());
        edit.apply();
    }

    public String message() {
        return prefs.getString("message", "");
    }

    public int interval(String app) {
        return prefs.getInt("mins_" + app, 5);
    }

    public Uri imageUri() {
        String value = prefs.getString("image_uri", null);
        return value == null ? null : Uri.parse(value);
    }

    public synchronized void rebuild() {
        JSONArray tasks = new JSONArray();
        Set<String> apps = prefs.getStringSet("apps", Collections.emptySet());

        for (String phone : numbers()) {
            for (String app : apps) {
                try {
                    JSONObject task = new JSONObject();
                    task.put("id", UUID.randomUUID().toString());
                    task.put("phone", phone);
                    task.put("app", app);
                    task.put("status", "PENDING");
                    task.put("created", System.currentTimeMillis());
                    tasks.put(task);
                } catch (Exception ignored) {}
            }
        }

        prefs.edit()
                .putString("tasks", tasks.toString())
                .putLong("ready_" + BALE, 0L)
                .putLong("ready_" + EITAA, 0L)
                .putLong("ready_" + RUBIKA, 0L)
                .apply();
    }

    public synchronized Selection selection() {
        try {
            JSONArray tasks = new JSONArray(prefs.getString("tasks", "[]"));
            long now = System.currentTimeMillis();

            Task bestWaiting = null;
            long shortestWait = Long.MAX_VALUE;

            for (int i = 0; i < tasks.length(); i++) {
                JSONObject object = tasks.getJSONObject(i);
                if (!"PENDING".equals(object.optString("status"))) continue;

                Task task = Task.from(object, i);
                long readyAt = prefs.getLong("ready_" + task.app, 0L);
                long wait = Math.max(0L, readyAt - now);

                if (wait == 0L) return new Selection(task, 0L);
                if (wait < shortestWait) {
                    shortestWait = wait;
                    bestWaiting = task;
                }
            }

            return bestWaiting == null
                    ? new Selection(null, 0L)
                    : new Selection(bestWaiting, shortestWait);
        } catch (Exception e) {
            return new Selection(null, 0L);
        }
    }

    public synchronized void complete(Task task, String status, boolean enforceInterval) {
        if (task == null) return;
        try {
            JSONArray tasks = new JSONArray(prefs.getString("tasks", "[]"));
            if (task.index >= 0 && task.index < tasks.length()) {
                JSONObject object = tasks.getJSONObject(task.index);
                if (task.id.equals(object.optString("id"))) {
                    object.put("status", status);
                    object.put("updated", System.currentTimeMillis());
                }
            }

            SharedPreferences.Editor edit = prefs.edit().putString("tasks", tasks.toString());
            if (enforceInterval) {
                long ready = System.currentTimeMillis() + interval(task.app) * 60_000L;
                edit.putLong("ready_" + task.app, ready);
            }
            edit.apply();
        } catch (Exception ignored) {}
    }

    public synchronized void clear() {
        prefs.edit()
                .putString("tasks", "[]")
                .putLong("ready_" + BALE, 0L)
                .putLong("ready_" + EITAA, 0L)
                .putLong("ready_" + RUBIKA, 0L)
                .apply();
    }

    public synchronized String stats() {
        int pending = 0;
        int sent = 0;
        int skipped = 0;
        int total = 0;

        try {
            JSONArray tasks = new JSONArray(prefs.getString("tasks", "[]"));
            total = tasks.length();
            for (int i = 0; i < tasks.length(); i++) {
                String state = tasks.getJSONObject(i).optString("status");
                if ("PENDING".equals(state)) pending++;
                else if ("SENT".equals(state)) sent++;
                else if ("SKIPPED".equals(state)) skipped++;
            }
        } catch (Exception ignored) {}

        return "Queue " + total
                + " | Pending " + pending
                + " | Sent " + sent
                + " | Skipped " + skipped;
    }

    public static String packageName(String app) {
        if (BALE.equals(app)) return "ir.nasim";
        if (EITAA.equals(app)) return "ir.eitaa.messenger";
        return "ir.resaneh1.iptv";
    }

    private static int clamp(int minutes) {
        return Math.max(1, Math.min(1440, minutes));
    }

    public static final class Selection {
        public final Task task;
        public final long waitMillis;

        Selection(Task task, long waitMillis) {
            this.task = task;
            this.waitMillis = waitMillis;
        }
    }

    public static final class Task {
        public final String id;
        public final String phone;
        public final String app;
        public final String status;
        public final int index;

        Task(String id, String phone, String app, String status, int index) {
            this.id = id;
            this.phone = phone;
            this.app = app;
            this.status = status;
            this.index = index;
        }

        static Task from(JSONObject object, int index) {
            return new Task(
                    object.optString("id"),
                    object.optString("phone"),
                    object.optString("app"),
                    object.optString("status"),
                    index
            );
        }
    }
}
