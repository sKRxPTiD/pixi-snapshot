package org.nullprotocol.pixisnapshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.app.WallpaperManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_NOVA = 1, PICK_WALLPAPER = 2, PICK_SCREENSHOTS = 3,
            PICK_ARCHIVE = 4, SAVE_ARCHIVE = 5, SAVE_NOVA = 6, SAVE_WALLPAPER = 7,
            PICK_KEY = 8, PICK_KNOWN_HOSTS = 9, SAVE_SCREENSHOT = 10;
    private final ArrayList<Uri> screenshots = new ArrayList<>();
    private Uri novaUri, wallpaperUri, restoreArchive;
    private EditText profile, notes;
    private TextView status, novaLabel, wallpaperLabel, screenshotsLabel;
    private View exportLatestButton;
    private File latestArchive;
    private JSONObject restoreManifest;
    private String restoreNovaEntry, restoreWallpaperEntry;
    private String restoreScreenshotEntry;
    private Spinner destination;
    private EditText host, remotePort, remoteUser, remoteFolder;
    private TextView keyLabel, hostsLabel;
    private Uri keyUri, knownHostsUri;
    private boolean uploadAfterConfig;
    private boolean buildingSnapshot;
    private boolean fileOperation;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener uploadListener = (p, key) -> {
        if ("lastUpload".equals(key) && status != null) runOnUiThread(() -> status.setText(displayUploadStatus(p.getString("lastUpload", ""))));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("pixi_snapshot", MODE_PRIVATE);
        keyUri = uriPref("keyUri"); knownHostsUri = uriPref("knownHostsUri");
        novaUri = uriPref("novaUri"); wallpaperUri = uriPref("wallpaperUri");
        try { JSONArray saved = new JSONArray(prefs.getString("screenshots", "[]")); for (int i=0;i<saved.length();i++) screenshots.add(Uri.parse(saved.getString(i))); } catch (Exception ignored) {}
        if (state != null) {
            String archive = state.getString("restoreArchive"); if (archive != null) restoreArchive = Uri.parse(archive);
            restoreNovaEntry = state.getString("restoreNovaEntry"); restoreWallpaperEntry = state.getString("restoreWallpaperEntry"); restoreScreenshotEntry = state.getString("restoreScreenshotEntry");
            uploadAfterConfig = state.getBoolean("uploadAfterConfig");
        }
        makeUi();
        latestArchive = findLatestArchive();
        String lastUpload = prefs.getString("lastUpload", "");
        if (!lastUpload.isEmpty()) status.setText(displayUploadStatus(lastUpload));
        else if (latestArchive != null) status.setText("Latest QuickSnap · " + humanSize(latestArchive.length()));
        if (exportLatestButton != null) exportLatestButton.setEnabled(latestArchive != null);
    }

    @Override protected void onResume() { super.onResume(); if (prefs != null) { prefs.registerOnSharedPreferenceChangeListener(uploadListener); String upload = prefs.getString("lastUpload", ""); if (!upload.isEmpty() && status != null) status.setText(displayUploadStatus(upload)); } }
    @Override protected void onPause() { if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(uploadListener); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (restoreArchive != null) state.putString("restoreArchive", restoreArchive.toString());
        state.putString("restoreNovaEntry", restoreNovaEntry); state.putString("restoreWallpaperEntry", restoreWallpaperEntry); state.putString("restoreScreenshotEntry", restoreScreenshotEntry);
        state.putBoolean("uploadAfterConfig", uploadAfterConfig); super.onSaveInstanceState(state);
    }

    // Persisted messages from earlier versions should use the current vocabulary.
    private String displayUploadStatus(String text) {
        return text.replaceAll("(?i)(?<![\\w./-])snapshots(?![\\w./-])", "QuickSnaps")
                .replaceAll("(?i)(?<![\\w./-])snapshot(?![\\w./-])", "QuickSnap");
    }

    private void makeUi() {
        int bg = 0xff202127, accent = 0xffff77ad, white = 0xffece9ee;
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setGravity(Gravity.CENTER_VERTICAL); screen.setPadding(dp(22), dp(20), dp(22), dp(24)); screen.setBackgroundColor(bg);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this); brand.setText("∅ PROTOCOL  ·  BACKUP & RESTORE"); brand.setTextSize(11); brand.setTypeface(null, Typeface.BOLD); brand.setTextColor(0xffd98eae); brand.setGravity(Gravity.CENTER); body.addView(brand);
        PixieLogoView pixi = new PixieLogoView(this); pixi.setContentDescription("PiXi"); body.addView(pixi, new LinearLayout.LayoutParams(-1, dp(54)));
        PixieDustView dust = new PixieDustView(this); dust.setContentDescription("Pixie dust falling from the PiXi wordmark");
        LinearLayout.LayoutParams dustParams = new LinearLayout.LayoutParams(-1, dp(56)); dustParams.setMargins(-dp(22), 0, -dp(22), 0); body.addView(dust, dustParams);
        TextView snapshot = new TextView(this); snapshot.setText("QuickSnaps"); snapshot.setTextSize(25); snapshot.setTypeface(null, Typeface.BOLD); snapshot.setTextColor(accent); snapshot.setGravity(Gravity.CENTER); snapshot.setPadding(0, 0, 0, dp(3)); body.addView(snapshot);
        TextView intro = label("Remember your apps and phone setup. Build or restore a QuickSnap offline."); intro.setGravity(Gravity.CENTER); intro.setPadding(0, dp(4), 0, dp(14)); body.addView(intro);
        LinearLayout remote = new LinearLayout(this); remote.setOrientation(LinearLayout.VERTICAL); remote.setPadding(dp(8), dp(8), dp(8), dp(8));
        android.graphics.drawable.GradientDrawable shell = new android.graphics.drawable.GradientDrawable(); shell.setColor(0xff292a31); shell.setCornerRadius(dp(22)); shell.setStroke(dp(1), 0xff494a54); remote.setBackground(shell);
        LinearLayout firstRow = new LinearLayout(this); firstRow.setOrientation(LinearLayout.HORIZONTAL);
        View create = remoteTile("●", "CREATE", "New QuickSnap", true); firstRow.addView(create, tileParams()); create.setOnClickListener(v -> createSnapshot());
        View restore = remoteTile("↻", "RESTORE", "Open a QuickSnap", false); firstRow.addView(restore, tileParams()); restore.setOnClickListener(v -> pick(PICK_ARCHIVE, "application/zip", false));
        LinearLayout secondRow = new LinearLayout(this); secondRow.setOrientation(LinearLayout.HORIZONTAL);
        exportLatestButton = remoteTile("↥", "EXPORT", "Latest QuickSnap", false); secondRow.addView(exportLatestButton, tileParams()); exportLatestButton.setOnClickListener(v -> offerLatest());
        exportLatestButton.setEnabled(false);
        View setup = remoteTile("⚙", "SETUP", "Details & destinations", false); secondRow.addView(setup, tileParams()); setup.setOnClickListener(v -> editSetupDetails());
        remote.addView(firstRow); remote.addView(secondRow); LinearLayout.LayoutParams remoteParams = new LinearLayout.LayoutParams(-1, -2); remoteParams.topMargin = dp(8); remoteParams.bottomMargin = dp(12); body.addView(remote, remoteParams);
        status = label("NO QUICKSNAP YET  ·  WORKS OFFLINE"); status.setGravity(Gravity.CENTER); status.setTextSize(12); status.setTypeface(null, Typeface.BOLD); status.setPadding(dp(10), dp(10), dp(10), dp(10));
        android.graphics.drawable.GradientDrawable display = new android.graphics.drawable.GradientDrawable(); display.setColor(0xff17181d); display.setCornerRadius(dp(12)); display.setStroke(dp(1), 0xff4d4e58); status.setBackground(display); body.addView(status);
        screen.addView(body, new LinearLayout.LayoutParams(-1, -2));
        ScrollView home = new ScrollView(this); home.setFillViewport(true); home.setBackgroundColor(bg); home.addView(screen, new ScrollView.LayoutParams(-1, -2)); setContentView(home);
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(94), 1f); p.setMargins(dp(5), dp(5), dp(5), dp(5)); return p;
    }

    private View remoteTile(String glyph, String title, String subtitle, boolean primary) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(8), dp(5), dp(8), dp(5));
        android.graphics.drawable.GradientDrawable face = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                primary ? new int[]{0xffff8cba, 0xffed5590} : new int[]{0xff3b3c46, 0xff303139});
        face.setCornerRadius(dp(13)); face.setStroke(dp(1), primary ? 0xffffb4d0 : 0xff60616c); tile.setBackground(face);
        tile.setFocusable(true); tile.setClickable(true); tile.setContentDescription(title + " · " + subtitle);
        TextView icon = new TextView(this); icon.setText(glyph); icon.setTextSize(19); icon.setTypeface(null, Typeface.BOLD); icon.setGravity(Gravity.CENTER); icon.setTextColor(primary ? 0xff3b1929 : 0xffff8ab8); tile.addView(icon);
        TextView name = new TextView(this); name.setText(title); name.setTextSize(13); name.setTypeface(null, Typeface.BOLD); name.setGravity(Gravity.CENTER); name.setTextColor(primary ? 0xff2b1720 : 0xfff2eff4); tile.addView(name);
        TextView detail = new TextView(this); detail.setText(subtitle); detail.setTextSize(10); detail.setGravity(Gravity.CENTER); detail.setTextColor(primary ? 0xff4a2535 : 0xffc3c1c9); tile.addView(detail);
        tile.setOnTouchListener((v, event) -> { if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) v.setAlpha(0.76f); else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) v.setAlpha(1f); return false; });
        return tile;
    }

    private final class PixieLogoView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private PixieLogoView(android.content.Context context) { super(context); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            String word = "PiXi";
            paint.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            paint.setTextSize(dpFloat(40));
            paint.setTextAlign(android.graphics.Paint.Align.LEFT);
            android.graphics.Paint.FontMetrics fm = paint.getFontMetrics();
            float x = (getWidth() - paint.measureText(word)) / 2f;
            float baseline = (getHeight() - fm.ascent - fm.descent) / 2f;
            paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            paint.setStrokeWidth(dpFloat(1.2f)); paint.setColor(0xff54283e); paint.setShadowLayer(dpFloat(8), 0, dpFloat(2), 0x88ff6ca8);
            canvas.drawText(word, x, baseline, paint);
            paint.clearShadowLayer(); paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShader(new android.graphics.LinearGradient(x, baseline - dpFloat(34), x + paint.measureText(word), baseline + dpFloat(4),
                    new int[]{0xffffa5ca, 0xffff67a5, 0xffd4a9ef}, null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawText(word, x, baseline, paint); paint.setShader(null);

            // Tiny four-point glints turn the two i dots into part of the wordmark.
            float firstI = x + paint.measureText("P") + paint.measureText("i") * 0.52f;
            float secondI = x + paint.measureText("PiX") + paint.measureText("i") * 0.52f;
            drawLogoSparkle(canvas, firstI, baseline - dpFloat(31), dpFloat(4.2f), 0xffffd8e9);
            drawLogoSparkle(canvas, secondI, baseline - dpFloat(31), dpFloat(4.2f), 0xffffd8e9);
        }
        private void drawLogoSparkle(android.graphics.Canvas canvas, float x, float y, float radius, int color) {
            paint.setColor(color); paint.setStrokeWidth(dpFloat(1.35f)); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            canvas.drawLine(x-radius, y, x+radius, y, paint); canvas.drawLine(x, y-radius, x, y+radius, paint);
            canvas.drawLine(x-radius*.62f, y-radius*.62f, x+radius*.62f, y+radius*.62f, paint);
            canvas.drawLine(x-radius*.62f, y+radius*.62f, x+radius*.62f, y-radius*.62f, paint);
            canvas.drawCircle(x, y, dpFloat(1.1f), paint);
        }
        private float dpFloat(float value) { return value * getResources().getDisplayMetrics().density; }
    }

    private final class PixieDustView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private PixieDustView(android.content.Context context) { super(context); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, h = getHeight();
            int[] colors = {0xffff77ad, 0xffffb4d0, 0xffc7a8da, 0xffffd5e6};
            float[][] dots = {
                    {0.00f,0.04f,2.1f},{-0.05f,0.10f,1.3f},{0.07f,0.16f,2.5f},{-0.13f,0.22f,1.5f},{0.18f,0.27f,1.6f},
                    {-0.02f,0.32f,3.0f},{-0.24f,0.36f,1.4f},{0.28f,0.39f,1.8f},{0.12f,0.45f,2.2f},{-0.34f,0.49f,1.5f},
                    {0.42f,0.43f,1.7f},{0.55f,0.52f,1.9f},{0.67f,0.36f,1.3f},{-0.49f,0.57f,1.4f},{0.02f,0.60f,1.6f},
                    {-0.18f,0.69f,1.3f},{0.35f,0.72f,1.4f},{-0.59f,0.77f,1.7f},{0.76f,0.65f,1.5f},{-0.72f,0.42f,1.2f},
                    {0.23f,0.83f,1.5f},{-0.42f,0.88f,1.2f},{0.63f,0.91f,1.4f},{-0.08f,0.95f,1.2f}
            };
            for (int i=0;i<dots.length;i++) {
                float x = cx + dots[i][0] * getWidth(); float y = dots[i][1] * h;
                paint.setColor(colors[i % colors.length]); canvas.drawCircle(x, y, dpFloat(dots[i][2]), paint);
                if (i == 2 || i == 5 || i == 8 || i == 11 || i == 17 || i == 22) {
                    float r = dpFloat(i == 5 ? 6f : 4.5f); paint.setStrokeWidth(dpFloat(1.4f));
                    canvas.drawLine(x-r, y, x+r, y, paint); canvas.drawLine(x, y-r, x, y+r, paint);
                    canvas.drawLine(x-r*0.65f, y-r*0.65f, x+r*0.65f, y+r*0.65f, paint);
                    canvas.drawLine(x-r*0.65f, y+r*0.65f, x+r*0.65f, y-r*0.65f, paint);
                }
            }
            // A striped candy straw slips in from the right edge and points into the drifting sparkle trail.
            float xStart = getWidth() + dpFloat(5f), yStart = h * 0.04f;
            float xEnd = getWidth() * 0.69f, yEnd = h * 0.72f;
            float dx = xEnd - xStart, dy = yEnd - yStart, length = (float)Math.hypot(dx, dy);
            float nx = -dy / length, ny = dx / length;
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeWidth(dpFloat(8f)); paint.setColor(0xff5b4653); canvas.drawLine(xStart, yStart, xEnd, yEnd, paint);
            paint.setStrokeWidth(dpFloat(6f)); paint.setColor(0xffffe8df); canvas.drawLine(xStart, yStart, xEnd, yEnd, paint);
            paint.setStrokeCap(android.graphics.Paint.Cap.BUTT); paint.setStrokeWidth(dpFloat(2.5f)); paint.setColor(0xfff06d9f);
            for (float t = 0.12f; t < 0.92f; t += 0.17f) {
                float x = xStart + dx * t, y = yStart + dy * t, r = dpFloat(3.2f);
                canvas.drawLine(x - nx*r, y - ny*r, x + nx*r, y + ny*r, paint);
            }
            paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dpFloat(1.5f)); paint.setColor(0xff5b4653);
            canvas.drawCircle(xEnd, yEnd, dpFloat(3.1f), paint);
            paint.setStyle(android.graphics.Paint.Style.FILL); paint.setColor(0xff5b4653); canvas.drawCircle(xEnd, yEnd, dpFloat(1.1f), paint);
        }
        private float dpFloat(float value) { return value * getResources().getDisplayMetrics().density; }
    }

    private void editSetupDetails() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(6), dp(16), dp(8));
        TextView help = label("Optional: add notes and visual files. Create a fresh Nova backup in Nova Settings → Backup & restore, then select it here. App sources can be corrected below."); box.addView(help);
        profile = edit("Setup profile name"); profile.setText(prefs.getString("profile", "Daily")); box.addView(profile);
        notes = edit("Widgets, profiles, permissions, special settings…"); notes.setMinLines(2); notes.setGravity(Gravity.TOP); notes.setText(prefs.getString("notes", "")); box.addView(notes);
        novaLabel = label(novaUri == null ? "Nova backup · none selected" : "Nova backup · " + SnapshotArchive.displayName(this, novaUri)); box.addView(novaLabel);
        Button nova = button("Choose Nova backup", 0xffeee8ed); box.addView(nova); nova.setOnClickListener(v -> pick(PICK_NOVA, "application/*", false));
        wallpaperLabel = label(wallpaperUri == null ? "Wallpaper image · none selected" : "Wallpaper · " + SnapshotArchive.displayName(this, wallpaperUri)); box.addView(wallpaperLabel);
        Button wallpaper = button("Choose wallpaper image", 0xffeee8ed); box.addView(wallpaper); wallpaper.setOnClickListener(v -> pick(PICK_WALLPAPER, "image/*", false));
        screenshotsLabel = label(screenshots.isEmpty() ? "Screenshots · none selected" : screenshots.size() + " screenshot(s) selected"); box.addView(screenshotsLabel);
        Button shots = button("Choose home-screen screenshots", 0xffeee8ed); box.addView(shots); shots.setOnClickListener(v -> pick(PICK_SCREENSHOTS, "image/*", true));
        Button clear = button("Clear selected setup files", 0xffd5d1d8); box.addView(clear); clear.setOnClickListener(v -> {
            novaUri = null; wallpaperUri = null; screenshots.clear(); prefs.edit().remove("novaUri").remove("wallpaperUri").remove("screenshots").apply();
            novaLabel.setText("Nova backup · none selected"); wallpaperLabel.setText("Wallpaper image · none selected"); screenshotsLabel.setText("Screenshots · none selected");
        });
        Button sources = button("Review app sources", 0xffd5d1d8); box.addView(sources); sources.setOnClickListener(v -> reviewApps());
        Button hostSettings = button("Configure server export", 0xffd5d1d8); box.addView(hostSettings); hostSettings.setOnClickListener(v -> configureHost());
        Button privacy = button("Privacy & backup limits", 0xffd5d1d8); box.addView(privacy); privacy.setOnClickListener(v -> showTextAsset("Privacy & backup limits", "privacy.txt"));
        if (((JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE)).getPendingJob(WifiUploadJob.JOB_ID) != null) {
            Button cancel = button("Cancel queued / active export", 0xffd5d1d8); box.addView(cancel); cancel.setOnClickListener(v -> WifiUploadJob.cancel(this));
        }
        Button notices = button("Open-source license notices", 0xffd5d1d8); box.addView(notices); notices.setOnClickListener(v -> showOpenSourceNotices());
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Setup details").setView(scroll)
                .setPositiveButton("Save details", (d,w) -> prefs.edit().putString("profile", profile.getText().toString()).putString("notes", notes.getText().toString()).apply())
                .setNegativeButton("Close", null).show();
    }

    private void showTextAsset(String title, String file) {
        try (InputStream in = getAssets().open(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            TextView text = label(out.toString("UTF-8")); text.setTextIsSelectable(true);
            ScrollView scroll = new ScrollView(this); scroll.setPadding(dp(16), dp(8), dp(16), dp(8)); scroll.addView(text);
            new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
        } catch (Exception e) { error("Could not load details", e); }
    }

    private void showOpenSourceNotices() {
        StringBuilder text = new StringBuilder();
        String[] files = {"MIT.txt", "bouncycastle-MIT.txt", "jsch-BSD.txt", "jzlib-BSD.txt", "jbcrypt-ISC.txt"};
        try {
            for (String file : files) {
                text.append("\n--- ").append(file).append(" ---\n");
                try (InputStream in = getAssets().open("licenses/" + file)) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = in.read(buffer)) >= 0) text.append(new String(buffer, 0, count, java.nio.charset.StandardCharsets.UTF_8));
                }
                text.append("\n");
            }
        } catch (Exception e) { error("Could not load license notices", e); return; }
        TextView view = label(text.toString()); view.setTextSize(12); view.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.setPadding(dp(16), dp(8), dp(16), dp(8)); scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("Third-party notices").setView(scroll).setPositiveButton("Close", null).show();
    }

    private void createSnapshot() {
        if (buildingSnapshot) return;
        buildingSnapshot = true;
        String profileText = profile == null ? prefs.getString("profile", "Daily") : profile.getText().toString().trim();
        String notesText = notes == null ? prefs.getString("notes", "") : notes.getText().toString().trim();
        Uri selectedNova = novaUri, selectedWallpaper = wallpaperUri;
        List<Uri> selectedScreenshots = new ArrayList<>(screenshots);
        status.setText("Scanning installed apps and building the ZIP…");
        new Thread(() -> {
            try {
                JSONArray apps = captureApps();
                File created = SnapshotArchive.create(this, profileText, notesText, apps, selectedNova, selectedWallpaper, selectedScreenshots);
                runOnUiThread(() -> {
                    buildingSnapshot = false;
                    latestArchive = created;
                    if (exportLatestButton != null) exportLatestButton.setEnabled(true);
                    prefs.edit().putString("profile", profileText).putString("notes", notesText).apply();
                    String size = humanSize(latestArchive.length());
                    new AlertDialog.Builder(this).setTitle("QuickSnap is ready")
                            .setMessage(apps.length() + " user apps and selected setup files\nArchive size: " + size + "\n\nWhere should this copy go?")
                            .setPositiveButton("Save on this phone", (d, w) -> saveLocal())
                            .setNeutralButton("Send to a host…", (d, w) -> { if (prefs.getString("host", "").isEmpty() || keyUri == null || knownHostsUri == null) { uploadAfterConfig = true; configureHost(); } else askNetworkAndUpload(); })
                            .setNegativeButton("Later", (d, w) -> status.setText("QuickSnap created · " + size + " · stored inside PiXi QuickSnaps until exported."))
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { buildingSnapshot = false; error("Could not create QuickSnap", e); });
            }
        }, "pixi-snapshot-create").start();
    }

    private File findLatestArchive() {
        File folder = new File(getFilesDir(), "snapshots");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null || files.length == 0) return null;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files[0];
    }

    private void offerLatest() {
        if (latestArchive == null || !latestArchive.isFile()) { toast("Create a QuickSnap first."); return; }
        String size = humanSize(latestArchive.length());
        new AlertDialog.Builder(this).setTitle("QuickSnap · " + size)
                .setMessage(latestArchive.getName() + "\n\nSave a copy on this phone or send it to a host?")
                .setPositiveButton("Save on this phone", (d,w) -> saveLocal())
                .setNeutralButton("Send to a host…", (d,w) -> { if (prefs.getString("host", "").isEmpty() || keyUri == null || knownHostsUri == null) { uploadAfterConfig = true; configureHost(); } else askNetworkAndUpload(); })
                .setNegativeButton("Close", null).show();
    }

    private JSONArray captureApps() throws Exception {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        JSONArray result = new JSONArray();
        for (ApplicationInfo ai : installed) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || getPackageName().equals(ai.packageName)) continue;
            JSONObject app = new JSONObject();
            app.put("package", ai.packageName);
            app.put("name", pm.getApplicationLabel(ai).toString());
            try { PackageInfo pi = pm.getPackageInfo(ai.packageName, 0); app.put("version", pi.versionName == null ? "" : pi.versionName); }
            catch (Exception ignored) { app.put("version", ""); }
            String source = prefs.getString("source:" + ai.packageName, SnapshotArchive.installerFor(this, ai.packageName));
            String sourceUrl = prefs.getString("url:" + ai.packageName, "");
            app.put("installerPackage", SnapshotArchive.installerPackageFor(this, ai.packageName));
            app.put("source", source); app.put("sourceUrl", sourceUrl);
            result.put(app);
        }
        return result;
    }

    private void showAppCount() {
        try { int count = captureApps().length(); status.setText(count + " user-installed apps found. Open “Review app sources” to correct store labels before creating the QuickSnap."); }
        catch (Exception e) { error("Could not scan apps", e); }
    }

    private void reviewApps() {
        try {
            JSONArray apps = captureApps();
            String[] rows = new String[apps.length()];
            for (int i = 0; i < apps.length(); i++) { JSONObject a = apps.getJSONObject(i); rows[i] = a.optString("name") + "\n" + a.optString("source") + "  ·  " + a.optString("package"); }
            new AlertDialog.Builder(this).setTitle("App source labels")
                    .setItems(rows, (d, which) -> editSource(apps.optJSONObject(which)))
                    .setPositiveButton("Done", null).show();
        } catch (Exception e) { error("Could not list apps", e); }
    }

    private void editSource(JSONObject app) {
        if (app == null) return;
        String pkg = app.optString("package");
        LinearLayout box = new LinearLayout(this); box.setPadding(dp(12), dp(8), dp(12), 0); box.setOrientation(LinearLayout.VERTICAL);
        EditText source = edit("Source label"); source.setText(app.optString("source")); box.addView(source);
        EditText url = edit("Optional app page or download URL"); url.setText(app.optString("sourceUrl")); box.addView(url);
        new AlertDialog.Builder(this).setTitle(app.optString("name")).setView(box)
                .setPositiveButton("Save", (d, w) -> { prefs.edit().putString("source:" + pkg, source.getText().toString().trim()).putString("url:" + pkg, url.getText().toString().trim()).apply(); reviewApps(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void saveLocal() {
        if (latestArchive == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, latestArchive.getName());
        startActivityForResult(i, SAVE_ARCHIVE);
    }

    private void pick(int request, String type, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        startActivityForResult(i, request);
    }

    private void configureHost() {
        LinearLayout box = new LinearLayout(this); box.setPadding(dp(16), dp(6), dp(16), dp(8)); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(label("Send directly by SFTP over Wi-Fi, mobile data, or your phone’s Tailscale connection. Each destination has its own settings. Use an SSH key without a passphrase and a trusted known_hosts file. SSH files stay out of QuickSnaps."));
        destination = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"My server", "Dolo", "Swervo", "Novo"}); destination.setAdapter(adapter);
        int selected = Math.min(3, Math.max(0, prefs.getInt("destinationChoice", 0))); destination.setSelection(selected, false); box.addView(destination);
        host = edit("Host name or Tailscale IP"); box.addView(host);
        remotePort = edit("SSH port"); remotePort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); box.addView(remotePort);
        remoteUser = edit("SSH username"); box.addView(remoteUser);
        remoteFolder = edit("Remote folder (must already exist)"); box.addView(remoteFolder);
        keyLabel = label(""); box.addView(keyLabel); Button chooseKey = button("Choose SSH private key", 0xffead1dd); box.addView(chooseKey); chooseKey.setOnClickListener(v -> pick(PICK_KEY, "*/*", false));
        hostsLabel = label(""); box.addView(hostsLabel); Button chooseHosts = button("Choose known_hosts file", 0xffead1dd); box.addView(chooseHosts); chooseHosts.setOnClickListener(v -> pick(PICK_KNOWN_HOSTS, "*/*", false));
        loadDestination(selected, true);
        final boolean[] first = {true};
        destination.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { if (first[0]) { first[0] = false; return; } loadDestination(pos, false); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Server export").setView(scroll)
                .setPositiveButton("Save destination", null).setNegativeButton("Close", (d,w) -> uploadAfterConfig = false).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int port = Integer.parseInt(remotePort.getText().toString().trim());
                SftpTransfer.Destination config = new SftpTransfer.Destination(host.getText().toString(), port, remoteUser.getText().toString(), remoteFolder.getText().toString(), keyUri == null ? null : keyUri.toString(), knownHostsUri == null ? null : knownHostsUri.toString());
                String prefix = "dest" + destination.getSelectedItemPosition() + ":";
                SharedPreferences.Editor edit = prefs.edit().putInt("destinationChoice", destination.getSelectedItemPosition());
                for (String key : new String[]{"", prefix}) edit.putString(key+"host", config.host).putInt(key+"port", config.port).putString(key+"remoteUser", config.user).putString(key+"remoteFolder", config.folder).putString(key+"keyUri", config.keyUri).putString(key+"knownHostsUri", config.knownHostsUri);
                edit.apply(); dialog.dismiss(); status.setText("Server destination saved.");
                if (uploadAfterConfig) { uploadAfterConfig = false; askNetworkAndUpload(); }
            } catch (Exception e) { toast(e instanceof NumberFormatException ? "Enter an SSH port between 1 and 65535." : e.getMessage()); }
        }));
        dialog.show();
    }

    private void loadDestination(int index, boolean allowLegacy) {
        String prefix = "dest" + index + ":";
        if (!prefs.contains(prefix+"host") && allowLegacy) prefix = "";
        host.setText(prefs.getString(prefix+"host", "")); remotePort.setText(String.valueOf(prefs.getInt(prefix+"port", 22)));
        remoteUser.setText(prefs.getString(prefix+"remoteUser", "")); remoteFolder.setText(prefs.getString(prefix+"remoteFolder", "quick-snaps"));
        keyUri = uriPref(prefix+"keyUri"); knownHostsUri = uriPref(prefix+"knownHostsUri");
        keyLabel.setText(keyUri == null ? "SSH private key: not selected" : "SSH private key selected"); hostsLabel.setText(knownHostsUri == null ? "known_hosts: not selected" : "known_hosts selected");
    }

    private void askNetworkAndUpload() {
        if (latestArchive == null) return;
        try { SftpTransfer.Destination.fromPreferences(prefs); } catch (Exception e) { uploadAfterConfig = true; configureHost(); return; }
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        boolean metered = cm == null || cm.isActiveNetworkMetered();
        new AlertDialog.Builder(this).setTitle("Server export · " + humanSize(latestArchive.length()))
                .setMessage("Send to " + prefs.getString("host", "") + "?\n\n" + (metered ? "Your current network may use mobile data or be disconnected. " : "Your current network is unmetered. ") + "You can send now or wait for an unmetered connection. Keep a local copy: QuickSnaps are not password-encrypted.")
                .setPositiveButton("Send now", (d,w) -> scheduleUpload(false)).setNeutralButton("Wait for Wi-Fi", (d,w) -> scheduleUpload(true)).setNegativeButton("Cancel", null).show();
    }

    private void scheduleUpload(boolean unmetered) {
        if (latestArchive == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
        try {
            if (!WifiUploadJob.schedule(this, latestArchive, SftpTransfer.Destination.fromPreferences(prefs), unmetered))
                toast("An export is already queued or could not be scheduled. Cancel it in Setup to replace it.");
            status.setText(displayUploadStatus(prefs.getString("lastUpload", "")));
        } catch (Exception e) { error("Could not queue export", e); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null && data.getClipData() == null) return;
        try {
            switch (request) {
                case PICK_NOVA: novaUri = keep(uri); prefs.edit().putString("novaUri", uri.toString()).apply(); if (novaLabel != null) novaLabel.setText(SnapshotArchive.displayName(this, uri)); break;
                case PICK_WALLPAPER: wallpaperUri = keep(uri); prefs.edit().putString("wallpaperUri", uri.toString()).apply(); if (wallpaperLabel != null) wallpaperLabel.setText(SnapshotArchive.displayName(this, uri)); break;
                case PICK_SCREENSHOTS:
                    screenshots.clear();
                    if (data.getClipData() != null) for (int i=0;i<data.getClipData().getItemCount();i++) screenshots.add(keep(data.getClipData().getItemAt(i).getUri()));
                    else screenshots.add(keep(uri));
                    JSONArray saved = new JSONArray(); for (Uri selected : screenshots) if (selected != null) saved.put(selected.toString()); prefs.edit().putString("screenshots", saved.toString()).apply();
                    if (screenshotsLabel != null) screenshotsLabel.setText(screenshots.size() + " screenshot(s) selected"); break;
                case PICK_ARCHIVE: restoreArchive = keep(uri); openRestore(uri); break;
                case SAVE_ARCHIVE: {
                    File file = latestArchive;
                    performFileWork("Saving QuickSnap…", () -> copyFile(file, uri), () -> status.setText("Saved " + humanSize(file.length()) + " to the selected folder.")); break;
                }
                case SAVE_NOVA: {
                    Uri archive = restoreArchive; String entry = restoreNovaEntry;
                    performFileWork("Exporting Nova backup…", () -> SnapshotArchive.copyEntryTo(this, archive, entry, uri), () -> toast("Nova backup exported. Open Nova Settings → Backup & restore to load it.")); break;
                }
                case SAVE_WALLPAPER: {
                    Uri archive = restoreArchive; String entry = restoreWallpaperEntry;
                    performFileWork("Exporting wallpaper…", () -> SnapshotArchive.copyEntryTo(this, archive, entry, uri), () -> new AlertDialog.Builder(this).setMessage("Wallpaper image exported. Set it from the selected image in your wallpaper app.")
                            .setPositiveButton("Set as wallpaper", (d,w) -> setWallpaperFrom(uri)).setNegativeButton("Done", null).show()); break;
                }
                case SAVE_SCREENSHOT: {
                    Uri archive = restoreArchive; String entry = restoreScreenshotEntry;
                    performFileWork("Exporting screenshot…", () -> SnapshotArchive.copyEntryTo(this, archive, entry, uri), () -> toast("Screenshot reference exported.")); break;
                }
                case PICK_KEY: keyUri = keep(uri); if (keyLabel != null) keyLabel.setText("SSH private key selected"); break;
                case PICK_KNOWN_HOSTS: knownHostsUri = keep(uri); if (hostsLabel != null) hostsLabel.setText("known_hosts selected"); break;
            }
        } catch (Exception e) { error("Could not use selected file", e); }
    }

    private void openRestore(Uri archive) {
        if (fileOperation) { toast("Please wait for the current file operation."); return; }
        fileOperation = true; status.setText("Reading QuickSnap…");
        new Thread(() -> {
            try {
                JSONObject manifest = SnapshotArchive.readManifest(this, archive);
                runOnUiThread(() -> { fileOperation = false; if (!isFinishing() && !isDestroyed()) { restoreArchive = archive; restoreManifest = manifest; showRestoreGuide(); } });
            } catch (Exception e) { runOnUiThread(() -> { fileOperation = false; if (!isFinishing() && !isDestroyed()) error("Cannot read this QuickSnap", e); }); }
        }, "pixi-snapshot-read").start();
    }

    private void showRestoreGuide() {
        try {
            JSONArray loadedApps = restoreManifest.optJSONArray("apps");
            final JSONArray apps = loadedApps == null ? new JSONArray() : loadedApps;
            JSONArray assets = restoreManifest.optJSONArray("assets"); restoreNovaEntry = null; restoreWallpaperEntry = null;
            if (assets != null) for (int i=0;i<assets.length();i++) { String entry = assets.optString(i); if (entry.startsWith("assets/nova/")) restoreNovaEntry = entry; if (entry.startsWith("assets/wallpaper/")) restoreWallpaperEntry = entry; }
            ArrayList<String> missing = new ArrayList<>();
            StringBuilder summary = new StringBuilder("Profile: ").append(restoreManifest.optString("profile", "(unnamed)")).append("\nCreated: ").append(restoreManifest.optString("createdAt", "unknown")).append("\nDevice: ").append(restoreManifest.optString("device", "unknown")).append("\n\n");
            if (!restoreManifest.optString("notes", "").isEmpty()) summary.append("Setup notes:\n").append(restoreManifest.optString("notes")).append("\n\n");
            for (int i=0;i<apps.length();i++) { JSONObject app=apps.getJSONObject(i); if (!installed(app.optString("package"))) missing.add(app.optString("name")+"  ·  "+app.optString("source")+"  ·  "+app.optString("package")); }
            JSONArray profileRows=restoreManifest.optJSONArray("androidProfiles");
            summary.append("Apps listed: ").append(apps.length()).append("\nNot currently installed: ").append(missing.size())
                    .append("\nAndroid profiles recorded: ").append(profileRows==null?0:profileRows.length())
                    .append("\n\nApp installs need your confirmation. Nova layout, wallpaper and screenshots are available below.");
            LinearLayout box = new LinearLayout(this); box.setPadding(dp(18),dp(8),dp(18),0); box.setOrientation(LinearLayout.VERTICAL);
            TextView text=label(summary.toString()); box.addView(text);
            if (!missing.isEmpty()) { Button show=button("Show apps to reinstall ("+missing.size()+")",0xffead1dd); box.addView(show); show.setOnClickListener(v -> showMissing(apps)); }
            if (restoreNovaEntry != null) { Button b=button("Export Nova backup",0xffead1dd); box.addView(b); b.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, restoreNovaEntry.substring(restoreNovaEntry.lastIndexOf('/')+1)), SAVE_NOVA)); }
            if (restoreWallpaperEntry != null) { Button b=button("Export wallpaper image",0xffead1dd); box.addView(b); b.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, restoreWallpaperEntry.substring(restoreWallpaperEntry.lastIndexOf('/')+1)), SAVE_WALLPAPER)); }
            if (assets != null) { for (int i=0;i<assets.length();i++) { String a=assets.optString(i); if (a.startsWith("assets/screenshots/")) { Button s=button("Export screenshot · "+a.substring(a.lastIndexOf('/')+1),0xffead1dd); box.addView(s); s.setOnClickListener(v -> { restoreScreenshotEntry=a; startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,a.substring(a.lastIndexOf('/')+1)), SAVE_SCREENSHOT); }); } } }
            ScrollView restoreScroll = new ScrollView(this); restoreScroll.addView(box);
            new AlertDialog.Builder(this).setTitle("Setup restore guide").setView(restoreScroll).setPositiveButton("Close",null).show();
        } catch (Exception e) { error("Cannot read this QuickSnap", e); }
    }

    private void showMissing(JSONArray apps) {
        ArrayList<JSONObject> missing = new ArrayList<>(); ArrayList<String> names = new ArrayList<>();
        for (int i=0;i<apps.length();i++) { JSONObject a=apps.optJSONObject(i); if (a!=null && !installed(a.optString("package"))) { missing.add(a); names.add(a.optString("name")+"\n"+a.optString("source")+" · "+a.optString("package")); } }
        new AlertDialog.Builder(this).setTitle("Choose an app to find").setItems(names.toArray(new String[0]),(d,which)->openSource(missing.get(which))).setPositiveButton("Done",null).show();
    }

    private void openSource(JSONObject app) {
        String pkg=app.optString("package"), source=app.optString("source"), url=app.optString("sourceUrl");
        try {
            if (url != null && !url.isEmpty()) {
                Uri supplied = Uri.parse(url);
                if ("https".equalsIgnoreCase(supplied.getScheme()) || "http".equalsIgnoreCase(supplied.getScheme())) { startActivity(new Intent(Intent.ACTION_VIEW, supplied)); return; }
            }
            if (source.equalsIgnoreCase("F-Droid")) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/"+pkg+"/"))); return; }
            if (source.equalsIgnoreCase("Aurora Store") || source.equalsIgnoreCase("Google Play")) {
                Intent market=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+pkg));
                if (market.resolveActivity(getPackageManager())!=null) startActivity(market);
                else startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://duckduckgo.com/?q="+Uri.encode(pkg+" Aurora Store"))));
                return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://duckduckgo.com/?q="+Uri.encode(pkg+" "+source+" download"))));
        } catch (Exception e) { toast("No app or browser is available for that source."); }
    }

    private boolean installed(String pkg) { try { getPackageManager().getPackageInfo(pkg,0); return true; } catch(Exception e) { return false; } }

    private interface FileWork { void run() throws Exception; }
    private void performFileWork(String message, FileWork work, Runnable done) {
        if (fileOperation) { toast("Please wait for the current file operation."); return; }
        fileOperation = true; status.setText(message);
        new Thread(() -> {
            try { work.run(); runOnUiThread(() -> { fileOperation = false; if (!isFinishing() && !isDestroyed()) { status.setText("File operation complete."); done.run(); } }); }
            catch (Exception e) { runOnUiThread(() -> { fileOperation = false; if (!isFinishing() && !isDestroyed()) error("Could not complete file operation", e); }); }
        }, "pixi-snapshot-file").start();
    }

    private void setWallpaperFrom(Uri uri) {
        performFileWork("Setting wallpaper…", () -> {
            try (InputStream in=getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Could not open wallpaper image."); WallpaperManager.getInstance(this).setStream(in);
            }
        }, () -> toast("Wallpaper set."));
    }

    private Uri keep(Uri uri) { if (uri == null) return null; try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {} return uri; }

    private void copyFile(File file, Uri dest) throws Exception {
        try (InputStream in=new FileInputStream(file); OutputStream out=getContentResolver().openOutputStream(dest,"wt")) {
            if (out==null) throw new Exception("Cannot write to selected folder."); byte[] b=new byte[32768]; int n; while((n=in.read(b))>=0) out.write(b,0,n);
        }
    }

    private Uri uriPref(String key) { String s=prefs.getString(key,null); return s==null?null:Uri.parse(s); }
    private static String humanSize(long bytes) { if(bytes<1024)return bytes+" B"; if(bytes<1024*1024)return String.format(Locale.US,"%.1f KB",bytes/1024.0); return String.format(Locale.US,"%.1f MB",bytes/(1024.0*1024.0)); }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private EditText edit(String hint) { EditText e=new EditText(this); e.setHint(hint); e.setTextColor(0xff282128); e.setHintTextColor(0xff756671); e.setSingleLine(hint.startsWith("Profile")||hint.startsWith("Host")||hint.startsWith("SSH")||hint.startsWith("Remote")); e.setBackgroundColor(0xffeee8ed); e.setPadding(dp(12),dp(8),dp(12),dp(8)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(8); e.setLayoutParams(p); return e; }
    private TextView label(String text) { TextView t=new TextView(this); t.setText(text); t.setTextColor(0xffdedce2); t.setTextSize(14); t.setPadding(0,dp(5),0,dp(8)); return t; }
    private void section(LinearLayout root,String text,int color) { TextView t=new TextView(this); t.setText(text); t.setTextColor(color); t.setTextSize(17); t.setTypeface(null,Typeface.BOLD); t.setPadding(0,dp(18),0,dp(8)); root.addView(t); }
    private Button button(String text,int color) { Button b=new Button(this); b.setText(text); b.setTextColor(0xff292128); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(7); b.setLayoutParams(p); return b; }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    private void error(String title,Exception e) { String message=e.getMessage()==null?e.toString():e.getMessage(); status.setText(title+": "+message); new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show(); }
}
