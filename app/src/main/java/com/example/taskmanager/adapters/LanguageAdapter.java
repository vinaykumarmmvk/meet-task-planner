package com.example.taskmanager.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.VH> {

    public static class LangItem {
        public final String code;
        public final String name;
        public LangItem(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    private final List<LangItem> all;
    private final List<LangItem> shown;
    private String selectedCode;

    public LanguageAdapter(List<LangItem> items, String currentCode) {
        all = new ArrayList<>(items);
        shown = new ArrayList<>(items);
        // Ensure we always have a valid selection
        if (currentCode != null && !currentCode.trim().isEmpty()) {
            selectedCode = currentCode;
        } else if (!items.isEmpty()) {
            selectedCode = items.get(0).code;
        } else {
            selectedCode = null;
        }
    }

    public String getSelectedCode() { return selectedCode; }

    public void filter(String q) {
        String s = (q == null) ? "" : q.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        if (s.isEmpty()) {
            shown.addAll(all);
        } else {
            for (LangItem li : all) {
                if (li.name.toLowerCase(Locale.getDefault()).contains(s)) {
                    shown.add(li);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_language, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LangItem item = shown.get(position);
        boolean sel = item.code.equals(selectedCode);

        // Prevent old listeners from firing during recycle
        h.checkBox.setOnCheckedChangeListener(null);
        h.checkBox.setText(item.name);
        h.checkBox.setChecked(sel);

        View.OnClickListener selectClick = v -> {
            // Single-select behavior (checkbox UI, radio-like logic)
            selectedCode = item.code;
            notifyDataSetChanged();
        };

        h.itemView.setOnClickListener(selectClick);
        h.checkBox.setOnClickListener(selectClick);
        h.checkBox.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (isChecked) {
                selectedCode = item.code;
                notifyDataSetChanged();
            } else {
                // Keep at least one selected; revert uncheck
                if (item.code.equals(selectedCode)) {
                    buttonView.setChecked(true);
                }
            }
        });
    }

    @Override
    public int getItemCount() { return shown.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCheckBox checkBox;
        VH(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.cb_language);
        }
    }
}
