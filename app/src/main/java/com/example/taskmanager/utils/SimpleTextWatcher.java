package com.example.taskmanager.utils;

import android.text.Editable;
import android.text.TextWatcher;

public abstract class SimpleTextWatcher implements TextWatcher {

    private final Runnable onTextChangedCallback;

    public SimpleTextWatcher(Runnable callback) {
        this.onTextChangedCallback = callback;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        onTextChangedCallback.run();
    }

    @Override
    public void afterTextChanged(Editable s) { }
}
