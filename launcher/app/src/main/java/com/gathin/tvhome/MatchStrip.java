package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Fork de Romeo, clé de thème match_layout "days" (maquette Matchs 7) : à la place du panneau qui tourne, tous les
 * matchs à venir en étiquettes sur une ligne au-dessus du dock, groupés sous « CE SOIR », « DEMAIN », « SAMEDI »…
 * La ligne s'arrête avant la pastille Reprendre : une étiquette qui ne tient pas n'est pas affichée.
 */
final class MatchStrip {
    private static final int LIVE_RED = 0xFFFF3B2F;
    private final Context ctx;
    private final float density;
    final LinearLayout view;

    MatchStrip(Context ctx, FrameLayout root, float density) {
        this.ctx = ctx;
        this.density = density;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.BOTTOM);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        lp.leftMargin = dp(60);
        lp.bottomMargin = dp(150);
        root.addView(view, lp);
    }

    static boolean enabled(Context ctx) {
        org.json.JSONObject raw = Config.rawTheme(ctx);
        return raw != null && "days".equals(raw.optString("match_layout", ""));
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /**
     * Refait la ligne. {@code maxWidth} : la place jusqu'à la pastille Reprendre (ou le bord droit), en pixels.
     * {@code f1} : le week-end F1 du thème, ou null.
     */
    void render(List<GameDay.Turn> turns, RaceWeekend.State f1, long now, int maxWidth, boolean is24) {
        view.removeAllViews();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        String lastDay = null;
        int used = 0;
        for (GameDay.Turn t : turns) {
            boolean live = GameDay.live(t, now);
            long start = t.startMs;
            String title;
            int colour;
            if (t.f1) {
                if (f1 == null) continue;
                live = f1.live != null;
                title = "F1 · " + f1.race.shortName();
                colour = 0xFFFF3B2F;
                if (!live && f1.next != null) start = f1.next.start.toEpochMilli();
            } else {
                title = matchup(t.topLine);
                colour = t.colour;
            }
            ZonedDateTime when = Instant.ofEpochMilli(live ? now : start).atZone(zone);
            String day = dayLabel(when, today);
            String time = live ? ctx.getString(R.string.game_live) : time(when, is24);
            LinearLayout column = column(day.equals(lastDay) ? null : day, chip(title, time, colour, live));
            column.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int w = column.getMeasuredWidth() + (view.getChildCount() > 0 ? dp(6) : 0);
            if (used + w > maxWidth) break;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (view.getChildCount() > 0) lp.leftMargin = dp(6);
            view.addView(column, lp);
            used += w;
            lastDay = day;
        }
        view.setVisibility(view.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /** "Lyon at Marseille" (le format de Game Day) devient « Marseille – Lyon », l'équipe à domicile d'abord. */
    static String matchup(String topLine) {
        if (topLine == null) return "";
        int at = topLine.indexOf(" at ");
        if (at <= 0 || topLine.indexOf(" at ", at + 4) >= 0) return topLine;
        return topLine.substring(at + 4).trim() + " – " + topLine.substring(0, at).trim();
    }

    private String dayLabel(ZonedDateTime when, LocalDate today) {
        LocalDate d = when.toLocalDate();
        if (d.equals(today)) return ctx.getString(when.getHour() >= 17 ? R.string.match_tonight : R.string.match_today);
        if (d.equals(today.plusDays(1))) return ctx.getString(R.string.match_tomorrow);
        return when.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()));
    }

    private static String time(ZonedDateTime when, boolean is24) {
        if ("fr".equals(Locale.getDefault().getLanguage()) && is24) {
            return when.getMinute() == 0 ? when.getHour() + " h" : String.format(Locale.FRENCH, "%d h %02d", when.getHour(), when.getMinute());
        }
        return when.format(DateTimeFormatter.ofPattern(is24 ? "HH:mm" : "h:mm a", Locale.getDefault()));
    }

    private LinearLayout column(String label, View chip) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(ctx);
        l.setText(label == null ? " " : label);
        l.setAllCaps(true);
        l.setSingleLine(true);
        l.setTextColor(0xB3FFFFFF);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        l.setLetterSpacing(0.14f);
        l.setTypeface(UiFont.get(ctx, R.string.font_medium));
        l.setIncludeFontPadding(false);
        if (label == null) l.setVisibility(View.INVISIBLE);   // garde la même hauteur, les étiquettes restent alignées
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        col.addView(l, lp);
        col.addView(chip);
        return col;
    }

    private View chip(String title, String time, int colour, boolean live) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setMinimumHeight(dp(26));
        c.setPadding(dp(9), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(0x6B1C1B1A);
        bg.setStroke(Math.max(1, dp(0.5f)), 0x24FFFFFF);
        c.setBackground(bg);
        View dot = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(live ? LIVE_RED : colour);
        dot.setBackground(d);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(5), dp(5));
        dl.rightMargin = dp(6);
        c.addView(dot, dl);
        TextView t = text(title, 0xFFFFFFFF, true);
        t.setMaxWidth(dp(180));
        t.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(t);
        TextView w = text(time, live ? LIVE_RED : 0xA6FFFFFF, false);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.leftMargin = dp(6);
        c.addView(w, wl);
        return c;
    }

    private TextView text(String s, int color, boolean medium) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setSingleLine(true);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(medium ? UiFont.get(ctx, R.string.font_medium) : UiFont.get(ctx, R.string.font_regular));
        t.setIncludeFontPadding(false);
        return t;
    }
}
