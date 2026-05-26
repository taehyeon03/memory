package com.example.memorygraph;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BirthdayReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long personId = intent.getLongExtra("person_id", -1L);
        String birthday = intent.getStringExtra("birthday");
        int eventYear = intent.getIntExtra("event_year", 0);
        if (personId >= 0 && birthday != null) {
            BirthdayReminder.notifyBirthday(context, personId, birthday, eventYear);
        }
    }
}
