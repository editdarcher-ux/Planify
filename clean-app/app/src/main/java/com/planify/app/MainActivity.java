package com.planify.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_IMAGE = 501;
    private static final int REQ_SOUND = 502;
    private static final int REQ_NOTIFY = 503;
    private static final String[] TYPE_CODES = {"CLASS","EXAM","DELIVERY","TASK","EVENT","REMINDER","ALARM","OTHER"};
    private static final String[] TYPE_LABELS = {"Clase","Examen","Entrega","Tarea","Evento","Recordatorio","Alarma","Otro"};
    private static final int[] PALETTE = {
            0xFF4E79A7, 0xFFE15759, 0xFFF28E2B, 0xFFEDC948,
            0xFF59A14F, 0xFF76B7B2, 0xFF7A6FF0, 0xFFB07AA1,
            0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AC, 0xFF26A69A,
            0xFFEF5350, 0xFFAB47BC, 0xFF5C6BC0, 0xFFFF7043
    };

    private PlanStore store;
    private FrameLayout content;
    private LinearLayout nav;
    private String currentScreen = "schedule";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private ImageView scheduleImageView;
    private LocalDate selectedDate = LocalDate.now();

    private final ArrayList<TimerState> timers = new ArrayList<>();
    private int nextTimerId = 1;
    private LinearLayout timersPanel;
    private TextView pomodoroClock;
    private TextView pomodoroPhase;
    private TextView pomodoroCount;
    private EditText pomodoroStudyInput;
    private EditText pomodoroBreakInput;
    private EditText pomodoroCyclesInput;
    private long pomodoroEndMs = 0L;
    private long pomodoroPausedMs = 25L * 60_000L;
    private boolean pomodoroRunning = false;
    private boolean pomodoroBreak = false;
    private int pomodoroCompleted = 0;
    private int pomodoroTarget = 4;
    private int pomodoroStudyMin = 25;
    private int pomodoroBreakMin = 5;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try {
                updatePomodoro();
                updateTimers();
            } catch (Throwable ignored) {}
            handler.postDelayed(this, 500L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PlanStore(this);
        restoreStudyState();
        buildShell();
        setScreen("schedule");
        handler.post(ticker);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(surface());
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
        setContentView(root);
        rebuildNav();
    }

    private void rebuildNav() {
        nav.removeAllViews();
        addNav("Horario", "schedule");
        addNav("Calendario", "calendar");
        addNav("Estudio", "study");
        addNav("Ajustes", "settings");
    }

    private void addNav(String label, String screen) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setTextColor(currentScreen.equals(screen) ? primary() : text());
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> safeAction(() -> setScreen(screen)));
        nav.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void setScreen(String screen) {
        currentScreen = screen;
        rebuildNav();
        content.removeAllViews();
        try {
            if ("calendar".equals(screen)) showCalendar();
            else if ("study".equals(screen)) showStudy();
            else if ("settings".equals(screen)) showSettings();
            else showSchedule();
        } catch (Throwable e) {
            showFallback();
        }
    }

    private void showFallback() {
        LinearLayout box = vertical(dp(18));
        box.setGravity(Gravity.CENTER);
        box.addView(label("No se pudo abrir esta sección", 18, true));
        Button retry = button("Volver a Horario", () -> setScreen("schedule"));
        box.addView(retry, matchWrap(dp(12)));
        content.addView(box, matchMatch());
    }

    private void showSchedule() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical(dp(18));
        scroll.addView(body);
        body.addView(topBar("Horario", true));

        LinearLayout imageCard = card();
        String imageUri = store.getScheduleImage();
        if (imageUri == null || imageUri.isEmpty()) {
            imageCard.addView(button("Agregar imagen", this::pickScheduleImage), matchWrap(0));
        } else {
            scheduleImageView = new ImageView(this);
            scheduleImageView.setAdjustViewBounds(true);
            scheduleImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageCard.addView(scheduleImageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
            LinearLayout actions = row();
            actions.addView(button("Cambiar", this::pickScheduleImage), weight());
            actions.addView(buttonOutline("Quitar", () -> {
                store.setScheduleImage(null);
                scheduleImageView = null;
                setScreen("schedule");
            }), weight());
            imageCard.addView(actions, matchWrap(dp(10)));
            loadScheduleBitmap(imageUri, scheduleImageView);
        }
        body.addView(imageCard, matchWrap(dp(12)));

        List<PlanItem> upcoming = new ArrayList<>();
        String today = LocalDate.now().toString();
        for (PlanItem item : store.all()) {
            if (!"TASK".equals(item.type) && item.date != null && item.date.compareTo(today) >= 0) upcoming.add(item);
            if (upcoming.size() >= 8) break;
        }
        if (!upcoming.isEmpty()) {
            TextView h = label("Próximos", 16, true);
            h.setPadding(0, dp(8), 0, dp(4));
            body.addView(h);
            for (PlanItem item : upcoming) body.addView(itemRow(item), matchWrap(dp(6)));
        }
        body.addView(spacer(dp(90)));
        content.addView(scroll, matchMatch());
    }

    private void pickScheduleImage() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_IMAGE);
        } catch (Exception e) { toast("No se pudo abrir el selector de imágenes"); }
    }

    private void showCalendar() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical(dp(18));
        scroll.addView(body);
        body.addView(topBar("Calendario", true));

        LinearLayout selectors = row();
        Spinner monthSpinner = new Spinner(this);
        String[] months = {"Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        monthSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, months));
        Spinner yearSpinner = new Spinner(this);
        ArrayList<String> years = new ArrayList<>();
        for (int y = 2000; y <= 2100; y++) years.add(String.valueOf(y));
        yearSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years));
        monthSpinner.setSelection(selectedDate.getMonthValue() - 1, false);
        yearSpinner.setSelection(selectedDate.getYear() - 2000, false);
        selectors.addView(monthSpinner, weight());
        selectors.addView(yearSpinner, weight());
        body.addView(selectors, matchWrap(dp(8)));

        LinearLayout calendarHost = vertical(0);
        body.addView(calendarHost, matchWrap(dp(6)));
        LinearLayout dayList = vertical(0);
        body.addView(dayList, matchWrap(dp(8)));

        final boolean[] ready = {false};
        Runnable rebuild = () -> {
            if (!ready[0]) return;
            int year = 2000 + yearSpinner.getSelectedItemPosition();
            int month = monthSpinner.getSelectedItemPosition() + 1;
            int day = Math.min(selectedDate.getDayOfMonth(), YearMonth.of(year, month).lengthOfMonth());
            selectedDate = LocalDate.of(year, month, day);
            renderCalendar(calendarHost, dayList, year, month);
        };
        monthSpinner.setOnItemSelectedListener(new SimpleItemSelected(rebuild));
        yearSpinner.setOnItemSelectedListener(new SimpleItemSelected(rebuild));
        ready[0] = true;
        renderCalendar(calendarHost, dayList, selectedDate.getYear(), selectedDate.getMonthValue());

        body.addView(spacer(dp(90)));
        content.addView(scroll, matchMatch());
    }

    private void renderCalendar(LinearLayout host, LinearLayout dayList, int year, int month) {
        host.removeAllViews();
        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(true);
        String[] headers = {"lu","ma","mi","ju","vi","sá","do"};
        TableRow header = new TableRow(this);
        for (String h : headers) {
            TextView t = label(h, 12, true);
            t.setGravity(Gravity.CENTER);
            header.addView(t, new TableRow.LayoutParams(0, dp(34), 1f));
        }
        table.addView(header);

        YearMonth ym = YearMonth.of(year, month);
        int offset = LocalDate.of(year, month, 1).getDayOfWeek().getValue() - 1;
        int total = ym.lengthOfMonth();
        int cursor = 1 - offset;
        for (int r = 0; r < 6; r++) {
            TableRow tr = new TableRow(this);
            for (int c = 0; c < 7; c++, cursor++) {
                TextView cell = label("", 15, false);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(2), dp(2), dp(2));
                if (cursor >= 1 && cursor <= total) {
                    final int day = cursor;
                    LocalDate d = LocalDate.of(year, month, day);
                    boolean has = false;
                    for (PlanItem item : store.all()) if (d.toString().equals(item.date)) { has = true; break; }
                    cell.setText(has ? day + " •" : String.valueOf(day));
                    if (d.equals(selectedDate)) cell.setBackground(round(primarySoft(), 18));
                    cell.setOnClickListener(v -> safeAction(() -> {
                        selectedDate = d;
                        renderCalendar(host, dayList, year, month);
                    }));
                }
                tr.addView(cell, new TableRow.LayoutParams(0, dp(50), 1f));
            }
            table.addView(tr);
            if (cursor > total) break;
        }
        host.addView(table, matchWrap(0));
        renderDayItems(dayList, selectedDate);
    }

    private void renderDayItems(LinearLayout list, LocalDate date) {
        list.removeAllViews();
        TextView dateTitle = label(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("es","ES"))), 16, true);
        dateTitle.setPadding(0, dp(8), 0, dp(4));
        list.addView(dateTitle);
        boolean any = false;
        for (PlanItem item : store.all()) {
            if (date.toString().equals(item.date)) {
                any = true;
                list.addView(itemRow(item), matchWrap(dp(6)));
            }
        }
        if (!any) list.addView(label("Sin elementos", 14, false));
    }

    private void showStudy() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical(dp(18));
        scroll.addView(body);
        body.addView(topBar("Estudio", true));

        LinearLayout pomo = card();
        pomo.addView(label("Pomodoro", 18, true));
        pomodoroPhase = label(pomodoroBreak ? "Descanso" : "Estudio", 14, true);
        pomodoroPhase.setTextColor(primary());
        pomo.addView(pomodoroPhase, matchWrap(dp(6)));
        pomodoroClock = label(formatMs(pomodoroRunning ? Math.max(0, pomodoroEndMs - System.currentTimeMillis()) : pomodoroPausedMs), 34, true);
        pomo.addView(pomodoroClock);
        pomodoroCount = label((pomodoroCompleted) + "/" + pomodoroTarget, 13, false);
        pomo.addView(pomodoroCount);

        LinearLayout fields = row();
        pomodoroStudyInput = numberField("Estudio", String.valueOf(pomodoroStudyMin));
        pomodoroBreakInput = numberField("Descanso", String.valueOf(pomodoroBreakMin));
        pomodoroCyclesInput = numberField("Pomodoros", String.valueOf(pomodoroTarget));
        fields.addView(pomodoroStudyInput, weight());
        fields.addView(pomodoroBreakInput, weight());
        fields.addView(pomodoroCyclesInput, weight());
        pomo.addView(fields, matchWrap(dp(10)));

        LinearLayout pomoActions = row();
        pomoActions.addView(button(pomodoroRunning ? "Pausar" : "Iniciar", () -> {
            if (pomodoroRunning) pausePomodoro(); else startPomodoro();
            setScreen("study");
        }), weight());
        pomoActions.addView(buttonOutline("Reiniciar", () -> {
            resetPomodoro();
            setScreen("study");
        }), weight());
        pomo.addView(pomoActions, matchWrap(dp(10)));
        body.addView(pomo, matchWrap(dp(12)));

        LinearLayout timersCard = card();
        LinearLayout timerHeader = row();
        timerHeader.addView(label("Temporizadores", 18, true), weight());
        Button addTimer = button("Agregar", () -> {
            TimerState timer = new TimerState(nextTimerId++);
            timers.add(timer);
            renderTimersPanel();
        });
        timerHeader.addView(addTimer);
        timersCard.addView(timerHeader);
        timersPanel = vertical(0);
        timersCard.addView(timersPanel, matchWrap(dp(6)));
        body.addView(timersCard, matchWrap(dp(12)));
        renderTimersPanel();

        LinearLayout tasksCard = card();
        tasksCard.addView(label("Tareas", 18, true));
        boolean any = false;
        for (PlanItem item : store.all()) {
            if ("TASK".equals(item.type) || "DELIVERY".equals(item.type)) {
                any = true;
                CheckBox cb = new CheckBox(this);
                cb.setText(item.title + (item.date.isEmpty() ? "" : "  ·  " + item.date));
                cb.setTextColor(text());
                cb.setChecked(item.done);
                cb.setOnCheckedChangeListener((v, checked) -> safeAction(() -> {
                    item.done = checked;
                    store.upsert(item);
                }));
                cb.setOnLongClickListener(v -> { safeAction(() -> showEditor(item, item.type)); return true; });
                tasksCard.addView(cb, matchWrap(dp(4)));
            }
        }
        if (!any) tasksCard.addView(label("Sin tareas", 14, false), matchWrap(dp(8)));
        body.addView(tasksCard, matchWrap(dp(12)));
        body.addView(spacer(dp(90)));
        content.addView(scroll, matchMatch());
        updatePomodoro();
    }

    private void startPomodoro() {
        pomodoroStudyMin = clamp(parseInt(pomodoroStudyInput, 25), 1, 180);
        pomodoroBreakMin = clamp(parseInt(pomodoroBreakInput, 5), 1, 120);
        pomodoroTarget = clamp(parseInt(pomodoroCyclesInput, 4), 1, 100);
        if (pomodoroPausedMs <= 0) pomodoroPausedMs = (pomodoroBreak ? pomodoroBreakMin : pomodoroStudyMin) * 60_000L;
        pomodoroEndMs = System.currentTimeMillis() + pomodoroPausedMs;
        pomodoroRunning = true;
        scheduleStudyAlarm(91000, pomodoroEndMs, "Pomodoro", pomodoroBreak ? "Descanso terminado" : "Sesión terminada");
        saveStudyState();
    }

    private void pausePomodoro() {
        if (!pomodoroRunning) return;
        pomodoroPausedMs = Math.max(0, pomodoroEndMs - System.currentTimeMillis());
        pomodoroRunning = false;
        pomodoroEndMs = 0L;
        cancelAlarm(91000);
        saveStudyState();
    }

    private void resetPomodoro() {
        pomodoroRunning = false;
        pomodoroBreak = false;
        pomodoroCompleted = 0;
        pomodoroPausedMs = pomodoroStudyMin * 60_000L;
        pomodoroEndMs = 0L;
        cancelAlarm(91000);
        saveStudyState();
    }

    private void updatePomodoro() {
        if (pomodoroRunning && pomodoroEndMs > 0 && System.currentTimeMillis() >= pomodoroEndMs) {
            if (!pomodoroBreak) {
                pomodoroCompleted++;
                if (pomodoroCompleted >= pomodoroTarget) {
                    pomodoroRunning = false;
                    pomodoroPausedMs = 0L;
                    pomodoroEndMs = 0L;
                } else {
                    pomodoroBreak = true;
                    pomodoroPausedMs = pomodoroBreakMin * 60_000L;
                    pomodoroEndMs = System.currentTimeMillis() + pomodoroPausedMs;
                    scheduleStudyAlarm(91000, pomodoroEndMs, "Pomodoro", "Descanso terminado");
                }
            } else {
                pomodoroBreak = false;
                pomodoroPausedMs = pomodoroStudyMin * 60_000L;
                pomodoroEndMs = System.currentTimeMillis() + pomodoroPausedMs;
                scheduleStudyAlarm(91000, pomodoroEndMs, "Pomodoro", "Sesión terminada");
            }
            saveStudyState();
        }
        long remaining = pomodoroRunning ? Math.max(0, pomodoroEndMs - System.currentTimeMillis()) : pomodoroPausedMs;
        if (pomodoroClock != null) pomodoroClock.setText(formatMs(remaining));
        if (pomodoroPhase != null) pomodoroPhase.setText(pomodoroBreak ? "Descanso" : "Estudio");
        if (pomodoroCount != null) pomodoroCount.setText(pomodoroCompleted + "/" + pomodoroTarget);
    }

    private void renderTimersPanel() {
        if (timersPanel == null) return;
        timersPanel.removeAllViews();
        for (TimerState timer : new ArrayList<>(timers)) {
            LinearLayout box = cardInner();
            LinearLayout line = row();
            EditText input = numberField("Min", String.valueOf(timer.minutes));
            TextView clock = label(formatMs(timer.remainingMs()), 22, true);
            timer.clock = clock;
            line.addView(input, weight());
            line.addView(clock, weight());
            box.addView(line);
            LinearLayout actions = row();
            actions.addView(button(timer.running ? "Pausar" : "Iniciar", () -> {
                timer.minutes = clamp(parseInt(input, 10), 1, 1440);
                if (timer.running) timer.pause(); else timer.start();
                if (timer.running) scheduleStudyAlarm(92000 + timer.id, timer.endMs, "Temporizador", "Tiempo finalizado");
                else cancelAlarm(92000 + timer.id);
                renderTimersPanel();
            }), weight());
            actions.addView(buttonOutline("Reiniciar", () -> {
                timer.minutes = clamp(parseInt(input, 10), 1, 1440);
                timer.reset();
                cancelAlarm(92000 + timer.id);
                renderTimersPanel();
            }), weight());
            actions.addView(buttonOutline("Quitar", () -> {
                cancelAlarm(92000 + timer.id);
                timers.remove(timer);
                renderTimersPanel();
            }), weight());
            box.addView(actions, matchWrap(dp(8)));
            timersPanel.addView(box, matchWrap(dp(8)));
        }
    }

    private void updateTimers() {
        long now = System.currentTimeMillis();
        for (TimerState timer : timers) {
            if (timer.running && now >= timer.endMs) {
                timer.running = false;
                timer.pausedMs = 0L;
            }
            if (timer.clock != null) timer.clock.setText(formatMs(timer.remainingMs()));
        }
    }

    private void showSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = vertical(dp(18));
        scroll.addView(body);
        body.addView(label("Ajustes", 24, true), matchWrap(dp(6)));

        LinearLayout alerts = card();
        alerts.addView(label("Avisos", 18, true));
        CheckBox sound = new CheckBox(this);
        sound.setText("Sonido"); sound.setTextColor(text()); sound.setChecked(store.soundEnabled());
        sound.setOnCheckedChangeListener((b, checked) -> store.setSoundEnabled(checked));
        alerts.addView(sound);
        CheckBox vibration = new CheckBox(this);
        vibration.setText("Vibración"); vibration.setTextColor(text()); vibration.setChecked(store.vibrationEnabled());
        vibration.setOnCheckedChangeListener((b, checked) -> store.setVibrationEnabled(checked));
        alerts.addView(vibration);
        alerts.addView(button("Permitir notificaciones", this::requestNotificationPermission), matchWrap(dp(6)));
        alerts.addView(buttonOutline("Permitir alarmas exactas", this::requestExactAlarmPermission), matchWrap(dp(6)));
        LinearLayout soundRow = row();
        soundRow.addView(buttonOutline("Elegir sonido", this::pickSound), weight());
        soundRow.addView(buttonOutline("Probar", this::testSound), weight());
        alerts.addView(soundRow, matchWrap(dp(6)));
        body.addView(alerts, matchWrap(dp(12)));

        LinearLayout colors = card();
        colors.addView(label("Colores", 18, true));
        String[] types = {"CLASS","EXAM","DELIVERY","TASK","REMINDER","ALARM","EVENT"};
        String[] names = {"Clases","Exámenes","Entregas","Tareas","Recordatorios","Alarmas","Eventos"};
        for (int i = 0; i < types.length; i++) {
            final String type = types[i];
            LinearLayout r = row();
            TextView dot = label("●", 26, false);
            dot.setTextColor(store.colorFor(type));
            r.addView(dot);
            TextView name = label(names[i], 16, false);
            r.addView(name, weight());
            r.addView(buttonOutline("Cambiar", () -> showColorDialog(type, () -> setScreen("settings"))));
            colors.addView(r, matchWrap(dp(4)));
        }
        body.addView(colors, matchWrap(dp(12)));
        body.addView(spacer(dp(90)));
        content.addView(scroll, matchMatch());
    }

    private void showTypeChooser() {
        new AlertDialog.Builder(this)
                .setTitle("Agregar")
                .setItems(TYPE_LABELS, (d, which) -> safeAction(() -> showEditor(null, TYPE_CODES[which])))
                .show();
    }

    private void showEditor(PlanItem existing, String initialType) {
        try {
            PlanItem source = existing;
            ScrollView scroll = new ScrollView(this);
            LinearLayout form = vertical(dp(14));
            scroll.addView(form);

            Spinner typeSpinner = new Spinner(this);
            typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TYPE_LABELS));
            String type = source == null ? PlanItem.safeType(initialType) : PlanItem.safeType(source.type);
            int typeIndex = Arrays.asList(TYPE_CODES).indexOf(type);
            if (typeIndex < 0) typeIndex = 4;
            typeSpinner.setSelection(typeIndex);
            form.addView(typeSpinner, matchWrap(dp(4)));

            EditText title = textField("Título", source == null ? "" : source.title, false);
            form.addView(title, matchWrap(dp(6)));
            EditText desc = textField("Descripción / notas", source == null ? "" : source.description, true);
            form.addView(desc, matchWrap(dp(6)));

            final String[] date = {source == null || source.date == null || source.date.isEmpty() ? LocalDate.now().toString() : source.date};
            final String[] time = {source == null || source.time == null || source.time.isEmpty() ? LocalTime.now().withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm")) : source.time};
            Button dateButton = buttonOutline("Fecha · " + date[0], () -> {
                LocalDate parsed;
                try { parsed = LocalDate.parse(date[0]); } catch (Exception e) { parsed = LocalDate.now(); }
                new DatePickerDialog(this, (v,y,m,d) -> {
                    date[0] = LocalDate.of(y,m+1,d).toString();
                    v.post(() -> {});
                }, parsed.getYear(), parsed.getMonthValue()-1, parsed.getDayOfMonth()).show();
            });
            form.addView(dateButton, matchWrap(dp(6)));
            Button timeButton = buttonOutline("Hora · " + time[0], () -> {
                LocalTime parsed;
                try { parsed = LocalTime.parse(time[0]); } catch (Exception e) { parsed = LocalTime.of(9,0); }
                new TimePickerDialog(this, (v,h,m) -> time[0] = String.format(Locale.US, "%02d:%02d", h,m), parsed.getHour(), parsed.getMinute(), true).show();
            });
            form.addView(timeButton, matchWrap(dp(6)));

            CheckBox notify = new CheckBox(this);
            notify.setText("Avisar a la hora"); notify.setTextColor(text());
            notify.setChecked(source != null && source.notify || "REMINDER".equals(type) || "ALARM".equals(type));
            form.addView(notify);

            final int[] chosenColor = {source == null ? store.colorFor(type) : source.color};
            Button colorButton = buttonOutline("Color", () -> showStandaloneColorDialog(chosenColor));
            form.addView(colorButton, matchWrap(dp(6)));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(source == null ? "Nuevo" : "Editar")
                    .setView(scroll)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Guardar", null)
                    .create();
            if (source != null) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Eliminar", (d,w) -> {});
            dialog.setOnShowListener(x -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> safeAction(() -> {
                    String cleanTitle = title.getText().toString().trim();
                    if (cleanTitle.isEmpty()) { toast("Escribe un título"); return; }
                    PlanItem item = source == null ? new PlanItem() : source;
                    if (item.id <= 0) item.id = System.currentTimeMillis();
                    int idx = Math.max(0, typeSpinner.getSelectedItemPosition());
                    item.type = TYPE_CODES[Math.min(TYPE_CODES.length - 1, idx)];
                    item.title = cleanTitle;
                    item.description = desc.getText().toString().trim();
                    item.date = date[0];
                    item.time = time[0];
                    item.color = chosenColor[0];
                    item.notify = notify.isChecked() || "REMINDER".equals(item.type) || "ALARM".equals(item.type);
                    store.upsert(item);
                    AlarmScheduler.cancelItem(this, item.id);
                    if (item.notify) AlarmScheduler.scheduleItem(this, item, store);
                    dialog.dismiss();
                    setScreen(currentScreen);
                }));
                if (source != null) {
                    Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    neutral.setText("Eliminar");
                    neutral.setVisibility(View.VISIBLE);
                    neutral.setOnClickListener(v -> safeAction(() -> {
                        AlarmScheduler.cancelItem(this, source.id);
                        store.delete(source.id);
                        dialog.dismiss();
                        setScreen(currentScreen);
                    }));
                }
            });
            dialog.show();
        } catch (Throwable e) {
            toast("No se pudo abrir el editor");
        }
    }

    private void showColorDialog(String type, Runnable after) {
        final int[] chosen = {store.colorFor(type)};
        AlertDialog dialog = colorDialog(chosen, () -> {
            store.setColor(type, chosen[0]);
            if (after != null) after.run();
        });
        dialog.show();
    }

    private void showStandaloneColorDialog(int[] chosen) {
        colorDialog(chosen, null).show();
    }

    private AlertDialog colorDialog(int[] chosen, Runnable after) {
        LinearLayout body = vertical(dp(12));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (int color : PALETTE) {
            Button swatch = new Button(this);
            swatch.setText("");
            swatch.setBackground(round(color, 18));
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dp(58); p.height = dp(48); p.setMargins(dp(4),dp(4),dp(4),dp(4));
            swatch.setLayoutParams(p);
            swatch.setOnClickListener(v -> chosen[0] = color);
            grid.addView(swatch);
        }
        body.addView(grid);
        EditText hex = textField("#RRGGBB", String.format("#%06X", (0xFFFFFF & chosen[0])), false);
        body.addView(hex, matchWrap(dp(8)));
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Color").setView(body).setNegativeButton("Cancelar", null).setPositiveButton("Aplicar", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> safeAction(() -> {
            String value = hex.getText().toString().trim();
            if (!value.isEmpty()) {
                try {
                    if (!value.startsWith("#")) value = "#" + value;
                    chosen[0] = Color.parseColor(value);
                } catch (Exception ignored) {}
            }
            if (after != null) after.run();
            d.dismiss();
        })));
        return d;
    }

    private LinearLayout itemRow(PlanItem item) {
        LinearLayout box = cardInner();
        TextView title = label(item.title, 16, true);
        title.setTextColor(item.color);
        box.addView(title);
        String meta = typeLabel(item.type) + (item.date == null || item.date.isEmpty() ? "" : " · " + item.date) + (item.time == null || item.time.isEmpty() ? "" : " · " + item.time);
        box.addView(label(meta, 12, false));
        if (item.description != null && !item.description.isEmpty()) {
            TextView d = label(item.description, 13, false); d.setMaxLines(2); box.addView(d);
        }
        box.setOnClickListener(v -> safeAction(() -> showEditor(item, item.type)));
        return box;
    }

    private LinearLayout topBar(String title, boolean add) {
        LinearLayout bar = row();
        TextView t = label(title, 24, true);
        bar.addView(t, weight());
        if (add) bar.addView(button("＋", this::showTypeChooser));
        return bar;
    }

    private void pickSound() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_SOUND);
        } catch (Exception e) { toast("No se pudo abrir el selector de audio"); }
    }

    private void testSound() {
        try {
            Uri uri = null;
            String saved = store.getSoundUri();
            if (saved != null && !saved.isEmpty()) uri = Uri.parse(saved);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ring = RingtoneManager.getRingtone(this, uri);
            if (ring != null) {
                ring.play();
                handler.postDelayed(() -> { try { if (ring.isPlaying()) ring.stop(); } catch (Exception ignored) {} }, 3000L);
            }
        } catch (Exception e) { toast("No se pudo reproducir el sonido"); }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        } else toast("Notificaciones permitidas");
    }

    private void requestExactAlarmPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null && !am.canScheduleExactAlarms()) {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                    return;
                }
            }
            toast("Alarmas permitidas");
        } catch (Exception e) { toast("Revisa el permiso de alarmas en Android"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        if (requestCode == REQ_IMAGE) {
            store.setScheduleImage(uri.toString());
            setScreen("schedule");
        } else if (requestCode == REQ_SOUND) {
            store.setSoundUri(uri.toString());
            toast("Sonido guardado");
        }
    }

    private void loadScheduleBitmap(String uriString, ImageView target) {
        imageExecutor.execute(() -> {
            Bitmap bitmap = decodeSampled(Uri.parse(uriString), 1600, 1000);
            runOnUiThread(() -> {
                if (target == scheduleImageView && bitmap != null) target.setImageBitmap(bitmap);
            });
        });
    }

    private Bitmap decodeSampled(Uri uri, int reqW, int reqH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
            int sample = 1;
            while (bounds.outWidth / sample > reqW * 2 || bounds.outHeight / sample > reqH * 2) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = Math.max(1, sample); opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, opts); }
        } catch (Throwable e) { return null; }
    }

    private void scheduleStudyAlarm(int request, long when, String title, String message) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestNotificationPermission();
        AlarmScheduler.scheduleTimer(this, request, when, title, message, store.getSoundUri());
    }

    private void cancelAlarm(int request) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent i = new Intent(this, AlarmReceiver.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, request, i, android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (am != null && pi != null) { am.cancel(pi); pi.cancel(); }
        } catch (Exception ignored) {}
    }

    private void saveStudyState() {
        getSharedPreferences("planify_clean_v2", MODE_PRIVATE).edit()
                .putInt("pomo_study", pomodoroStudyMin)
                .putInt("pomo_break", pomodoroBreakMin)
                .putInt("pomo_target", pomodoroTarget)
                .putInt("pomo_done", pomodoroCompleted)
                .putBoolean("pomo_phase_break", pomodoroBreak)
                .putBoolean("pomo_running", pomodoroRunning)
                .putLong("pomo_end", pomodoroEndMs)
                .putLong("pomo_paused", pomodoroPausedMs)
                .apply();
    }

    private void restoreStudyState() {
        android.content.SharedPreferences p = getSharedPreferences("planify_clean_v2", MODE_PRIVATE);
        pomodoroStudyMin = clamp(p.getInt("pomo_study", 25), 1, 180);
        pomodoroBreakMin = clamp(p.getInt("pomo_break", 5), 1, 120);
        pomodoroTarget = clamp(p.getInt("pomo_target", 4), 1, 100);
        pomodoroCompleted = clamp(p.getInt("pomo_done", 0), 0, pomodoroTarget);
        pomodoroBreak = p.getBoolean("pomo_phase_break", false);
        pomodoroRunning = p.getBoolean("pomo_running", false);
        pomodoroEndMs = p.getLong("pomo_end", 0L);
        pomodoroPausedMs = p.getLong("pomo_paused", pomodoroStudyMin * 60_000L);
        if (pomodoroRunning && pomodoroEndMs <= 0) pomodoroRunning = false;
    }

    private static final class TimerState {
        final int id;
        int minutes = 10;
        boolean running = false;
        long endMs = 0L;
        long pausedMs = 10L * 60_000L;
        TextView clock;
        TimerState(int id) { this.id = id; }
        void start() { if (pausedMs <= 0) pausedMs = minutes * 60_000L; endMs = System.currentTimeMillis() + pausedMs; running = true; }
        void pause() { pausedMs = Math.max(0L, endMs - System.currentTimeMillis()); endMs = 0L; running = false; }
        void reset() { running = false; endMs = 0L; pausedMs = minutes * 60_000L; }
        long remainingMs() { return running ? Math.max(0L, endMs - System.currentTimeMillis()) : pausedMs; }
    }

    private static final class SimpleItemSelected implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable action;
        SimpleItemSelected(Runnable action) { this.action = action; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { if (action != null) action.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }

    private void safeAction(Runnable r) {
        try { r.run(); }
        catch (Throwable e) { toast("No se pudo completar la acción"); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private boolean dark() { return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    private int bg() { return dark() ? Color.rgb(16,14,19) : Color.rgb(250,250,252); }
    private int surface() { return dark() ? Color.rgb(31,29,34) : Color.WHITE; }
    private int text() { return dark() ? Color.rgb(238,235,242) : Color.rgb(34,32,38); }
    private int muted() { return dark() ? Color.rgb(190,185,198) : Color.rgb(100,96,108); }
    private int primary() { return dark() ? Color.rgb(202,177,255) : Color.rgb(91,63,166); }
    private int primarySoft() { return dark() ? Color.rgb(74,55,103) : Color.rgb(229,220,255); }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }
    private LinearLayout vertical(int padding) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(padding,padding,padding,padding); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout card() { LinearLayout l = vertical(dp(14)); l.setBackground(round(surface(), 16)); return l; }
    private LinearLayout cardInner() { LinearLayout l = vertical(dp(10)); l.setBackground(round(dark() ? 0xFF28252C : 0xFFF3F1F6, 12)); return l; }
    private TextView label(String s, int sp, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextColor(text()); t.setTextSize(sp); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD); return t; }
    private View spacer(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1,h)); return v; }
    private Button button(String s, Runnable action) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(dark()?0xFF231A32:Color.WHITE); b.setBackground(round(primary(), 18)); b.setOnClickListener(v -> safeAction(action)); return b; }
    private Button buttonOutline(String s, Runnable action) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(primary()); GradientDrawable g=round(Color.TRANSPARENT,18); g.setStroke(dp(1), muted()); b.setBackground(g); b.setOnClickListener(v -> safeAction(action)); return b; }
    private EditText numberField(String hint, String value) { EditText e=textField(hint,value,false); e.setInputType(InputType.TYPE_CLASS_NUMBER); return e; }
    private EditText textField(String hint, String value, boolean multiline) { EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(muted()); e.setTextColor(text()); e.setText(value==null?"":value); e.setTextSize(15); e.setSingleLine(!multiline); if(multiline){e.setMinLines(3);e.setGravity(Gravity.TOP);} return e; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private LinearLayout.LayoutParams matchWrap(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin=top; return p; }
    private FrameLayout.LayoutParams matchMatch() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    private int parseInt(EditText e, int fallback) { try { return Integer.parseInt(e.getText().toString().trim()); } catch(Exception x){ return fallback; } }
    private int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private String formatMs(long ms){ long sec=Math.max(0,ms)/1000L; long h=sec/3600, m=(sec%3600)/60, s=sec%60; return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s); }
    private String typeLabel(String type){ int i=Arrays.asList(TYPE_CODES).indexOf(type); return i>=0?TYPE_LABELS[i]:"Evento"; }
}
