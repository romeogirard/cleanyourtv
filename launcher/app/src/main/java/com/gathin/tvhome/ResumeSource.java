package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONObject;

import java.io.InputStream;

/**
 * Fork de Romeo, clé de thème resume_card (true) : la dernière lecture en cours, lue dans « Continuer à regarder »
 * du système (TvContract Watch Next). L'affiche vient de l'app (content://, android.resource://) ou, avec
 * resume_posters (true), de l'adresse https:// que l'app a publiée : seule requête réseau du launcher, une image,
 * gardée en cache. Sinon la carte montre l'icône de l'app.
 * Permission : android.permission.READ_TV_LISTINGS (pm grant, voir docs/watch-next.md). Sans elle, rien ne s'affiche.
 */
final class ResumeSource {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    /** Colonnes nommées : une projection null casse sur certains modèles (split_id), voir docs/watch-next.md. */
    private static final String[] COLUMNS = {"title", "package_name", "watch_next_type", "last_engagement_time_utc_millis",
            "last_playback_position_millis", "duration_millis", "poster_art_uri", "intent_uri"};
    private static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int MAX_POSTER = 640;

    private ResumeSource() { }

    static final class Item {
        final String title;
        final String pkg;
        final String intentUri;
        final long leftMs;
        final float progress;
        final Bitmap poster;

        Item(String title, String pkg, String intentUri, long leftMs, float progress, Bitmap poster) {
            this.title = title;
            this.pkg = pkg;
            this.intentUri = intentUri;
            this.leftMs = leftMs;
            this.progress = progress;
            this.poster = poster;
        }
    }

    static boolean enabled(Context ctx) {
        JSONObject raw = Config.rawTheme(ctx);
        return raw != null && raw.optBoolean("resume_card", false);
    }

    static boolean permitted(Context ctx) {
        return ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** La lecture la plus récente, entamée et pas finie ; null s'il n'y en a pas. À appeler hors du fil principal. */
    static Item read(Context ctx) {
        long now = System.currentTimeMillis();
        String bestTitle = null, bestPkg = null, bestIntent = null, bestPoster = null;
        long bestWhen = 0, bestLeft = 0;
        float bestProgress = 0;
        try (Cursor c = ctx.getContentResolver().query(WATCH_NEXT, COLUMNS, null, null, null)) {
            if (c == null) return null;
            int iTitle = c.getColumnIndex("title"), iPkg = c.getColumnIndex("package_name"),
                    iType = c.getColumnIndex("watch_next_type"), iWhen = c.getColumnIndex("last_engagement_time_utc_millis"),
                    iPos = c.getColumnIndex("last_playback_position_millis"), iDur = c.getColumnIndex("duration_millis"),
                    iPoster = c.getColumnIndex("poster_art_uri"), iIntent = c.getColumnIndex("intent_uri");
            if (iTitle < 0 || iType < 0 || iWhen < 0 || iPos < 0 || iDur < 0 || iIntent < 0) return null;
            while (c.moveToNext()) {
                if (c.getInt(iType) != 0) continue;                       // 0 = continuer ; 1, 2 = suivant, nouveau
                long pos = c.getLong(iPos), dur = c.getLong(iDur), when = c.getLong(iWhen);
                if (dur <= 0 || pos <= 0 || pos * 100 >= dur * 95) continue;   // pas entamé, ou presque fini
                if (when <= 0 || now - when > MAX_AGE_MS) continue;       // trop ancien, ou sans date
                String intent = c.getString(iIntent), title = c.getString(iTitle);
                if (intent == null || intent.isEmpty() || title == null || title.isEmpty()) continue;
                if (when <= bestWhen) continue;
                bestWhen = when;
                bestTitle = title;
                bestIntent = intent;
                bestPkg = iPkg < 0 ? null : c.getString(iPkg);
                bestPoster = iPoster < 0 ? null : c.getString(iPoster);
                bestLeft = dur - pos;
                bestProgress = pos / (float) dur;
            }
        } catch (RuntimeException e) {
            return null;   // permission retirée, fournisseur absent ou firmware qui refuse
        }
        if (bestTitle == null) return null;
        return new Item(bestTitle, bestPkg, bestIntent, bestLeft, bestProgress, poster(ctx, bestPoster));
    }

    private static final int MAX_DOWNLOAD = 3 * 1024 * 1024;

    /** resume_posters : télécharge l'affiche https:// une fois, la garde dans le cache de l'app (sous 30 jours). */
    private static Bitmap remotePoster(Context ctx, String url) {
        JSONObject raw = Config.rawTheme(ctx);
        if (raw == null || !raw.optBoolean("resume_posters", false)) return null;
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "posters");
        java.io.File file = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            java.net.HttpURLConnection c = null;
            java.io.File tmp = new java.io.File(dir, file.getName() + ".part");
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) return null;
                try (InputStream in = c.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n, total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_DOWNLOAD) return null;   // pas une affiche
                        out.write(buf, 0, n);
                    }
                }
                if (!tmp.renameTo(file)) return null;
            } catch (Exception e) {
                return null;
            } finally {
                if (c != null) c.disconnect();
                tmp.delete();
            }
        }
        return decode(file);
    }

    private static Bitmap decode(java.io.File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_POSTER) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap poster(Context ctx, String uriString) {
        if (uriString == null || uriString.isEmpty()) return null;
        Uri uri = Uri.parse(uriString);
        String scheme = uri.getScheme();
        if ("https".equals(scheme)) return remotePoster(ctx, uriString);
        if (!ContentResolver.SCHEME_CONTENT.equals(scheme) && !ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= MAX_POSTER) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
