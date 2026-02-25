package com.enerflowlabs.todoplanner.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enerflowlabs.todoplanner.R;
import com.enerflowlabs.todoplanner.adapters.TaskAdapter;
import com.enerflowlabs.todoplanner.database.AppDatabase;
import com.enerflowlabs.todoplanner.models.Task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TasksFragment extends Fragment {

    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private SearchView searchView;
    private Spinner spinnerSort;
    private ImageView btnImport, btnExport, btnFilter;

    private final List<Task> allTasks = new ArrayList<>();
    private String currentQuery = "";
    private int currentSortMode = SORT_LATEST;

    private static final int SORT_LATEST = 0;
    private static final int SORT_OLDEST = 1;
    private static final int SORT_DURATION = 2;
    private static final int SORT_CREATED_ASC = 3;
    private static final int SORT_CREATED_DESC = 4;

    // Type filters
    private boolean filterClockin = true;
    private boolean filterAllDay = true;
    private boolean filterDuration = true;

    // Status filters
    private boolean filterNotStarted = true;
    private boolean filterInProgress = true;
    private boolean filterCompleted = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_tasks);
        searchView = view.findViewById(R.id.search_tasks);
        spinnerSort = view.findViewById(R.id.spinner_sort);
        btnExport = view.findViewById(R.id.img_export);
        btnFilter = view.findViewById(R.id.img_filter);
        btnImport = view.findViewById(R.id.img_import);

        // Always show full search bar with hint
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);
        searchView.clearFocus(); // so keyboard doesn’t pop up immediately

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskAdapter(new ArrayList<Task>());
        recyclerView.setAdapter(adapter);

        btnImport.setOnClickListener(v -> {
            // most compatible for CSV from different file managers:
            csvPicker.launch("text/*");
        });
        btnExport.setOnClickListener(v -> exportTasksToCsv());
        btnFilter.setOnClickListener(v -> showFilterDialog());

        // Set up sort spinner
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.task_sort_options,
                android.R.layout.simple_spinner_item
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sortAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view,
                                       int position,
                                       long id) {
                currentSortMode = position; // 0, 1, 2
                applyFilterAndSort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Search (in-memory, on current list)
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText == null ? "" : newText;
                applyFilterAndSort();
                return true;
            }
        });

        // Initial load
        reloadTasks();
    }

    private final ActivityResultLauncher<String> csvPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                importTasksFromCsv(uri);
            });

    private void importTasksFromCsv(Uri uri) {
        int skipped = 0;

        Context ctx = getContext();
        if (ctx == null) return;

        // Must match exportTasksToCsv() header exactly:
        final List<String> requiredHeader = Arrays.asList(
                "Title", "Description", "Type", "Status", "Date", "From", "To", "Duration"
        );

        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {

            String headerLine = br.readLine();
            if (headerLine == null) {
                Toast.makeText(ctx, getString(R.string.empty_csv), Toast.LENGTH_SHORT).show();
                return;
            }

            // Handle BOM if present
            headerLine = headerLine.replace("\uFEFF", "");

            List<String> headers = parseCsvLine(headerLine);

            if (!headers.equals(requiredHeader)) {
                Toast.makeText(ctx, getString(R.string.csv_invalid_format), Toast.LENGTH_SHORT).show();
                return;
            }

            List<Task> tasksToInsert = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < headers.size()) continue;

                Task t = taskFromExportedCsvRow(cols);
                if (t == null) {
                    // you can detect reason if you return a wrapper; easiest is count generic skip
                    skipped++;
                } else {
                    tasksToInsert.add(t);
                }
            }

            if (tasksToInsert.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.no_tasks_to_import), Toast.LENGTH_SHORT).show();
                return;
            }

            AppDatabase db = AppDatabase.getInstance(ctx);
            db.runInTransaction(() -> {
                for (Task t : tasksToInsert) db.taskDao().insert(t);
            });

            // after insert
            Toast.makeText(ctx,
                    "Imported " + tasksToInsert.size() + " tasks. Skipped " + skipped + " invalid rows.",
                    Toast.LENGTH_LONG).show();

            // Refresh list with your current sort/filter/search
            reloadTasks();          // or applyFilterAndSort() if you prefer
            applyFilterAndSort();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ctx, getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private Task taskFromExportedCsvRow(List<String> c) {
        // Export header order:
        // 0 Title, 1 Description, 2 Type, 3 Status, 4 Date, 5 From, 6 To, 7 Duration

        String title = safe(c, 0);
        if (title.trim().isEmpty()) return null;

        String desc = safe(c, 1);
        String type = safe(c, 2);
        String status = safe(c, 3);
        String dateCol = safe(c, 4);
        String fromCol = safe(c, 5);
        String toCol = safe(c, 6);

        Task t = new Task();
        t.title = title;
        t.description = desc;

        // CSV doesn't include CreatedAt in your export -> set now
        t.createdAt = System.currentTimeMillis();

        // status normalize like you do elsewhere
        if (status == null || status.trim().isEmpty()) status = Task.STATUS_NOT_STARTED;
        t.status = status;

        // Patterns based on export formats
        // - dateOnly: dd.MM.yyyy (dots)
        // - dateTimeFull: dd-MM-yyyy HH:mm:ss (dashes)
        // - timeHms: HH:mm:ss
        SimpleDateFormat dateOnly = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat dateTimeFull = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        SimpleDateFormat timeHms = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        // Type mapping from export:
        // "All-day", "Duration", "Clock-in"
        if ("All-day".equalsIgnoreCase(type)) {
            t.isAllDay = true;
            t.date = dateCol;              // export uses task.date here
            t.fromDate = null;
            t.toDate = null;
            t.isOngoing = false;
            t.startTimestamp = 0;
            t.stopTimestamp = 0;
            t.durationMillis = 0;
            return t;
        }

        if ("Duration".equalsIgnoreCase(type)) {
            t.isAllDay = false;
            t.isOngoing = false;

            // Export sets Date col as: fromDate OR "fromDate - toDate" using dd.MM.yyyy
            if (dateCol.contains(" - ")) {
                String[] parts = dateCol.split("\\s-\\s");
                t.fromDate = parts[0].trim();
                t.toDate = parts.length > 1 ? parts[1].trim() : parts[0].trim();
            } else {
                t.fromDate = dateCol;
                t.toDate = dateCol;
            }

            // Export writes From/To as dd-MM-yyyy HH:mm:ss using start/stop timestamps
            long startTs = parseDateTimeFull(dateTimeFull, fromCol);
            long stopTs = parseDateTimeFull(dateTimeFull, toCol);

            t.startTimestamp = startTs;
            t.stopTimestamp = stopTs;

            if (startTs > 0 && stopTs > startTs) {
                t.durationMillis = stopTs - startTs;
            } else {
                t.durationMillis = 0;
            }
            return t;
        }

        // Default: Clock-in
        t.isAllDay = false;
        t.fromDate = null;
        t.toDate = null;

        // Export for clock-in:
        // - same day stopped: Date = dd.MM.yyyy, From/To = HH:mm:ss
        // - different days stopped: Date = dd.MM.yyyy - dd.MM.yyyy, From/To = dd-MM-yyyy HH:mm:ss
        // - ongoing: Date = dd.MM.yyyy, From = HH:mm:ss, To empty
        long startTs = 0;
        long stopTs = 0;

        boolean fromIsTimeOnly = isTimeOnly(fromCol);
        boolean toIsTimeOnly = isTimeOnly(toCol);

        if (fromIsTimeOnly && !dateCol.isEmpty()) {
            // Combine dateCol (single day) + time
            // Only valid when dateCol is a single dd.MM.yyyy
            try {
                Date d = dateOnly.parse(dateCol.trim());
                if (d != null) {
                    String combinedStart = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(d)
                            + " " + fromCol.trim();

                    // parse using "dd.MM.yyyy HH:mm:ss"
                    SimpleDateFormat dt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
                    startTs = dt.parse(combinedStart).getTime();

                    if (!toCol.trim().isEmpty() && toIsTimeOnly) {
                        String combinedStop = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(d)
                                + " " + toCol.trim();
                        stopTs = dt.parse(combinedStop).getTime();
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            // Different-day case uses full datetime format
            startTs = parseDateTimeFull(dateTimeFull, fromCol);
            stopTs = parseDateTimeFull(dateTimeFull, toCol);
        }

        t.startTimestamp = startTs;
        t.stopTimestamp = stopTs;

        // ✅ Ensure "Started on" is not null (used in Scheduler)
        if (startTs > 0) {
            SimpleDateFormat startedFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
            t.dateTime = startedFmt.format(new Date(startTs));
            // also make sure date is set (for calendar / display)
            SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            t.date = dateFmt.format(new Date(startTs));
        } else {
            // fallback if timestamp parse failed
            if (!dateCol.trim().isEmpty() && !fromCol.trim().isEmpty()) {
                t.date = dateCol.trim();
                t.dateTime = dateCol.trim() + " " + fromCol.trim();
            }
        }

        if (startTs > 0 && stopTs > startTs) {
            t.durationMillis = stopTs - startTs;
            t.isOngoing = false;
            // If someone exported clock-in with empty status, keep completed
            if (t.status == null || t.status.trim().isEmpty()) t.status = Task.STATUS_COMPLETED;
        } else {
            // ongoing clock-in
            t.durationMillis = 0;
            t.isOngoing = true;
            t.status = Task.STATUS_IN_PROGRESS;
        }

        return t;
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // escaped quote inside quotes: ""
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());

        // strip surrounding quotes from each cell (because export uses csvEscape)
        for (int i = 0; i < out.size(); i++) {
            out.set(i, stripQuotes(out.get(i).trim()));
        }

        return out;
    }

    private String stripQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        // restore escaped double quotes
        return s.replace("\"\"", "\"");
    }

    private String safe(List<String> c, int idx) {
        if (idx < 0 || idx >= c.size()) return "";
        return c.get(idx) == null ? "" : c.get(idx);
    }

    private long parseDateTimeFull(SimpleDateFormat fmt, String value) {
        try {
            if (value == null) return 0;
            value = value.trim();
            if (value.isEmpty()) return 0;
            Date d = fmt.parse(value);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isTimeOnly(String s) {
        if (s == null) return false;
        s = s.trim();
        // HH:mm:ss
        return Pattern.matches("\\d{2}:\\d{2}:\\d{2}", s);
    }

    private void showFilterDialog() {
        Context ctx = getContext();
        if (ctx == null) return;

        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_filters, null);

        CheckBox cbClockin = dialogView.findViewById(R.id.cb_clockin);
        CheckBox cbAllDay = dialogView.findViewById(R.id.cb_allday);
        CheckBox cbDuration = dialogView.findViewById(R.id.cb_duration);

        CheckBox cbNotStarted = dialogView.findViewById(R.id.cb_not_started);
        CheckBox cbInProgress = dialogView.findViewById(R.id.cb_in_progress);
        CheckBox cbCompleted = dialogView.findViewById(R.id.cb_completed);
        CheckBox cbSelectAll = dialogView.findViewById(R.id.cb_select_all);

// Put all checkboxes (except SelectAll) into a list
        List<CheckBox> allBoxes = Arrays.asList(
                cbClockin, cbAllDay, cbDuration,
                cbNotStarted, cbInProgress, cbCompleted
        );

// Guard to avoid infinite loops when we programmatically setChecked()
        final boolean[] internalChange = {false};

        // restore previous selections
        cbClockin.setChecked(filterClockin);
        cbAllDay.setChecked(filterAllDay);
        cbDuration.setChecked(filterDuration);

        cbNotStarted.setChecked(filterNotStarted);
        cbInProgress.setChecked(filterInProgress);
        cbCompleted.setChecked(filterCompleted);

// 1) SelectAll -> (check/uncheck) all
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChange[0]) return;

            internalChange[0] = true;
            for (CheckBox cb : allBoxes) {
                cb.setChecked(isChecked);
            }
            internalChange[0] = false;
        });

