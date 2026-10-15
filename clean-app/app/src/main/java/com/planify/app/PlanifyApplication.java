package com.planify.app;

import android.app.Application;
import android.os.Build;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Guarda el último cierre inesperado para poder mostrarlo al volver a abrir la app.
 * Así cualquier fallo en un móvil real se puede copiar y diagnosticar.
 */
public final class PlanifyApplication extends Application {
    public static final String PREFS = "planify_crash_diag";
    public static final String KEY = "last_crash";

    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println("Planify " + versionName());
                pw.println("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                pw.println("Dispositivo: " + Build.MANUFACTURER + " " + Build.MODEL);
                pw.println("Hilo: " + (thread == null ? "?" : thread.getName()));
                pw.println();
                if (error != null) error.printStackTrace(pw);
                pw.flush();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, sw.toString()).putBoolean("shown", false).commit();
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
            else System.exit(10);
        });
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable e) { return "?"; }
    }
}
