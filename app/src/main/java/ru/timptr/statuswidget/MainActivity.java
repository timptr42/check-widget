package ru.timptr.statuswidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout list;
    private TextView summary;
    private TextView error;
    private ProgressBar progress;
    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.status_list);
        summary = findViewById(R.id.summary_text);
        error = findViewById(R.id.error_text);
        progress = findViewById(R.id.progress);
        refreshButton = findViewById(R.id.refresh_button);

        refreshButton.setOnClickListener(view -> refresh());
        StatusScheduler.schedule(this);
        render(StatusRepository.getCached(this));
        refresh();
    }

    private void refresh() {
        setLoading(true);
        error.setVisibility(View.GONE);
        executor.execute(() -> {
            StatusRepository.StatusResult result;
            try {
                result = StatusRepository.refresh(this);
            } catch (Exception exception) {
                StatusRepository.StatusResult cached = StatusRepository.getCached(this);
                result = new StatusRepository.StatusResult(
                        cached.items,
                        cached.fetchedAt,
                        true,
                        exception.getMessage()
                );
            }

            StatusRepository.StatusResult finalResult = result;
            runOnUiThread(() -> {
                setLoading(false);
                render(finalResult);
                if (finalResult.fromCache && finalResult.message != null) {
                    error.setText(getString(R.string.refresh_error, finalResult.message));
                    error.setVisibility(View.VISIBLE);
                }
                updateWidgets();
            });
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
    }

    private void render(StatusRepository.StatusResult result) {
        list.removeAllViews();
        if (!result.hasData()) {
            summary.setText(result.message == null ? getString(R.string.no_statuses) : result.message);
            return;
        }

        for (StatusItem item : result.items) {
            list.addView(createStatusRow(item));
        }
        String prefix = result.fromCache ? getString(R.string.cache_prefix) : getString(R.string.updated_prefix);
        summary.setText(getString(
                R.string.status_summary,
                result.items.size(),
                prefix,
                StatusRepository.formatEpoch(this, result.fetchedAt)
        ));
    }

    private View createStatusRow(StatusItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        int dotSize = getResources().getDimensionPixelSize(R.dimen.status_dot_size);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.setMargins(0, 0, 14, 0);
        dot.setLayoutParams(dotParams);
        dot.setBackgroundResource(item.dotDrawable());
        row.addView(dot);

        TextView text = new TextView(this);
        text.setTextColor(getColor(R.color.text_primary));
        text.setTextSize(16);
        text.setText(TextUtils.concat(
                item.title(), "\n",
                getString(R.string.status_details, item.status, StatusRepository.formatIso(this, item.updatedAt))));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void updateWidgets() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        StatusWidgetProvider.updateAllWidgets(this, StatusRepository.getCached(this));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
