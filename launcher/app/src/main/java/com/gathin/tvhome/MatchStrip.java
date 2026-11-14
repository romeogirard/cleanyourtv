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
 * Trois pages qui tournent toutes les {@value #PAGE_SECONDS} secondes : aujourd'hui, demain, puis le reste de la
 * semaine (7 jours). Une période vide n'a pas de page ; une période qui ne tient pas avant la pastille Reprendre
 * continue sur une page de plus. Un point par page.
 */
final class MatchStrip {
    private static final int LIVE_RED = 0xFFFF3B2F;
    static final int PAGE_SECONDS = 10;
    private final Context ctx;
    private final float density;
    final LinearLayout view;
    private final LinearLayout row;
    private final LinearLayout dots;

    MatchStrip(Context ctx, FrameLayout root, float density) {
        this.ctx = ctx;
        this.density = density;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        // WRAP_CONTENT explicite : par défaut une colonne verticale étire la ligne puis la ramène à la largeur des
        // points (forceUniformWidth), ce qui écrasait les étiquettes.
        view.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dots = new LinearLayout(ctx);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(9);
        view.addView(dots, dlp);
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

    private static final class Item {
        final String title, time, day;
        final int colour;
        final boolean live;
        /** 0 aujourd'hui, 1 demain, 2 le reste de la semaine. */
        final int period;

        Item(String title, String time, String day, int colour, boolean live, int period) {
            this.title = title;
            this.time = time;
            this.day = day;
            this.colour = colour;
            this.live = live;
            this.period = period;
        }
    }

    /**
     * Refait la ligne et renvoie le nombre de pages. {@code maxWidth} : la place jusqu'à la pastille Reprendre (ou le
     * bord droit), en pixels. {@code f1} : le week-end F1 du thème, ou null. {@code sinceMs} choisit la page.
     */
    int render(List<GameDay.Turn> turns, RaceWeekend.State f1, long now, int maxWidth, boolean is24, long sinceMs) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        java.util.ArrayList<Item> items = new java.util.ArrayList<>();
        for (GameDay.Turn t : turns) {
            boolean live = GameDay.live(t, now);
            long start = t.startMs;
            String title;
            int colour;
            if (t.f1) {
                if (f1 == null) continue;
                live = f1.live != null;
                title = "F1 · " + f1.race.shortName();
                colour = LIVE_RED;
                if (!live && f1.next != null) start = f1.next.start.toEpochMilli();
            } else {
                title = matchup(t.topLine);
                colour = t.colour;
            }
            ZonedDateTime when = Instant.ofEpochMilli(live ? now : start).atZone(zone);
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, when.toLocalDate());
            if (days > 7) continue;                       // au-delà de la semaine : pas affiché
            int period = days <= 0 ? 0 : days == 1 ? 1 : 2;
            items.add(new Item(title, live ? ctx.getString(R.string.game_live) : time(when, is24),
                    dayLabel(when, today), colour, live, period));
        }
        // Pages : autant d'étiquettes que la place le permet ; chaque page redonne le jour de sa première étiquette.
        java.util.ArrayList<java.util.ArrayList<View>> pages = new java.util.ArrayList<>();
        java.util.ArrayList<View> page = new java.util.ArrayList<>();
        int used = 0;
        String lastDay = null;
        int lastPeriod = -1;
        for (Item it : items) {
            View col = column(it.day.equals(lastDay) ? null : it.day, chip(it.title, it.time, it.colour, it.live));
            col.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int w = col.getMeasuredWidth() + (page.isEmpty() ? 0 : dp(6));
            // Nouvelle période (demain, la semaine) : nouvelle page ; sinon, nouvelle page seulement si ça déborde.
            if (!page.isEmpty() && (it.period != lastPeriod || used + w > maxWidth)) {
                pages.add(page);
                page = new java.util.ArrayList<>();
                col = column(it.day, chip(it.title, it.time, it.colour, it.live));
                col.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                w = col.getMeasuredWidth();
                used = 0;
            }
            if (page.isEmpty() && w > maxWidth) continue;   // une seule étiquette plus large que la place
            page.add(col);
            used += w;
            lastDay = it.day;
            lastPeriod = it.period;
        }
        if (!page.isEmpty()) pages.add(page);

        row.removeAllViews();
        dots.removeAllViews();
        if (pages.isEmpty()) {
            view.setVisibility(View.GONE);
            return 0;
        }
        int on = (int) ((Math.max(0, sinceMs) / (PAGE_SECONDS * 1000L)) % pages.size());
        for (View col : pages.get(on)) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row.getChildCount() > 0) lp.leftMargin = dp(6);
            row.addView(col, lp);
        }
        if (pages.size() > 1) {
            int size = dp(5);
            for (int k = 0; k < pages.size(); k++) {
                View dot = new View(ctx);
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(0xFFFFFFFF);
                dot.setBackground(d);
                dot.setAlpha(k == on ? 0.9f : 0.3f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.rightMargin = size;
                dots.addView(dot, lp);
            }
            dots.setVisibility(View.VISIBLE);
        } else {
            dots.setVisibility(View.GONE);
        }
        view.setVisibility(View.VISIBLE);
        return pages.size();
    }

    /** "Lyon at Marseille" (le format de Game Day) devient « Marseille – Lyon », l'équipe à domicile d'abord. */
    static String matchup(String topLine) {
        if (topLine == null) return "";
        topLine = noEmoji(topLine);
        int at = topLine.indexOf(" at ");
        if (at <= 0 || topLine.indexOf(" at ", at + 4) >= 0) return topLine;
        return topLine.substring(at + 4).trim() + " – " + topLine.substring(0, at).trim();
    }

    /**
     * Retire les emojis et drapeaux où qu'ils soient : les calendriers en mettent de chaque côté (« 🇫🇷 France – Turquie
     * 🇹🇷 ») et Game Day n'enlève que celui de tête, d'où un seul drapeau sur deux.
     */
    static String noEmoji(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean emoji = Character.getType(cp) == Character.OTHER_SYMBOL || Character.getType(cp) == Character.SURROGATE
                    || (cp >= 0x1F1E6 && cp <= 0x1F1FF)        // lettres des drapeaux
                    || (cp >= 0x1F3FB && cp <= 0x1F3FF)        // tons de peau
                    || cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || (cp >= 0xE0020 && cp <= 0xE007F);
            if (!emoji) b.appendCodePoint(cp);
        }
        return b.toString().replaceAll("\\s{2,}", " ").trim();
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
        l.setSingleLine(true);
        l.setText(label == null ? " " : label.toUpperCase(Locale.getDefault()));
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
        col.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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
