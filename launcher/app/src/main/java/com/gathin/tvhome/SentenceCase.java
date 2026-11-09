package com.gathin.tvhome;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import java.util.Locale;

/**
 * Fork de Romeo : met une majuscule au premier mot d'une date (« Lundi 9 novembre » au lieu de « lundi »),
 * comme dans son design. Un TextClock réécrit son texte à chaque tick ; on le corrige à chaque fois.
 */
final class SentenceCase implements TextWatcher {
    private final TextView view;
    private boolean fixing;

    private SentenceCase(TextView view) {
        this.view = view;
    }

    static void attach(TextView view) {
        if (view == null) return;
        SentenceCase watcher = new SentenceCase(view);
        view.addTextChangedListener(watcher);
        watcher.fix(view.getText());
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

    @Override
    public void afterTextChanged(Editable s) {
        fix(s);
    }

    private void fix(CharSequence s) {
        if (fixing || s == null || s.length() == 0 || !Character.isLowerCase(s.charAt(0))) return;
        fixing = true;
        String text = s.toString();
        view.setText(text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1));
        fixing = false;
    }
}
