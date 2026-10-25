package org.nullprotocol.pixisnapshot;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PersistableBundle;
import java.io.File;

public class WifiUploadJob extends JobService {
    static final int JOB_ID = 61531;
    private volatile Task running;
    private static final class Task { final SftpTransfer transfer = new SftpTransfer(); volatile boolean stopped; }

    static boolean schedule(Context context, File file, SftpTransfer.Destination destination, boolean unmetered) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler.getPendingJob(JOB_ID) != null) return false;
        PersistableBundle extras = new PersistableBundle(); destination.writeTo(extras); extras.putString("file", file.getAbsolutePath());
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, WifiUploadJob.class))
                .setExtras(extras).setRequiredNetworkType(unmetered ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true).build();
        SharedPreferences p = context.getSharedPreferences("pixi_snapshot", MODE_PRIVATE);
        p.edit().putString("lastUpload", "Queued · " + file.getName() + " → " + destination.host + (unmetered ? " · waiting for unmetered Wi-Fi" : " · waiting for network"))
                .putString("pendingFile", file.getAbsolutePath()).commit();
        if (scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) return true;
        p.edit().remove("pendingFile").putString("lastUpload", "Could not schedule the host export.").apply(); return false;
    }

    static void cancel(Context context) {
        ((JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
        context.getSharedPreferences("pixi_snapshot", MODE_PRIVATE).edit().remove("pendingFile")
                .putString("lastUpload", "Host export cancelled · local snapshot kept.").apply();
        ((NotificationManager) context.getSystemService(NOTIFICATION_SERVICE)).cancel(JOB_ID);
    }

    @Override public boolean onStartJob(JobParameters params) {
        Task task = new Task(); running = task;
        SharedPreferences p = getSharedPreferences("pixi_snapshot", MODE_PRIVATE);
        new Thread(() -> {
            try {
                File file = new File(params.getExtras().getString("file", ""));
                File allowed = new File(getFilesDir(), "snapshots").getCanonicalFile();
                if (!allowed.equals(file.getCanonicalFile().getParentFile())) throw new Exception("Invalid snapshot location.");
                SftpTransfer.Destination destination = SftpTransfer.Destination.fromBundle(params.getExtras());
                p.edit().putString("lastUpload", "Sending " + file.getName() + " → " + destination.host).apply();
                notifyUser("Sending snapshot", file.getName() + " → " + destination.host, -1, false);
                final long[] lastUpdate = {0};
                task.transfer.upload(this, file, destination, Build.VERSION.SDK_INT >= 28 ? params.getNetwork() : null, (sent, total) -> {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (!task.stopped && now - lastUpdate[0] > 1000) {
                        lastUpdate[0] = now; int percent = (int) Math.min(100, total == 0 ? 0 : sent * 100 / total);
                        p.edit().putString("lastUpload", "Sending · " + percent + "% → " + destination.host).apply();
                        notifyUser("Sending snapshot", file.getName(), percent, false);
                    }
                });
                if (!task.stopped) {
                    p.edit().remove("pendingFile").putString("lastUpload", "Upload complete · " + file.getName() + " → " + destination.host).commit();
                    notifyUser("Snapshot uploaded", "Saved to " + destination.host + ". Local copy kept.", 100, true);
                }
            } catch (Exception e) {
                if (!task.stopped) {
                    String message = e.getMessage() == null ? "Check the host and SSH settings." : e.getMessage();
                    p.edit().remove("pendingFile").putString("lastUpload", "Host upload failed · " + message).commit();
                    notifyUser("Snapshot upload failed", message + " · local snapshot kept", -1, true);
                }
            } finally {
                if (!task.stopped) new android.os.Handler(getMainLooper()).post(() -> { if (running == task) { running = null; jobFinished(params, false); } });
            }
        }, "pixi-snapshot-upload").start();
        return true;
    }

    private void notifyUser(String title, String message, int percent, boolean done) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("pixi_snapshot", "Snapshot exports", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = new Notification.Builder(this, "pixi_snapshot")
                .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(title).setContentText(message).setContentIntent(open)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setOnlyAlertOnce(true).setAutoCancel(done).setOngoing(!done);
        if (!done) notification.setProgress(100, Math.max(0, percent), percent < 0);
        try { manager.notify(JOB_ID, notification.build()); } catch (SecurityException ignored) {}
    }

    @Override public boolean onStopJob(JobParameters params) {
        Task task = running;
        if (task != null) { task.stopped = true; task.transfer.cancel(); running = null; }
        SharedPreferences p = getSharedPreferences("pixi_snapshot", MODE_PRIVATE);
        if (p.contains("pendingFile")) p.edit().putString("lastUpload", "Export paused · waiting for the required network or Android scheduling.").apply();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(JOB_ID);
        return true;
    }

    @Override public void onDestroy() { Task task = running; if (task != null) { task.stopped = true; task.transfer.cancel(); } super.onDestroy(); }
}
