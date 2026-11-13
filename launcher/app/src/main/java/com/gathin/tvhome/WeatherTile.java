package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fork de Romeo, clé de thème weather_tile : la carte Météo dessine son nom et la météo du moment dans la
 * tuile (« Météo » / « 4°, nuageux »), comme les tuiles HDMI. La météo vient du module Météo
 * (content://io.github.sumyabazar1.weather.now) ; le launcher lui-même ne va jamais en ligne.
 * Clés : {"package": "...", "title": "Météo", "color": "#3E7FA8"}.
 */
final class WeatherTile {
    static final String DEFAULT_PACKAGE = "io.github.sumyabazar1.weather";
    private static final Uri NOW = Uri.parse("content://io.github.sumyabazar1.weather.now");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** La dernière légende lue, gardée d'un rechargement de la grille à l'autre. */
    private static String lastCaption = "";

    final String pkg;
    final String title;
    final int color;

    private WeatherTile(String pkg, String title, int color) {
        this.pkg = pkg;
        this.title = title;
        this.color = color;
    }

    /** Null quand le thème n'a pas de weather_tile. */
    static WeatherTile fromTheme(Context ctx) {
        JSONObject raw = Config.rawTheme(ctx);
        JSONObject w = raw == null ? null : raw.optJSONObject("weather_tile");
        if (w == null) return null;
        int color = 0xFF3E7FA8;
        try {
            color = android.graphics.Color.parseColor(w.optString("color", "#3E7FA8"));
        } catch (IllegalArgumentException ignored) {
        }
        return new WeatherTile(w.optString("package", DEFAULT_PACKAGE), w.optString("title", "Météo"), color);
    }

    Banner banner(Context ctx) {
        Banner b = new Banner(ctx, title, color);
        b.setCaption(lastCaption);
        refresh(ctx, b);
        return b;
    }

    /** Lit la météo hors du fil principal et met la légende à jour quand elle arrive. */
    private static void refresh(Context ctx, Banner banner) {
        captionAsync(ctx, text -> {
            if (text == null) return;
            lastCaption = text;
            banner.setCaption(text);
        });
    }

    /** « 4°, nuageux » : null si le module est absent, sans ville, ou si le firmware bloque l'accès. */
    static String queryCaption(Context app) {
        try (Cursor c = app.getContentResolver().query(NOW, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int temp = c.getInt(c.getColumnIndexOrThrow("temp"));
                String sky = c.getString(c.getColumnIndexOrThrow("sky"));
                return temp + "°" + (sky == null || sky.isEmpty() ? "" : ", " + sky.toLowerCase(Locale.getDefault()));
            }
        } catch (RuntimeException ignored) {
            // Module absent, pas encore de ville, ou le firmware TCL qui bloque l'accès : on garde l'affichage actuel.
        }
        return null;
    }

    /** Lit la légende hors du fil principal ; le rappel s'exécute sur le fil principal, avec null en cas d'échec. */
    static void captionAsync(Context ctx, java.util.function.Consumer<String> done) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            String caption = queryCaption(app);
            MAIN.post(() -> done.accept(caption));
        });
    }

    /** La tuile : fond uni, titre et légende centrés en Geist, à l'échelle de la tuile. */
    static final class Banner extends Drawable {
        private final Paint fill = new Paint();
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String title;
        private String caption = "";

        Banner(Context ctx, String title, int color) {
            this.title = title;
            fill.setColor(color);
            titlePaint.setColor(0xFFFFFFFF);
            titlePaint.setTypeface(font(ctx, R.font.geist_medium));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            captionPaint.setColor(0xA6FFFFFF);
            captionPaint.setTypeface(font(ctx, R.font.geist_regular));
            captionPaint.setTextAlign(Paint.Align.CENTER);
        }

        private static Typeface font(Context ctx, int id) {
            try {
                return ctx.getResources().getFont(id);
            } catch (RuntimeException e) {
                return Typeface.DEFAULT;
            }
        }

        void setCaption(String text) {
            caption = text == null ? "" : text;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect r = getBounds();
            float h = r.height(), cx = r.exactCenterX();
            canvas.drawRect(r, fill);
            titlePaint.setTextSize(0.19f * h);
            captionPaint.setTextSize(0.14f * h);
            Paint.FontMetrics t = titlePaint.getFontMetrics(), c = captionPaint.getFontMetrics();
            float titleCap = -t.ascent * 0.72f, captionCap = -c.ascent * 0.72f, gap = 0.08f * h;
            float block = caption.isEmpty() ? titleCap : titleCap + gap + captionCap;
            float top = r.exactCenterY() - block / 2f;
            canvas.drawText(title, cx, top + titleCap, titlePaint);
            if (!caption.isEmpty()) canvas.drawText(caption, cx, top + titleCap + gap + captionCap, captionPaint);
        }

        @Override public int getIntrinsicWidth() { return 640; }
        @Override public int getIntrinsicHeight() { return 360; }
        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); titlePaint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
