package com.example.taskmanager.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmanager.R;

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
        selectedCode = currentCode;
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
        h.text.setText(sel ? ("✓ " + item.name) : item.name);

        h.itemView.setOnClickListener(v -> {
            selectedCode = item.code;
            notifyDataSetChanged();
        });
    }

    @Override
    public int getItemCount() { return shown.size(); }

    static class VH extends RecyclerView.ViewHolder {
        EditText text;
        VH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.edit_search_language);
        }
    }
}
