package com.gathin.tvhome;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Fork de Romeo, clé de thème clock_weather (true) : la température et le ciel à droite de la date, après un
 * filet comme celui qui suit l'heure (« 20:50 | Vendredi 6 novembre | 4° Nuageux »). La météo vient du module Météo
 * (WeatherTile.queryCaption) ; sans module ni ville choisie, rien ne s'affiche.
 */
final class ClockWeather {
    private final TextView text;
    private final View divider;

    private ClockWeather(TextView text, View divider) {
        this.text = text;
        this.divider = divider;
    }

    /** Null quand le thème n'active pas clock_weather ou que l'en-tête n'a pas la forme attendue. */
    static ClockWeather attach(Activity a) {
        JSONObject raw = Config.rawTheme(a);
        if (raw == null || !raw.optBoolean("clock_weather", false)) return null;
        TextView date = a.findViewById(R.id.date);
        View clockDivider = a.findViewById(R.id.clockDivider);
        if (date == null || clockDivider == null || !(date.getParent() instanceof View)
                || !(((View) date.getParent()).getParent() instanceof LinearLayout)) return null;
        View dateColumn = (View) date.getParent();
        LinearLayout row = (LinearLayout) dateColumn.getParent();

        // La colonne de la date prenait toute la place restante : elle se réduit à son texte pour laisser la météo.
        LinearLayout.LayoutParams colLp = (LinearLayout.LayoutParams) dateColumn.getLayoutParams();
        colLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        colLp.weight = 0f;
        dateColumn.setLayoutParams(colLp);
        ViewGroup.LayoutParams dateLp = date.getLayoutParams();
        dateLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        date.setLayoutParams(dateLp);

        View divider = new View(a);
        divider.setBackground(clockDivider.getBackground());
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) clockDivider.getLayoutParams());
        divider.setLayoutParams(divLp);
        divider.setVisibility(View.GONE);

        TextView text = new TextView(a);
        text.setSingleLine(true);
        text.setIncludeFontPadding(false);
        text.setTypeface(date.getTypeface());
        text.setTextColor(date.getTextColors());
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, date.getTextSize());
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(colLp);
        textLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        textLp.weight = 0f;
        text.setLayoutParams(textLp);
        text.setVisibility(View.GONE);

        int at = row.indexOfChild(dateColumn) + 1;
        row.addView(divider, at);
        row.addView(text, at + 1);
        return new ClockWeather(text, divider);
    }

    /** À rappeler au retour sur l'accueil : lit la météo hors du fil principal, puis met le texte à jour. */
    void refresh(Activity a) {
        WeatherTile.captionAsync(a, caption -> {
            boolean has = caption != null && !caption.isEmpty();
            text.setText(has ? caption.replace(", ", "  ") : "");
            text.setVisibility(has ? View.VISIBLE : View.GONE);
            divider.setVisibility(has ? View.VISIBLE : View.GONE);
        });
    }
}
