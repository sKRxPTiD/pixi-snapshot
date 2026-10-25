package org.nullprotocol.pixisnapshot;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs on an isolated emulator. No changes to personal phone settings or wallpaper. */
public class ReleaseChecks extends Instrumentation {
    private Bundle arguments;
    private final StringBuilder results = new StringBuilder();
    @Override public void onCreate(Bundle args) { arguments = args; super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!android.os.Build.HARDWARE.contains("ranchu") && !android.os.Build.HARDWARE.contains("goldfish")) throw new Exception("Run these checks only on a disposable Android emulator.");
            context.getSharedPreferences("pixi_snapshot", Context.MODE_PRIVATE).edit().clear().commit();
            File nova = fixture(context, "layout.novabackup", "sample launcher setup");
            File image = fixture(context, "wallpaper.png", "sample wallpaper bytes");
            JSONArray apps = new JSONArray().put(new JSONObject().put("package", "org.example.missing").put("name", "Missing app").put("source", "F-Droid"));
            File archive = SnapshotArchive.create(context, "Test profile", "Widget rebuild notes", apps, Uri.fromFile(nova), Uri.fromFile(image), Collections.singletonList(Uri.fromFile(image)));
            JSONObject manifest = SnapshotArchive.readManifest(context, Uri.fromFile(archive));
            check(manifest.getJSONArray("apps").length() == 1 && manifest.getJSONArray("assets").length() == 3, "inventory and selected assets round trip");
            File restored = new File(context.getCacheDir(), "restored.novabackup");
            SnapshotArchive.copyEntryTo(context, Uri.fromFile(archive), manifest.getJSONArray("assets").getString(0), Uri.fromFile(restored));
            check(java.util.Arrays.equals(Files.readAllBytes(nova.toPath()), Files.readAllBytes(restored.toPath())), "Nova file export preserves bytes");
            File second = SnapshotArchive.create(context, "Test", "", apps, null, null, Collections.emptyList());
            check(!archive.equals(second) && archive.isFile(), "consecutive snapshots preserve previous files");
            File malformed = zip(context, "wrong-format.zip", "{\"format\":\"other\",\"formatVersion\":1,\"apps\":[],\"assets\":[]}");
            rejects(() -> SnapshotArchive.readManifest(context, Uri.fromFile(malformed)), "wrong snapshot format rejected");
            File oversized = zip(context, "huge-manifest.zip", new String(new char[SnapshotArchive.MAX_MANIFEST_BYTES+1]).replace('\0', ' '));
            rejects(() -> SnapshotArchive.readManifest(context, Uri.fromFile(oversized)), "oversized manifest rejected without unbounded allocation");
            File invalid = zip(context, "invalid-package.zip", "{\"format\":\"org.nullprotocol.pixi-snapshot\",\"formatVersion\":1,\"apps\":[{\"package\":\"bad/path\"}],\"assets\":[]}");
            rejects(() -> SnapshotArchive.readManifest(context, Uri.fromFile(invalid)), "invalid package rejected");
            rejects(() -> SnapshotArchive.copyEntryTo(context, Uri.fromFile(archive), "assets/nova/../escape", Uri.fromFile(restored)), "unsafe asset path rejected");
            check(!manifest.toString().contains("keyUri") && !manifest.toString().contains("remoteUser"), "snapshot excludes host configuration and credentials");
            testActivitySelections(context, nova, image);
            if (arguments != null && arguments.containsKey("remoteFolder")) testTransfer(context, archive);
            result.putString("stream", "\n" + results + "ALL RELEASE CHECKS PASSED\n"); finish(Activity.RESULT_OK, result);
        } catch (Throwable e) { result.putString("stream", "\n" + results + "FAILED: " + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }

    private void testActivitySelections(Context context, File nova, File image) throws Exception {
        android.content.Intent intent = new android.content.Intent(context, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity first = (MainActivity) startActivitySync(intent);
        runOnMainSync(() -> {
            first.onActivityResult(1, Activity.RESULT_OK, new android.content.Intent().setData(Uri.fromFile(nova)));
            first.onActivityResult(2, Activity.RESULT_OK, new android.content.Intent().setData(Uri.fromFile(image)));
            first.onActivityResult(3, Activity.RESULT_OK, new android.content.Intent().setData(Uri.fromFile(image)));
            first.finish();
        });
        waitForIdleSync();
        MainActivity reopened = (MainActivity) startActivitySync(intent);
        java.lang.reflect.Field selected = MainActivity.class.getDeclaredField("novaUri"); selected.setAccessible(true);
        check(Uri.fromFile(nova).equals(selected.get(reopened)), "Nova selection survives Activity restart");
        selected = MainActivity.class.getDeclaredField("wallpaperUri"); selected.setAccessible(true);
        check(Uri.fromFile(image).equals(selected.get(reopened)), "wallpaper selection survives Activity restart");
        selected = MainActivity.class.getDeclaredField("screenshots"); selected.setAccessible(true);
        check(((java.util.List<?>)selected.get(reopened)).size() == 1, "screenshot selection survives Activity restart");
        runOnMainSync(reopened::finish); waitForIdleSync();
    }

    private void testTransfer(Context context, File archive) throws Exception {
        File folder = context.getExternalFilesDir(null);
        String key = Uri.fromFile(new File(folder, "client-key")).toString();
        String hosts = Uri.fromFile(new File(folder, "known_hosts")).toString();
        SftpTransfer.Destination destination = new SftpTransfer.Destination("10.0.2.2", 22222, arguments.getString("user", "test"), arguments.getString("remoteFolder"), key, hosts);
        new SftpTransfer().upload(context, archive, destination, null, (sent,total) -> {});
        check(true, "SFTP authenticated upload and remote size check");
        File badHosts = fixture(context, "wrong-known-hosts", "");
        SftpTransfer.Destination bad = new SftpTransfer.Destination(destination.host, destination.port, destination.user, destination.folder, key, Uri.fromFile(badHosts).toString());
        rejects(() -> new SftpTransfer().upload(context, archive, bad, null, (sent,total) -> {}), "unknown SSH host key rejected");
        SftpTransfer cancelled = new SftpTransfer(); cancelled.cancel();
        rejects(() -> cancelled.upload(context, archive, destination, null, (sent,total) -> {}), "cancelled transfer does not start");
        check(WifiUploadJob.schedule(context, archive, destination, true), "unmetered export scheduled");
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo job = scheduler.getPendingJob(WifiUploadJob.JOB_ID);
        check(job != null && job.isPersisted() && job.getNetworkType() == JobInfo.NETWORK_TYPE_UNMETERED, "queue persists and requires an unmetered network");
        context.getSharedPreferences("pixi_snapshot", Context.MODE_PRIVATE).edit().putString("host", "changed.invalid").commit();
        check(job.getExtras().getString("host").equals(destination.host), "queued destination is frozen when preferences change");
        try (android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand("cmd jobscheduler run -f org.nullprotocol.pixisnapshot " + WifiUploadJob.JOB_ID);
             java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { byte[] buffer = new byte[1024]; while(in.read(buffer) != -1) {} }
        long limit = android.os.SystemClock.elapsedRealtime()+30000;
        String status = "";
        while (android.os.SystemClock.elapsedRealtime() < limit) {
            status = context.getSharedPreferences("pixi_snapshot", Context.MODE_PRIVATE).getString("lastUpload", "");
            if (status.startsWith("Upload complete") || status.startsWith("Host upload failed")) break;
            Thread.sleep(250);
        }
        check(status.startsWith("Upload complete"), "background JobService transfer completes: " + status);
        // Let the main-thread jobFinished callback remove the completed job before cancellation check.
        Thread.sleep(500);
        scheduler.cancel(WifiUploadJob.JOB_ID);
        check(WifiUploadJob.schedule(context, archive, destination, true), "new export can be queued");
        WifiUploadJob.cancel(context);
        check(scheduler.getPendingJob(WifiUploadJob.JOB_ID) == null, "queued export cancellation removes the job");
    }
    private interface Action { void run() throws Exception; }
    private void rejects(Action action, String name) throws Exception { boolean rejected=false; try { action.run(); } catch (Exception expected) { rejected=true; } check(rejected, name); }
    private void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); results.append("PASS: ").append(name).append('\n'); }
    private File fixture(Context c, String name, String text) throws Exception { File file=new File(c.getCacheDir(),name); Files.write(file.toPath(),text.getBytes(StandardCharsets.UTF_8)); return file; }
    private File zip(Context c, String name, String text) throws Exception { File file=new File(c.getCacheDir(),name); try (ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))) { out.putNextEntry(new ZipEntry("snapshot.json")); out.write(text.getBytes(StandardCharsets.UTF_8)); out.closeEntry(); } return file; }
}
