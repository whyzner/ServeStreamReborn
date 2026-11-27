package net.sourceforge.servestream.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some broadcasters hand out their stream through an API address that answers
 * with a short text (or JSON) holding the real stream address, often with a
 * token that expires after a few hours, e.g.
 * apis.sbs.co.kr/play-api/1.0/livestream/powerfm/powerfm?protocol=hls&ssl=Y
 * answers "https://radiolive.sbs.co.kr/powerfm/powerfm.stream/playlist.m3u8?token=...".
 *
 * The station keeps the API address; the stream address is looked up again
 * every time the station is opened (and on every reconnect), so an expired
 * token never gets stored.
 */
public final class StreamPointer {

    /** Replies longer than this are not pointers. */
    private static final int MAX_BODY = 16 * 1024;

    private static final Pattern SINGLE_URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern URL_IN_TEXT = Pattern.compile("(?i)https?://[^\"'\\s<>\\\\]+");
    private static final Pattern MEDIA_URL = Pattern.compile(
            "(?i).*\\.(m3u8|m3u|pls|mp3|aac|m4a|ogg|opus|flac)([?#].*)?$");

    private StreamPointer() {
    }

    /** Text and JSON replies may be pointers; audio and playlist types never are. */
    public static boolean isPointerType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.US);
        if (ct.contains("mpegurl") || ct.contains("scpls") || ct.contains("audio/") || ct.contains("video/")) {
            return false;
        }
        // some APIs (SBS) label the one-line reply as a web page (text/html);
        // a real page starts with "<" and is never taken for a pointer
        return ct.contains("text/plain") || ct.contains("text/html")
                || ct.contains("json") || ct.contains("javascript");
    }

    /**
     * The stream address inside a pointer reply, or null if the reply is
     * something else (an M3U/HLS/PLS playlist, a web page, a longer text).
     */
    public static String parse(String body) {
        if (body == null) {
            return null;
        }
        String t = body.replace("\uFEFF", "").trim();
        if (t.length() == 0 || t.length() > MAX_BODY) {
            return null;
        }
        // a real playlist: leave it to the playlist parser
        if (t.startsWith("#") || t.toLowerCase(Locale.US).contains("[playlist]") || t.startsWith("<")) {
            return null;
        }
        // a single address (optionally in quotes)
        String single = t;
        if (single.length() > 1 && single.startsWith("\"") && single.endsWith("\"")) {
            single = single.substring(1, single.length() - 1).replace("\\/", "/");
        }
        if (SINGLE_URL.matcher(single).matches()) {
            return single;
        }
        // JSON: prefer an address that looks like a stream, else the first one
        if (t.startsWith("{") || t.startsWith("[")) {
            Matcher m = URL_IN_TEXT.matcher(t.replace("\\/", "/"));
            String first = null;
            while (m.find()) {
                String u = m.group();
                if (first == null) {
                    first = u;
                }
                if (MEDIA_URL.matcher(u).matches()) {
                    return u;
                }
            }
            return first;
        }
        return null;
    }

    /** Result of {@link #peek}: the pointed-to address (or null), whether the reply is HLS, and the reply, still readable. */
    public static final class Peek {
        public final String target;
        /** An HLS playlist (live radio): play the address as one stream, never as a list of its segments. */
        public final boolean hls;
        public final InputStream rest;

        Peek(String target, boolean hls, InputStream rest) {
            this.target = target;
            this.hls = hls;
            this.rest = rest;
        }

        /** The address itself should be stored and played as one stream. */
        public boolean playAsOne() {
            return target != null || hls;
        }
    }

    /** HLS playlists carry #EXT-X- tags (an ordinary M3U never does). */
    public static boolean isHls(String body) {
        return body != null && body.contains("#EXT-X-");
    }

    private static boolean mayBePlaylistText(String contentType) {
        if (contentType == null) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.US);
        return isPointerType(ct) || ct.contains("mpegurl") || ct.contains("octet-stream");
    }

    /**
     * Looks at the start of a reply. When it is a pointer, {@code target} is the
     * stream address; when it is an HLS playlist, {@code hls} is set; either way
     * {@code rest} still yields the whole reply.
     */
    public static Peek peek(String contentType, InputStream is) {
        if (is == null || !mayBePlaylistText(contentType)) {
            return new Peek(null, false, is);
        }
        try {
            byte[] head = readUpTo(is, MAX_BODY + 1);
            InputStream rest = new SequenceInputStream(new ByteArrayInputStream(head), is);
            String text = new String(head, "UTF-8");
            boolean hls = isHls(text);
            if (hls || head.length > MAX_BODY || !isPointerType(contentType)) {
                return new Peek(null, hls, rest);
            }
            return new Peek(parse(text), false, rest);
        } catch (IOException e) {
            return new Peek(null, false, is);
        }
    }

    private static byte[] readUpTo(InputStream is, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while (out.size() < max && (n = is.read(buf, 0, Math.min(buf.length, max - out.size()))) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
