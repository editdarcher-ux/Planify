package com.planify.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            PlanStore store = new PlanStore(context);
            for (PlanItem item : store.all()) {
                if (item.notify) AlarmScheduler.scheduleItem(context, item, store);
            }
        } catch (Throwable ignored) {}
    }
}
