package coreUtils.library.views.listeners;

import android.widget.SeekBar;

public abstract class SeekBarListener implements SeekBar.OnSeekBarChangeListener {

    public abstract void onProgressChange(SeekBar seekBar, int progress, boolean fromUser);

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        onProgressChange(seekBar, progress, fromUser);
    }
}