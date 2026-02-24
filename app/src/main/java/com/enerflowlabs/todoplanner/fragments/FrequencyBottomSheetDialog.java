package com.enerflowlabs.todoplanner.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.enerflowlabs.todoplanner.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Arrays;

/**
 * Bottom sheet for selecting repeat frequency:
 * - Every day
 * - Custom days (Mon-Sun multi-select)
 */
public class FrequencyBottomSheetDialog extends BottomSheetDialogFragment {

    public interface Callback {
        void onFrequencySelected(boolean everyDay, boolean[] daysSelected);
    }

    private static final String ARG_EVERY_DAY = "arg_every_day";
    private static final String ARG_DAYS = "arg_days";

    private Callback callback;

    public static FrequencyBottomSheetDialog newInstance(boolean everyDay, boolean[] daysSelected) {
        FrequencyBottomSheetDialog dialog = new FrequencyBottomSheetDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_EVERY_DAY, everyDay);
        args.putBooleanArray(ARG_DAYS, daysSelected);
        dialog.setArguments(args);
        return dialog;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_frequency, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RadioGroup rg = view.findViewById(R.id.rg_frequency);
        RadioButton rbEvery = view.findViewById(R.id.rb_every_day);
        RadioButton rbCustom = view.findViewById(R.id.rb_custom_days);
        LinearLayout layoutDays = view.findViewById(R.id.layout_days);

        CheckBox cbMon = view.findViewById(R.id.cb_mon);
        CheckBox cbTue = view.findViewById(R.id.cb_tue);
        CheckBox cbWed = view.findViewById(R.id.cb_wed);
        CheckBox cbThu = view.findViewById(R.id.cb_thu);
        CheckBox cbFri = view.findViewById(R.id.cb_fri);
        CheckBox cbSat = view.findViewById(R.id.cb_sat);
        CheckBox cbSun = view.findViewById(R.id.cb_sun);

        Button btnDone = view.findViewById(R.id.btn_done);

        boolean everyDay = true;
        boolean[] days = new boolean[]{true,true,true,true,true,true,true};

        if (getArguments() != null) {
            everyDay = getArguments().getBoolean(ARG_EVERY_DAY, true);
            boolean[] argDays = getArguments().getBooleanArray(ARG_DAYS);
            if (argDays != null && argDays.length == 7) {
                days = Arrays.copyOf(argDays, 7);
            }
        }

        if (everyDay) {
            rbEvery.setChecked(true);
            layoutDays.setVisibility(View.GONE);
        } else {
            rbCustom.setChecked(true);
            layoutDays.setVisibility(View.VISIBLE);
        }

        cbMon.setChecked(days[0]);
        cbTue.setChecked(days[1]);
        cbWed.setChecked(days[2]);
        cbThu.setChecked(days[3]);
        cbFri.setChecked(days[4]);
        cbSat.setChecked(days[5]);
        cbSun.setChecked(days[6]);

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_custom_days) {
                layoutDays.setVisibility(View.VISIBLE);
            } else {
                layoutDays.setVisibility(View.GONE);
            }
        });

        btnDone.setOnClickListener(v -> {
            boolean isEveryDay = rbEvery.isChecked();
            boolean[] selected = new boolean[7];
            if (isEveryDay) {
                Arrays.fill(selected, true);
            } else {
                selected[0] = cbMon.isChecked();
                selected[1] = cbTue.isChecked();
                selected[2] = cbWed.isChecked();
                selected[3] = cbThu.isChecked();
                selected[4] = cbFri.isChecked();
                selected[5] = cbSat.isChecked();
                selected[6] = cbSun.isChecked();
            }

            if (callback != null) {
                callback.onFrequencySelected(isEveryDay, selected);
            }
            dismiss();
        });
    }
}
