package coreUtils.library.views.listeners;

import android.text.Editable;
import android.text.TextWatcher;

public abstract class EditTextListener implements TextWatcher {

    @Override
    public abstract void afterTextChanged(Editable editable);

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int before, int count) {}
}