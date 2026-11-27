package net.sourceforge.servestream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.AutoLibrary;
import net.sourceforge.servestream.utils.StreamPointer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stations whose address is an API that answers with the real stream address
 * (SBS style: a tokenised .m3u8 that changes every 12 hours).
 */
@RunWith(AndroidJUnit4.class)
public class StreamPointerTest {
    private static final String STREAM = "https://stream1.rcast.net/73730";

    @Test
    public void recognisesPointerReplies() {
        String sbs = "https://radiolive.sbs.co.kr/powerfm/powerfm.stream/playlist.m3u8?token=eyJ0.eyJleHAi.xbK4";
        assertEquals(sbs, StreamPointer.parse(sbs + "\n"));
        assertEquals(sbs, StreamPointer.parse("\"" + sbs + "\""));
        assertEquals("https://cdn.example.com/live/a.m3u8?t=1",
                StreamPointer.parse("{\"result\":{\"logo\":\"https:\\/\\/cdn.example.com\\/logo.png\","
                        + "\"url\":\"https:\\/\\/cdn.example.com\\/live\\/a.m3u8?t=1\"}}"));
        // real playlists and pages are not pointers
        assertNull(StreamPointer.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nchunk.m3u8\n"));
        assertNull(StreamPointer.parse("[playlist]\nFile1=http://a/b\nNumberOfEntries=1\n"));
        assertNull(StreamPointer.parse("<html><a href=\"http://a/b\">x</a></html>"));
        assertNull(StreamPointer.parse("http://a/one\nhttp://a/two\n"));
        assertTrue(StreamPointer.isPointerType("text/plain;charset=UTF-8"));
        assertTrue(StreamPointer.isPointerType("application/json"));
        assertTrue(StreamPointer.isPointerType("text/html;charset=UTF-8"));
        assertNull(StreamPointer.parse("\n  <!DOCTYPE html><html><body>https://a/b</body></html>"));
        assertTrue(!StreamPointer.isPointerType("audio/x-mpegurl"));
        assertTrue(!StreamPointer.isPointerType("application/vnd.apple.mpegurl"));
        assertTrue(!StreamPointer.isPointerType("audio/mpeg"));
    }

    /** A tiny local "broadcaster API": every request answers with the stream address, labelled text/html like SBS. */
    private static final class PointerServer extends Thread {
        final ServerSocket socket;
        final AtomicInteger hits = new AtomicInteger();

        PointerServer() throws Exception {
            socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!socket.isClosed()) {
                try (Socket s = socket.accept()) {
                    InputStream in = s.getInputStream();
                    byte[] buf = new byte[4096];
                    in.read(buf);
                    hits.incrementAndGet();
                    byte[] body = (STREAM + "\n").getBytes("UTF-8");
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html;charset=UTF-8\r\n"
                            + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Test
    public void playsTheStreamAnApiPointsTo() throws Exception {
        PointerServer server = new PointerServer();
        server.start();
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String api = "http://127.0.0.1:" + server.socket.getLocalPort() + "/play-api/livestream/test?protocol=hls&ssl=Y";

        StreamDatabase db = new StreamDatabase(ctx);
        UriBean station = TransportFactory.getTransport("http").createUri(Uri.parse(api));
        station.setNickname("Pointer Test Radio");
        station = db.saveUri(station);
        db.close();

        final MediaBrowserCompat[] browser = new MediaBrowserCompat[1];
        final CountDownLatch connected = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                browser[0] = new MediaBrowserCompat(ctx, new ComponentName(ctx, MediaPlaybackService.class),
                        new MediaBrowserCompat.ConnectionCallback() {
                            @Override
                            public void onConnected() {
                                connected.countDown();
                            }
                        }, null);
                browser[0].connect();
            }
        });
        assertTrue("media browser did not connect", connected.await(30, TimeUnit.SECONDS));
        final MediaControllerCompat[] controller = new MediaControllerCompat[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                controller[0] = new MediaControllerCompat(ctx, browser[0].getSessionToken());
            }
        });
        try {
            String id = AutoLibrary.mediaIdFor(station.getId());
            controller[0].getTransportControls().playFromMediaId(id, null);
            waitPlaying(controller[0], id);
            int first = server.hits.get();
            // asked when the station was opened and again by the player right
            // before playing (the reply itself is text, so playing proves the
            // player used the address it named)
            assertTrue("the API was asked " + first + " times", first >= 2);

            // playing it again asks the API again (a new token every time)
            controller[0].getTransportControls().stop();
            SystemClock.sleep(3000);
            controller[0].getTransportControls().playFromMediaId(id, null);
            waitPlaying(controller[0], id);
            assertTrue("the API was not asked again: " + server.hits.get(), server.hits.get() > first);

            // the station keeps the API address, never the stream it pointed to
            StreamDatabase db2 = new StreamDatabase(ctx);
            UriBean saved = TransportFactory.findUri(db2, Uri.parse(api));
            db2.close();
            assertTrue("API station missing", saved != null);
            controller[0].getTransportControls().pause();
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                public void run() {
                    browser[0].disconnect();
                }
            });
            server.socket.close();
        }
    }

    /** Saves the address as a station and plays it through the service; returns the final state text. */
    private static void playAddress(String address, String name) throws Exception {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StreamDatabase db = new StreamDatabase(ctx);
        Uri u = Uri.parse(address);
        UriBean station = TransportFactory.findUri(db, u);
        if (station == null) {
            station = TransportFactory.getTransport(u.getScheme()).createUri(u);
            station.setNickname(name);
            station = db.saveUri(station);
        }
        db.close();
        final MediaBrowserCompat[] browser = new MediaBrowserCompat[1];
        final CountDownLatch connected = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                browser[0] = new MediaBrowserCompat(ctx, new ComponentName(ctx, MediaPlaybackService.class),
                        new MediaBrowserCompat.ConnectionCallback() {
                            @Override
                            public void onConnected() {
                                connected.countDown();
                            }
                        }, null);
                browser[0].connect();
            }
        });
        assertTrue("media browser did not connect", connected.await(30, TimeUnit.SECONDS));
        final MediaControllerCompat[] controller = new MediaControllerCompat[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                controller[0] = new MediaControllerCompat(ctx, browser[0].getSessionToken());
            }
        });
        try {
            String id = AutoLibrary.mediaIdFor(station.getId());
            controller[0].getTransportControls().playFromMediaId(id, null);
            waitPlaying(controller[0], id);
            SystemClock.sleep(30000); // past the live window (a few segments)
            PlaybackStateCompat s = controller[0].getPlaybackState();
            assertEquals("stopped after starting", PlaybackStateCompat.STATE_PLAYING, s == null ? -1 : s.getState());
            controller[0].getTransportControls().pause();
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                public void run() {
                    browser[0].disconnect();
                }
            });
        }
    }

    /** A plain HLS radio stream (EBS FM, reachable from abroad). */
    @Test
    public void playsHlsRadio() throws Exception {
        playAddress("https://ebsonair.ebs.co.kr/fmradiofamilypc/familypc1m/playlist.m3u8", "EBS FM test");
    }

    /** MBC answers with a tokenised HLS address as a one-line web page, like SBS (SBS itself is blocked abroad). */
    @Test
    public void playsTokenisedHlsFromAnApi() throws Exception {
        playAddress("https://sminiplay.imbc.com/aacplay.ashx?agent=webapp&channel=sfm", "MBC FM test");
    }

    private static void waitPlaying(MediaControllerCompat c, String id) {
        long end = SystemClock.elapsedRealtime() + 60000;
        String got = null;
        int state = -1;
        while (SystemClock.elapsedRealtime() < end) {
            MediaMetadataCompat meta = c.getMetadata();
            got = meta == null ? null : meta.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            PlaybackStateCompat s = c.getPlaybackState();
            state = s == null ? -1 : s.getState();
            if (id.equals(got) && state == PlaybackStateCompat.STATE_PLAYING) return;
            SystemClock.sleep(500);
        }
        fail("wanted " + id + " playing, got " + got + " in state " + state);
    }
}
