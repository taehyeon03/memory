package com.example.memorygraph;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class BirthdayReminder {
    private static final String CHANNEL_ID = "birthday_reminders";
    private static final int REMINDER_DAYS_BEFORE = 7;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private BirthdayReminder() {
    }

    static String normalizeBirthday(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim().replace('.', '-').replace('/', '-');
        if (value.isEmpty()) {
            return "";
        }
        String[] parts = value.split("-");
        try {
            if (parts.length == 2) {
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                return isValidMonthDay(month, day) ? String.format(Locale.US, "%02d-%02d", month, day) : "";
            }
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                return isValidDate(year, month, day)
                        ? String.format(Locale.US, "%04d-%02d-%02d", year, month, day) : "";
            }
        } catch (NumberFormatException ignored) {
        }
        return "";
    }

    static String genderLabel(String gender) {
        if ("female".equals(gender)) {
            return "여성";
        }
        if ("male".equals(gender)) {
            return "남성";
        }
        if ("other".equals(gender)) {
            return "기타";
        }
        return "미지정";
    }

    static List<UpcomingBirthday> upcomingBirthdays(List<Models.Person> persons, int windowDays, int limit) {
        List<UpcomingBirthday> events = new ArrayList<>();
        long today = startOfToday();
        if (persons == null) {
            return events;
        }
        for (Models.Person person : persons) {
            BirthdayParts parts = BirthdayParts.parse(person.birthday);
            if (parts == null) {
                continue;
            }
            Calendar next = nextBirthday(parts, today);
            int daysUntil = (int) ((startOfDay(next.getTimeInMillis()) - today) / DAY_MS);
            if (daysUntil >= 0 && daysUntil <= windowDays) {
                events.add(new UpcomingBirthday(person, parts.label(), daysUntil));
            }
        }
        Collections.sort(events, Comparator.comparingInt(a -> a.daysUntil));
        if (limit > 0 && events.size() > limit) {
            return new ArrayList<>(events.subList(0, limit));
        }
        return events;
    }

    static void scheduleAll(Context context, List<Models.Person> persons) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || persons == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Models.Person person : persons) {
            BirthdayParts parts = BirthdayParts.parse(person.birthday);
            PendingIntent intent = pendingIntent(context, person, 0);
            if (parts == null) {
                alarmManager.cancel(intent);
                continue;
            }
            ScheduledReminder reminder = scheduledReminder(context, person.id, parts, now);
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerAt,
                    pendingIntent(context, person, reminder.eventYear));
        }
    }

    static void notifyBirthday(Context context, long personId, String expectedBirthday, int eventYear) {
        Models.Person target = null;
        for (Models.Person person : new MemoryDatabase(context).listPersons()) {
            if (person.id == personId) {
                target = person;
                break;
            }
        }
        if (target == null || target.birthday.isEmpty() || !target.birthday.equals(expectedBirthday)) {
            return;
        }
        if (eventYear > 0 && wasReminderSent(context, personId, eventYear)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "생일 알림", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        String name = target.name.isEmpty() ? "이 사람" : target.name;
        String text = name + " 생일이 일주일 뒤입니다";
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("생일 챙기기")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        manager.notify((int) Math.min(Integer.MAX_VALUE, personId), builder.build());
        if (eventYear > 0) {
            markReminderSent(context, personId, eventYear);
        }
        scheduleAll(context, new MemoryDatabase(context).listPersons());
    }

    private static PendingIntent pendingIntent(Context context, Models.Person person, int eventYear) {
        Intent intent = new Intent(context, BirthdayReminderReceiver.class);
        intent.putExtra("person_id", person.id);
        intent.putExtra("birthday", person.birthday);
        intent.putExtra("event_year", eventYear);
        int requestCode = (int) Math.min(Integer.MAX_VALUE, person.id);
        return PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static ScheduledReminder scheduledReminder(Context context, long personId, BirthdayParts parts, long now) {
        Calendar birthday = nextBirthday(parts, now);
        Calendar reminder = (Calendar) birthday.clone();
        reminder.add(Calendar.DAY_OF_YEAR, -REMINDER_DAYS_BEFORE);
        reminder.set(Calendar.HOUR_OF_DAY, 9);
        reminder.set(Calendar.MINUTE, 0);
        reminder.set(Calendar.SECOND, 0);
        reminder.set(Calendar.MILLISECOND, 0);
        if (reminder.getTimeInMillis() <= now) {
            int eventYear = birthday.get(Calendar.YEAR);
            if (startOfDay(birthday.getTimeInMillis()) >= startOfToday()
                    && !wasReminderSent(context, personId, eventYear)) {
                return new ScheduledReminder(now + 5000L, eventYear);
            }
            return nextYearReminder(parts, birthday.get(Calendar.YEAR) + 1);
        }
        return new ScheduledReminder(reminder.getTimeInMillis(), birthday.get(Calendar.YEAR));
    }

    private static ScheduledReminder nextYearReminder(BirthdayParts parts, int year) {
        Calendar birthday = adjustedBirthday(parts, year);
        Calendar reminder = (Calendar) birthday.clone();
        reminder.add(Calendar.DAY_OF_YEAR, -REMINDER_DAYS_BEFORE);
        reminder.set(Calendar.HOUR_OF_DAY, 9);
        reminder.set(Calendar.MINUTE, 0);
        reminder.set(Calendar.SECOND, 0);
        reminder.set(Calendar.MILLISECOND, 0);
        return new ScheduledReminder(reminder.getTimeInMillis(), birthday.get(Calendar.YEAR));
    }

    private static boolean wasReminderSent(Context context, long personId, int eventYear) {
        return reminderPrefs(context).getBoolean(reminderKey(personId, eventYear), false);
    }

    private static void markReminderSent(Context context, long personId, int eventYear) {
        reminderPrefs(context).edit().putBoolean(reminderKey(personId, eventYear), true).apply();
    }

    private static SharedPreferences reminderPrefs(Context context) {
        return context.getSharedPreferences("birthday_reminder_prefs", Context.MODE_PRIVATE);
    }

    private static String reminderKey(long personId, int eventYear) {
        return "sent_" + personId + "_" + eventYear;
    }

    private static Calendar nextBirthday(BirthdayParts parts, long fromTime) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(fromTime);
        Calendar birthday = adjustedBirthday(parts, now.get(Calendar.YEAR));
        if (startOfDay(birthday.getTimeInMillis()) < startOfDay(fromTime)) {
            birthday = adjustedBirthday(parts, now.get(Calendar.YEAR) + 1);
        }
        return birthday;
    }

    private static Calendar adjustedBirthday(BirthdayParts parts, int year) {
        int day = parts.day;
        if (parts.month == 2 && parts.day == 29 && !isValidDate(year, 2, 29)) {
            day = 28;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, parts.month - 1, day, 0, 0, 0);
        return calendar;
    }

    private static long startOfToday() {
        return startOfDay(System.currentTimeMillis());
    }

    private static long startOfDay(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static boolean isValidMonthDay(int month, int day) {
        return isValidDate(2024, month, day);
    }

    private static boolean isValidDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setLenient(false);
        calendar.clear();
        try {
            calendar.set(year, month - 1, day);
            calendar.getTime();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static final class UpcomingBirthday {
        final Models.Person person;
        final String dateLabel;
        final int daysUntil;

        UpcomingBirthday(Models.Person person, String dateLabel, int daysUntil) {
            this.person = person;
            this.dateLabel = dateLabel;
            this.daysUntil = daysUntil;
        }

        String countdownLabel() {
            if (daysUntil == 0) {
                return "오늘";
            }
            if (daysUntil == 1) {
                return "내일";
            }
            return "D-" + daysUntil;
        }
    }

    private static final class ScheduledReminder {
        final long triggerAt;
        final int eventYear;

        ScheduledReminder(long triggerAt, int eventYear) {
            this.triggerAt = triggerAt;
            this.eventYear = eventYear;
        }
    }

    private static final class BirthdayParts {
        final int month;
        final int day;

        BirthdayParts(int month, int day) {
            this.month = month;
            this.day = day;
        }

        static BirthdayParts parse(String birthday) {
            String normalized = normalizeBirthday(birthday);
            if (normalized.isEmpty()) {
                return null;
            }
            String monthDay = normalized.length() == 10 ? normalized.substring(5) : normalized;
            String[] parts = monthDay.split("-");
            return new BirthdayParts(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        String label() {
            return String.format(Locale.US, "%02d-%02d", month, day);
        }
    }
}