// 2) Any individual checkbox change -> update SelectAll state
        CompoundButton.OnCheckedChangeListener childListener = (buttonView, isChecked) -> {
            if (internalChange[0]) return;

            boolean allChecked = true;
            for (CheckBox cb : allBoxes) {
                if (!cb.isChecked()) {
                    allChecked = false;
                    break;
                }
            }

            internalChange[0] = true;
            cbSelectAll.setChecked(allChecked);
            internalChange[0] = false;
        };

        for (CheckBox cb : allBoxes) {
            cb.setOnCheckedChangeListener(childListener);
        }

// 3) Initial state: set SelectAll checked if everything is checked
        boolean allCheckedInitially = true;
        for (CheckBox cb : allBoxes) {
            if (!cb.isChecked()) {
                allCheckedInitially = false;
                break;
            }
        }
        internalChange[0] = true;
        cbSelectAll.setChecked(allCheckedInitially);
        internalChange[0] = false;

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .create();

        Button btnApply = dialogView.findViewById(R.id.btn_apply_filter);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_filter);

        btnApply.setOnClickListener(v -> {
            filterClockin = cbClockin.isChecked();
            filterAllDay = cbAllDay.isChecked();
            filterDuration = cbDuration.isChecked();

            filterNotStarted = cbNotStarted.isChecked();
            filterInProgress = cbInProgress.isChecked();
            filterCompleted = cbCompleted.isChecked();

            dialog.dismiss();
            applyFilterAndSort();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void exportTasksToCsv() {
        Context ctx = getContext();
        if (ctx == null) return;

        List<Task> tasks = AppDatabase.getInstance(ctx).taskDao().getTodoTasks();
        // or getAll(), use whatever you currently use

        StringBuilder sb = new StringBuilder();
        // 1) Header: add Status, rename DurationMinutes -> Duration
        sb.append("Title,Description,Type,Status,Date,From,To,Duration\n");

        SimpleDateFormat dateOnly = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        SimpleDateFormat timeHms = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dateTimeFull = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        SimpleDateFormat timeHm = new SimpleDateFormat("HH:mm", Locale.getDefault());

        long now = System.currentTimeMillis();

        for (Task task : tasks) {
            // ---- Type detection ----
            boolean isRepeat = "REPEAT".equals(task.taskType) || "REPEAT_OCCURRENCE".equals(task.taskType);
            boolean isClockIn = !task.isAllDay
                    && task.fromDate == null
                    && task.toDate == null;

            String type;
            if (isRepeat) {
                type = "Repeat";
            } else if (task.isAllDay) {
                type = "All-day";
            } else if (!isClockIn && task.fromDate != null && task.toDate != null) {
                type = "Duration";
            } else {
                type = "Clock-in";
            }

            // ---- Status ----
            String status = task.status;
            if (isClockIn) {
                // for clock-in, status comes from ongoing/completed
                if (task.isOngoing) {
                    status = Task.STATUS_IN_PROGRESS;
                } else {
                    if (status == null || status.trim().isEmpty()) {
                        status = Task.STATUS_COMPLETED;
                    }
                }
            }
            if (status == null || status.trim().isEmpty()) {
                status = Task.STATUS_NOT_STARTED;
            }

            // ---- Date / From / To columns ----
            String dateCol = "";
            String fromCol = "";
            String toCol = "";

            if (isRepeat) {
                // Repeat series: keep Date as range (fromDate - toDate) but show ONLY time in From/To
                if (task.fromDate != null && task.toDate != null) {
                    if (task.fromDate.equals(task.toDate)) {
                        dateCol = task.fromDate;
                    } else {
                        dateCol = task.fromDate + " - " + task.toDate;
                    }
                }
                if (task.startTimestamp > 0) {
                    fromCol = timeHm.format(new Date(task.startTimestamp));
                }
                if (task.stopTimestamp > 0) {
                    toCol = timeHm.format(new Date(task.stopTimestamp));
                }
            } else if (isClockIn) {
                if (task.startTimestamp > 0) {
                    Date start = new Date(task.startTimestamp);

                    if (task.stopTimestamp > 0) {
                        Date stop = new Date(task.stopTimestamp);

                        String startDateStr = dateOnly.format(start);
                        String stopDateStr = dateOnly.format(stop);

                        if (startDateStr.equals(stopDateStr)) {
                            // same day → Date = dd.MM.yyyy; From/To = HH:mm:ss
                            dateCol = startDateStr;
                            fromCol = timeHms.format(start);
                            toCol = timeHms.format(stop);
                        } else {
                            // different days → Date empty; From/To = dd-MM-yyyy HH:mm:ss
                            dateCol = dateOnly.format(start) + " - " + dateOnly.format(stop);
                            fromCol = dateTimeFull.format(start);
                            toCol = dateTimeFull.format(stop);
                        }
                    } else {
                        // ongoing clock-in: show start date + time
                        dateCol = dateOnly.format(start);
                        fromCol = timeHms.format(start);
                        toCol = ""; // not yet stopped
                    }
                }
            } else {
                // Non clock-in tasks (All-day or Duration)
                if (task.isAllDay) {
                    // same as earlier: Date = date, From/To empty
                    dateCol = task.date != null ? task.date : "";
                    fromCol = "";
                    toCol = "";
                } else if (task.fromDate != null && task.toDate != null) {
                    // Duration tasks: show from/to date + times
                    if (task.fromDate.equals(task.toDate)) {
                        dateCol = task.fromDate;
                    } else {
                        dateCol = task.fromDate + " - " + task.toDate;
                    }

                    if (task.startTimestamp > 0) {
                        fromCol = dateTimeFull.format(new Date(task.startTimestamp));
                    }
                    if (task.stopTimestamp > 0) {
                        toCol = dateTimeFull.format(new Date(task.stopTimestamp));
                    }
                }
            }

            // ---- Duration (human readable) ----
            String durationCol = "";

            // Repeat: Duration column should be "<xh ym> <Everyday|Mon/Tue/...>"
            if (isRepeat) {
                String dur = formatDurationHoursMinutes(task.durationMillis);
                if (dur == null) dur = "";

                String freqLabel = "";
                if ("EVERY_DAY".equals(task.repeatRule)) {
                    freqLabel = "Everyday";
                } else if (task.repeatDays != null && !task.repeatDays.trim().isEmpty()) {
                    // stored as MON,TUE,... -> output Mon/Tue/...
                    String[] parts = task.repeatDays.split(",");
                    StringBuilder f = new StringBuilder();
                    for (String p : parts) {
                        String s = p == null ? "" : p.trim();
                        if (s.isEmpty()) continue;
                        String pretty;
                        switch (s) {
                            case "MON": pretty = "Mon"; break;
                            case "TUE": pretty = "Tue"; break;
                            case "WED": pretty = "Wed"; break;
                            case "THU": pretty = "Thu"; break;
                            case "FRI": pretty = "Fri"; break;
                            case "SAT": pretty = "Sat"; break;
                            case "SUN": pretty = "Sun"; break;
                            default: pretty = s;
                        }
                        if (f.length() > 0) f.append("/");
                        f.append(pretty);
                    }
                    freqLabel = f.toString();
                }

                if (!dur.isEmpty() && !freqLabel.isEmpty()) durationCol = dur + " " + freqLabel;
                else if (!dur.isEmpty()) durationCol = dur;
                else durationCol = freqLabel;
            }

// ✅ For CLOCK-IN ongoing, Duration column should be empty
            if (durationCol.isEmpty() && !(isClockIn && task.isOngoing)) {
                long durationMillis = task.durationMillis;
                if (durationMillis > 0) {
                    durationCol = formatDurationHuman(durationMillis);
                }
            }


            // ---- CSV row ----
            sb.append(csvEscape(task.title))
                    .append(',')
                    .append(csvEscape(task.description))
                    .append(',')
                    .append(csvEscape(type))
                    .append(',')
                    .append(csvEscape(status))
                    .append(',')
                    .append(csvEscape(dateCol))
                    .append(',')
                    .append(csvEscape(fromCol))
                    .append(',')
                    .append(csvEscape(toCol))
                    .append(',')
                    .append(csvEscape(durationCol))
                    .append('\n');
        }

        // ---- write file (keep your existing writing code, just use sb.toString()) ----
        try {
            SimpleDateFormat fileFmt = new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault());
            String ts = fileFmt.format(new Date());
            String fileName = "tasks_export_" + ts + ".csv";

            // 1) Write to app cache (temp export folder)
            File exportDir = new File(ctx.getCacheDir(), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File file = new File(exportDir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();

            // 2) Share immediately via FileProvider
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx,
                    ctx.getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ctx.startActivity(Intent.createChooser(shareIntent, "Share tasks CSV"));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ctx, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }

    }

    private String csvEscape(String value) {
        if (value == null) return "\"\"";
        String v = value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private
    /**
     * Repeat tasks should always show duration as hours/minutes (e.g., "4h 30m", "6h"),
     * never as weeks/days and never including seconds.
     */
    String formatDurationHoursMinutes(long millis) {
        if (millis <= 0) return "";
        long totalMinutes = millis / (60L * 1000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h");
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append("m");
        }
        // If < 1 hour and < 1 minute, keep it as 0m (better than blank)
        if (sb.length() == 0) sb.append("0m");
        return sb.toString();
    }

    String formatDurationHuman(long millis) {
        if (millis <= 0) return "";

        long totalSeconds = millis / 1000;

        long weeks = totalSeconds / (7L * 24 * 3600);
        totalSeconds %= 7L * 24 * 3600;

        long days = totalSeconds / (24L * 3600);
        totalSeconds %= 24L * 3600;

        long hours = totalSeconds / 3600;
        totalSeconds %= 3600;

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        // If we have weeks or days → show only weeks & days (as you described)
        List<String> parts = new ArrayList<>();

        if (weeks > 0 || days > 0) {
            if (weeks > 0) {
                parts.add(weeks + " week" + (weeks > 1 ? "s" : ""));
            }
            if (days > 0) {
                parts.add(days + " day" + (days > 1 ? "s" : ""));
            }
            if (parts.isEmpty()) {
                parts.add("0 days");
            }
        } else {
            // No weeks/days → show hours/minutes/seconds
            if (hours > 0) {
                parts.add(hours + "h");
            }
            if (minutes > 0) {
                parts.add(minutes + "m");
            }
            if (seconds > 0 || parts.isEmpty()) {
                parts.add(seconds + "s");
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }


    @Override
    public void onResume() {
        super.onResume();
        // Reload from DB every time we come back to this tab
        reloadTasks();
    }

    private void reloadTasks() {
        allTasks.clear();
        List<Task> latestFromDb = AppDatabase
                .getInstance(getContext())
                .taskDao()
                .getTodoTasks(); // hides repeat occurrences
        if (latestFromDb != null) {
            allTasks.addAll(latestFromDb);
        }
        applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        if (adapter == null) return;

        String queryLower = currentQuery == null
                ? ""
                : currentQuery.toLowerCase(Locale.getDefault());

        // 1) Filter by title (in-memory)
        List<Task> filtered = new ArrayList<>();
        for (Task task : allTasks) {

            if (task.repeatParentId != null) continue; // never show occurrences in Todotab

            // --- Type detection (same logic style as your exportTasksToCsv) ---
            boolean isClockIn = !task.isAllDay && task.fromDate == null && task.toDate == null;
            boolean isAllDay = task.isAllDay;
            boolean isDuration = !task.isAllDay && task.fromDate != null && task.toDate != null;

            boolean typeOk =
                    (isClockIn && filterClockin) ||
                            (isAllDay && filterAllDay) ||
                            (isDuration && filterDuration);

            if (!typeOk) continue;

            // --- Status normalize ---
            String status = task.status;

            if (isClockIn) {
                status = task.isOngoing ? Task.STATUS_IN_PROGRESS : Task.STATUS_COMPLETED;
            }
            if (status == null || status.trim().isEmpty()) status = Task.STATUS_NOT_STARTED;

            boolean statusOk =
                    (Task.STATUS_NOT_STARTED.equals(status) && filterNotStarted) ||
                            (Task.STATUS_IN_PROGRESS.equals(status) && filterInProgress) ||
                            (Task.STATUS_COMPLETED.equals(status) && filterCompleted);

            if (!statusOk) continue;

            // --- Search by title (your existing logic) ---
            String title = task.title == null ? "" : task.title;
            if (queryLower.isEmpty() || title.toLowerCase(Locale.getDefault()).contains(queryLower)) {
                filtered.add(task);
            }
        }


        // 2) Sort
        Collections.sort(filtered, new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                switch (currentSortMode) {
                    case SORT_OLDEST:
                        // Oldest first (smallest startTimestamp)
                        return Long.compare(t1.startTimestamp, t2.startTimestamp);

                    case SORT_DURATION:
                        // Longest duration first
                        int byDuration = Long.compare(t2.durationMillis, t1.durationMillis);
                        if (byDuration != 0) return byDuration;
                        // Tie-breaker: latest first
                        return Long.compare(t2.startTimestamp, t1.startTimestamp);

                    case SORT_CREATED_ASC:
                        // Tie-breaker: latest first
                        return Long.compare(t1.createdAt, t2.createdAt);

                    case SORT_CREATED_DESC:
                        // Tie-breaker: latest first
                        return Long.compare(t2.createdAt, t1.createdAt);


                    case SORT_LATEST:
                    default:
                        // Latest first (largest startTimestamp)
                        return Long.compare(t2.startTimestamp, t1.startTimestamp);
                }
            }
        });

        // 3) Push to adapter
        adapter.updateList(filtered);
    }
}
