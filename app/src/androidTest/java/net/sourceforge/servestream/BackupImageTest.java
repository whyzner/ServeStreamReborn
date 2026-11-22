package net.sourceforge.servestream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.BackupUtils;
import net.sourceforge.servestream.utils.StationImages;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Backup file round trip with station images, through a real file like the system file picker. */
@RunWith(AndroidJUnit4.class)
public class BackupImageTest {
    private static final String STREAM = "https://stream1.rcast.net/73730?backuptest=1";

    private Context ctx;
    private UriBean station;
    private File backup;

    @Before
    public void setUp() throws Exception {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        station = ensureStation();
        // a big photo-like picture, like one picked from the gallery (saved at up to 1024 px)
        Bitmap bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint();
        for (int i = 0; i < 64; i++) {
            p.setColor(Color.rgb(i * 4, 255 - i * 4, (i * 37) % 256));
            canvas.drawCircle((i * 97) % 1024, (i * 53) % 1024, 40 + i * 3, p);
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
        StationImages.delete(ctx, station.getId());
        assertTrue(StationImages.saveBytes(ctx, station.getId(), jpeg.toByteArray()));

        backup = new File(ctx.getCacheDir(), "servestream-backup.json");
        FileOutputStream out = new FileOutputStream(backup);
        BackupUtils.Counts written = BackupUtils.writeBackup(ctx, out);
        out.close();
        assertTrue("no image written to the backup", written.images >= 1);
        assertTrue("backup file too small: " + backup.length(), backup.length() > 5000);
    }

    private UriBean ensureStation() {
        StreamDatabase db = new StreamDatabase(ctx);
        UriBean b = TransportFactory.findUri(db, Uri.parse(STREAM));
        if (b == null) {
            b = TransportFactory.getTransport("https").createUri(Uri.parse(STREAM));
            b.setNickname("Backup Test Radio");
            b = db.saveUri(b);
        }
        db.close();
        return b;
    }

    private BackupUtils.Counts restore() throws Exception {
        InputStream in = ctx.getContentResolver().openInputStream(Uri.fromFile(backup));
        try {
            return BackupUtils.readBackup(ctx, in);
        } finally {
            in.close();
        }
    }

    @Test
    public void imageComesBackAfterRemovingIt() throws Exception {
        StationImages.delete(ctx, station.getId());
        assertFalse(StationImages.has(ctx, station.getId()));
        BackupUtils.Counts c = restore();
        assertTrue("restore did not bring the image back (in file: " + c.imagesInFile + ")", c.images >= 1);
        assertNotNull(StationImages.load(ctx, station.getId()));
    }

    @Test
    public void imageComesBackWithDeletedStation() throws Exception {
        StreamDatabase db = new StreamDatabase(ctx);
        db.deleteUri(station);
        db.close();
        StationImages.delete(ctx, station.getId());
        BackupUtils.Counts c = restore();
        assertTrue(c.stations >= 1);
        UriBean back = ensureStation();
        assertTrue("restored station has no image (restored " + c.images + " of " + c.imagesInFile + ")",
                StationImages.has(ctx, back.getId()));
        assertNotNull(StationImages.load(ctx, back.getId()));
        assertEquals(1024, StationImages.load(ctx, back.getId()).getWidth());
    }

    /** Bulk delete used to leave the picture file behind; a restored station reusing the number skipped its image. */
    @Test
    public void imageComesBackWhenALeftoverFileExists() throws Exception {
        StreamDatabase db = new StreamDatabase(ctx);
        db.deleteUri(station);
        db.close();
        // leave a different picture under the old number, as older versions did
        Bitmap stale = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        stale.eraseColor(Color.RED);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        stale.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
        long oldId = station.getId();
        assertTrue(StationImages.saveBytes(ctx, oldId, jpeg.toByteArray()));
        // ... and make the restored station get that very number
        long maxId = 0;
        db = new StreamDatabase(ctx);
        for (UriBean b : db.getUris()) maxId = Math.max(maxId, b.getId());
        db.close();
        for (long id = oldId; id > maxId; id--) {
            if (id != oldId) StationImages.delete(ctx, id);
        }

        BackupUtils.Counts c = restore();
        UriBean back = ensureStation();
        assertTrue("restored " + c.images + " of " + c.imagesInFile, c.images >= 1);
        assertEquals("restored station shows a leftover picture", 1024,
                StationImages.load(ctx, back.getId()).getWidth());
    }

    @Test
    public void leftoverPicturesAreCleanedUp() throws Exception {
        long ghost = 987654;
        Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
        assertTrue(StationImages.saveBytes(ctx, ghost, jpeg.toByteArray()));
        StationImages.removeOrphans(ctx);
        assertFalse(StationImages.has(ctx, ghost));
        assertTrue("a saved station's picture was removed", StationImages.has(ctx, station.getId()));
    }

    /** Like the user's case: ten stations with gallery pictures, fresh phone, restore all. */
    @Test
    public void tenStationsAllImagesComeBack() throws Exception {
        StreamDatabase db = new StreamDatabase(ctx);
        java.util.List<UriBean> made = new java.util.ArrayList<UriBean>();
        for (int k = 0; k < 10; k++) {
            Uri u = Uri.parse("https://example" + k + ".invalid/stream?n=" + k);
            UriBean b = TransportFactory.findUri(db, u);
            if (b == null) {
                b = TransportFactory.getTransport("https").createUri(u);
                b.setNickname("Ten Test " + k);
                b = db.saveUri(b);
            }
            made.add(b);
            // a logo-like PNG picked from the gallery, saved through the normal picker path
            Bitmap logo = Bitmap.createBitmap(300 + k * 150, 300 + k * 150, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(logo);
            c.drawColor(Color.WHITE);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(20 * k, 100, 255 - 20 * k));
            c.drawCircle(logo.getWidth() / 2f, logo.getHeight() / 2f, logo.getWidth() / 3f, p);
            File png = new File(ctx.getCacheDir(), "logo" + k + ".png");
            FileOutputStream fo = new FileOutputStream(png);
            logo.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            assertTrue(StationImages.save(ctx, b.getId(), Uri.fromFile(png)));
        }
        db.close();

        File file = new File(ctx.getCacheDir(), "ten.json");
        FileOutputStream out = new FileOutputStream(file);
        BackupUtils.Counts w = BackupUtils.writeBackup(ctx, out);
        out.close();

        // "reinstall": remove these stations and pictures
        db = new StreamDatabase(ctx);
        for (UriBean b : made) {
            db.deleteUri(b);
            StationImages.delete(ctx, b.getId());
        }
        db.close();

        InputStream in = ctx.getContentResolver().openInputStream(Uri.fromFile(file));
        BackupUtils.Counts r = BackupUtils.readBackup(ctx, in);
        in.close();
        int ok = 0;
        StringBuilder missing = new StringBuilder();
        db = new StreamDatabase(ctx);
        for (int k = 0; k < 10; k++) {
            UriBean b = TransportFactory.findUri(db, Uri.parse("https://example" + k + ".invalid/stream?n=" + k));
            if (b != null && StationImages.has(ctx, b.getId())) ok++; else missing.append(k).append(' ');
        }
        db.close();
        assertEquals("written " + w.images + ", in file " + r.imagesInFile + ", restored " + r.images
                + ", missing " + missing, 10, ok);
    }
}
