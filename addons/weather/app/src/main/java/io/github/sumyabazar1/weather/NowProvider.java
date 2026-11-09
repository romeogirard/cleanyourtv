package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Ajout de Romeo : la météo du moment pour la tuile du launcher (« 4°, nuageux »), en lecture seule.
 * content://io.github.sumyabazar1.weather.now : une ligne (temp, unit, sky, place), ou aucune tant qu'aucune
 * ville n'est choisie. Une lecture de plus de 30 minutes est rafraîchie sur Open-Meteo au moment de la requête
 * (le launcher appelle hors du fil principal) ; un échec garde la dernière lecture. Pas de service en fond.
 */
public final class NowProvider extends ContentProvider {
    static final String PREFS = "now";
    private static final String[] COLUMNS = {"temp", "unit", "sky", "place"};

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Garde la lecture que la page vient d'afficher, pour que la tuile la reprenne sans refaire d'appel. */
    static void store(Context ctx, Forecast.Report r) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat("tempC", (float) r.tempC)
                .putInt("code", r.code)
                .putBoolean("day", r.day)
                .putLong("at", System.currentTimeMillis())
                .apply();
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        Context ctx = getContext();
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (ctx == null) return out;
        SharedPreferences place = ctx.getSharedPreferences("place", Context.MODE_PRIVATE);
        if (!place.contains("name")) return out;
        SharedPreferences now = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = now.getLong("at", 0);
        if (System.currentTimeMillis() - at > Forecast.REFRESH_MS) {
            try {
                double lat = Double.longBitsToDouble(place.getLong("lat", 0));
                double lon = Double.longBitsToDouble(place.getLong("lon", 0));
                store(ctx, Forecast.fetch(lat, lon, Forecast.MIN_DAYS));
            } catch (Exception ignored) {
                // Hors ligne : la dernière lecture reste, s'il y en a une.
            }
        }
        if (!now.contains("at")) return out;
        SharedPreferences options = ctx.getSharedPreferences("options", Context.MODE_PRIVATE);
        boolean fahrenheit = options.getBoolean("fahrenheit",
                "f".equalsIgnoreCase(ctx.getString(R.string.temperature_unit).trim()));
        int temp = Forecast.degrees(now.getFloat("tempC", 0f), fahrenheit);
        String sky = ctx.getString(Forecast.sky(now.getInt("code", 3), now.getBoolean("day", true)));
        out.addRow(new Object[]{temp, fahrenheit ? "F" : "C", sky, place.getString("name", "")});
        return out;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.weather.now"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
