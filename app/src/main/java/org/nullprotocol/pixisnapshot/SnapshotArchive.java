package org.nullprotocol.pixisnapshot;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SnapshotArchive {
    static final String MANIFEST = "snapshot.json";
    static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
    static final int MAX_ENTRIES = 1024;
    private SnapshotArchive() {}

    static String installerPackageFor(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            String installer = Build.VERSION.SDK_INT >= 30 ? pm.getInstallSourceInfo(pkg).getInstallingPackageName() : pm.getInstallerPackageName(pkg);
            return installer == null ? "" : installer;
        } catch (Exception ignored) { return ""; }
    }

    static String installerFor(Context context, String pkg) {
        try {
            String installer = installerPackageFor(context, pkg);
            if (installer.isEmpty()) return "Unknown / manually imported";
            String value = installer.toLowerCase(Locale.ROOT);
            if (value.contains("fdroid")) return "F-Droid";
            if (value.contains("aurora")) return "Aurora Store";
            if (value.contains("aptoide")) return "Aptoide";
            if (value.contains("vending")) return "Google Play";
            if (value.contains("packageinstaller") || value.contains("permissioncontroller"))
                return "Unknown / manually imported";
            return "Installed by " + installer;
        } catch (Exception ignored) {
            return "Unknown / manually imported";
        }
    }

    static File create(Context context, String profile, String notes, JSONArray apps,
                       Uri novaUri, Uri wallpaperUri, List<Uri> screenshots) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        File parent = new File(context.getFilesDir(), "snapshots");
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("Cannot create snapshot folder.");
        if (screenshots.size() > 100) throw new Exception("Choose no more than 100 screenshots per snapshot.");
        if (profile.length() > 256 || notes.length() > 65536) throw new Exception("Profile or notes are too long.");
        File out = File.createTempFile("pixi-snapshot-" + stamp + "-", ".partial", parent);
        JSONObject root = new JSONObject();
        root.put("format", "org.nullprotocol.pixi-snapshot");
        root.put("formatVersion", 1);
        root.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));
        root.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        root.put("android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        root.put("profile", profile);
        root.put("notes", notes);
        root.put("apps", apps);
        JSONArray profiles = new JSONArray();
        try {
            UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
            for (UserHandle handle : users.getUserProfiles()) {
                JSONObject item = new JSONObject();
                item.put("serial", users.getSerialNumberForUser(handle));
                item.put("current", handle.equals(android.os.Process.myUserHandle()));
                profiles.put(item);
            }
        } catch (Exception ignored) {}
        root.put("androidProfiles", profiles);
        JSONArray assets = new JSONArray();
        if (novaUri != null) assets.put("assets/nova/" + safeName(displayName(context, novaUri), "nova-backup.novabackup"));
        if (wallpaperUri != null) assets.put("assets/wallpaper/" + safeName(displayName(context, wallpaperUri), "wallpaper-image"));
        for (int i = 0; i < screenshots.size(); i++)
            assets.put("assets/screenshots/" + (i + 1) + "-" + safeName(displayName(context, screenshots.get(i)), "screenshot"));
        root.put("assets", assets);

        long[] total = {0};
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            zip.putNextEntry(new ZipEntry(MANIFEST));
            byte[] manifest = root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (manifest.length > MAX_MANIFEST_BYTES) throw new Exception("App inventory and notes exceed 2 MiB.");
            zip.write(manifest); total[0] += manifest.length;
            zip.closeEntry();
            if (novaUri != null) addUri(context, zip, "assets/nova/" + safeName(displayName(context, novaUri), "nova-backup.novabackup"), novaUri, total);
            if (wallpaperUri != null) addUri(context, zip, "assets/wallpaper/" + safeName(displayName(context, wallpaperUri), "wallpaper-image"), wallpaperUri, total);
            for (int i = 0; i < screenshots.size(); i++)
                addUri(context, zip, "assets/screenshots/" + (i + 1) + "-" + safeName(displayName(context, screenshots.get(i)), "screenshot"), screenshots.get(i), total);
        } catch (Exception e) { out.delete(); throw e; }
        File complete = new File(parent, out.getName().replace(".partial", ".zip"));
        if (!out.renameTo(complete)) { out.delete(); throw new Exception("Could not finish the snapshot file."); }
        return complete;
    }

    static JSONObject readManifest(Context context, Uri archive) throws Exception {
        File cached = cacheArchive(context, archive);
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(cached)) {
            java.util.HashSet<String> names = new java.util.HashSet<>(); long total = 0; int count = 0;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (++count > MAX_ENTRIES || !names.add(e.getName())) throw new Exception("Snapshot has too many entries or duplicate file names.");
                if (e.getSize() < 0 || e.getSize() > MAX_ARCHIVE_BYTES || (total += e.getSize()) > MAX_ARCHIVE_BYTES)
                    throw new Exception("Snapshot exceeds the 256 MiB uncompressed limit.");
            }
            ZipEntry manifest = zip.getEntry(MANIFEST);
            if (manifest == null || manifest.isDirectory()) throw new Exception("This ZIP does not contain a PiXi Snapshot manifest.");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            try (InputStream in = zip.getInputStream(manifest)) { copyLimited(in, out, MAX_MANIFEST_BYTES); }
            JSONObject root = new JSONObject(out.toString("UTF-8"));
            if (!"org.nullprotocol.pixi-snapshot".equals(root.optString("format")) || root.optInt("formatVersion", -1) != 1)
                throw new Exception("Unsupported snapshot format or version.");
            JSONArray apps = root.optJSONArray("apps"), assets = root.optJSONArray("assets");
            if (apps == null || assets == null || apps.length() > 5000 || assets.length() > 102) throw new Exception("Invalid snapshot inventory.");
            for (int i=0;i<apps.length();i++) {
                JSONObject app = apps.optJSONObject(i);
                if (app == null || !app.optString("package").matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
                    throw new Exception("Snapshot contains an invalid app package name.");
            }
            java.util.HashSet<String> declared = new java.util.HashSet<>();
            for (int i=0;i<assets.length();i++) {
                String name = assets.getString(i); ZipEntry asset = zip.getEntry(name);
                if (!validAssetName(name) || !declared.add(name) || asset == null || asset.isDirectory()) throw new Exception("Snapshot contains an invalid or missing setup file.");
            }
            return root;
        } finally { cached.delete(); }
    }

    static void copyEntryTo(Context context, Uri archive, String name, Uri destination) throws Exception {
        if (!validAssetName(name)) throw new Exception("Invalid snapshot file name.");
        File cached = cacheArchive(context, archive);
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(cached)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) throw new Exception("That snapshot does not contain the requested file.");
            try (InputStream in = zip.getInputStream(entry); OutputStream out = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new Exception("Cannot write the selected destination.");
                copyLimited(in, out, MAX_ARCHIVE_BYTES);
            }
        } finally { cached.delete(); }
    }

    private static boolean validAssetName(String name) {
        return name != null && name.length() <= 240 && name.matches("assets/(nova|wallpaper|screenshots)/[A-Za-z0-9._-]+") && !name.endsWith("/.") && !name.endsWith("/..");
    }

    private static File cacheArchive(Context context, Uri archive) throws Exception {
        File cached = File.createTempFile("pixi-restore-", ".zip", context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(archive); OutputStream out = new FileOutputStream(cached)) {
            if (in == null) throw new Exception("Cannot open selected snapshot.");
            copyLimited(in, out, MAX_ARCHIVE_BYTES);
        } catch (Exception e) { cached.delete(); throw e; }
        return cached;
    }

    private static long copyLimited(InputStream in, OutputStream out, long maximum) throws Exception {
        long total = 0; byte[] buffer = new byte[32768]; int count;
        while ((count = in.read(buffer)) != -1) {
            total += count; if (total > maximum) throw new Exception("Selected file exceeds the snapshot size limit."); out.write(buffer, 0, count);
        }
        return total;
    }

    static String displayName(Context context, Uri uri) {
        try (android.database.Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) {}
        return "selected-file";
    }

    static long size(Context context, Uri uri) {
        try (android.database.Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (index >= 0 && !c.isNull(index)) return c.getLong(index);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static void addUri(Context context, ZipOutputStream zip, String name, Uri uri, long[] total) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Cannot read selected file: " + displayName(context, uri));
            byte[] buffer = new byte[32768]; int count;
            while ((count = in.read(buffer)) != -1) {
                total[0] += count; if (total[0] > MAX_ARCHIVE_BYTES) throw new Exception("Setup files exceed the 256 MiB snapshot limit."); zip.write(buffer, 0, count);
            }
        }
        zip.closeEntry();
    }

    private static String safeName(String value, String fallback) {
        String clean = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (clean.length() > 120) clean = clean.substring(clean.length() - 120);
        return clean.isEmpty() || clean.equals(".") || clean.equals("..") ? fallback : clean;
    }
}
