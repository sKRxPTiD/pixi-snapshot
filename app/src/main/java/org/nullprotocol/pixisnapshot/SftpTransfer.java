package org.nullprotocol.pixisnapshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.net.Uri;
import android.os.PersistableBundle;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

final class SftpTransfer {
    interface Progress { void update(long bytes, long total); }
    private volatile boolean cancelled;
    private volatile Session activeSession;

    static final class Destination {
        final String host, user, folder, keyUri, knownHostsUri;
        final int port;
        Destination(String host, int port, String user, String folder, String keyUri, String knownHostsUri) throws Exception {
            this.host = host.trim(); this.port = port; this.user = user.trim(); this.folder = folder.trim();
            this.keyUri = keyUri; this.knownHostsUri = knownHostsUri;
            if (this.host.isEmpty() || this.user.isEmpty() || this.folder.isEmpty() || keyUri == null || knownHostsUri == null)
                throw new Exception("Choose a host, username, remote folder, SSH key, and known_hosts file first.");
            if (port < 1 || port > 65535) throw new Exception("SSH port must be between 1 and 65535.");
        }
        static Destination fromPreferences(SharedPreferences p) throws Exception {
            return new Destination(p.getString("host", ""), p.getInt("port", 22), p.getString("remoteUser", ""),
                    p.getString("remoteFolder", ""), p.getString("keyUri", null), p.getString("knownHostsUri", null));
        }
        void writeTo(PersistableBundle b) {
            b.putString("host", host); b.putInt("port", port); b.putString("user", user); b.putString("folder", folder);
            b.putString("keyUri", keyUri); b.putString("knownHostsUri", knownHostsUri);
        }
        static Destination fromBundle(PersistableBundle b) throws Exception {
            return new Destination(b.getString("host", ""), b.getInt("port", 22), b.getString("user", ""),
                    b.getString("folder", ""), b.getString("keyUri"), b.getString("knownHostsUri"));
        }
    }

    void cancel() { cancelled = true; Session session = activeSession; if (session != null) session.disconnect(); }

    void upload(Context context, File archive, Destination destination, Network network, Progress progress) throws Exception {
        if (!archive.isFile()) throw new Exception("The selected snapshot is missing.");
        if (cancelled) throw new InterruptedException("Upload cancelled.");
        byte[] key = null;
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftp = null;
        String partial = null;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(destination.knownHostsUri))) {
                jsch.setKnownHosts(new java.io.ByteArrayInputStream(readLimited(in)));
            }
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(destination.keyUri))) { key = readLimited(in); }
            jsch.addIdentity("pixi-selected-key", key, null, null);
            session = jsch.getSession(destination.user, destination.host, destination.port);
            activeSession = session;
            if (cancelled) throw new InterruptedException("Upload cancelled.");
            if (network != null) session.setSocketFactory(new com.jcraft.jsch.SocketFactory() {
                public Socket createSocket(String host, int port) throws java.io.IOException {
                    Socket socket = network.getSocketFactory().createSocket();
                    try { socket.connect(new InetSocketAddress(network.getByName(host), port), 20000); return socket; }
                    catch (java.io.IOException e) { socket.close(); throw e; }
                }
                public InputStream getInputStream(Socket socket) throws java.io.IOException { return socket.getInputStream(); }
                public OutputStream getOutputStream(Socket socket) throws java.io.IOException { return socket.getOutputStream(); }
            });
            Properties config = new Properties(); config.put("StrictHostKeyChecking", "yes"); config.put("PreferredAuthentications", "publickey"); session.setConfig(config);
            session.setTimeout(15000); session.connect(20000);
            if (cancelled) throw new InterruptedException("Upload cancelled.");
            sftp = (ChannelSftp) session.openChannel("sftp"); sftp.connect(15000);
            String target = destination.folder + (destination.folder.endsWith("/") ? "" : "/") + archive.getName();
            partial = target + ".partial-" + UUID.randomUUID();
            final long[] sent = {0};
            sftp.put(archive.getAbsolutePath(), partial, new SftpProgressMonitor() {
                public void init(int operation, String source, String target, long total) { progress.update(0, archive.length()); }
                public boolean count(long count) { sent[0] += count; progress.update(sent[0], archive.length()); return !cancelled; }
                public void end() {}
            }, ChannelSftp.OVERWRITE);
            if (cancelled) throw new InterruptedException("Upload cancelled.");
            if (sftp.stat(partial).getSize() != archive.length()) throw new Exception("Uploaded file size does not match the local snapshot.");
            sftp.rename(partial, target); partial = null;
        } finally {
            if (sftp != null) {
                if (partial != null && sftp.isConnected()) try { sftp.rm(partial); } catch (Exception ignored) {}
                sftp.disconnect();
            }
            if (session != null) session.disconnect(); activeSession = null;
            jsch.removeAllIdentity(); if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] readLimited(InputStream in) throws Exception {
        if (in == null) throw new Exception("Could not read one of the selected SSH files.");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > 1024 * 1024) throw new Exception("Selected SSH file is too large (maximum 1 MiB).");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
