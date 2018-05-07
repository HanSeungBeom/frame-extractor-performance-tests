package bumbums.frameextraction.utilities;


import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.MediaStore;

import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.google.android.exoplayer2.C.msToUs;

/**
 * Created by hanseungbeom on 2018. 4. 27..
 */

public class Utils {

    /* get ClippingMediaSource from startPos to endPos */
    public static MediaSource getExtractedVideo(MediaSource source, long startPos, long endPos) {
        return new ClippingMediaSource(source, msToUs(startPos), msToUs(endPos));
    }

    /* get default trackSelector */
    public static TrackSelector getDefaultTrackSelector(){
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        return trackSelector;
    }

    /* refresh gallery */
    public static void addImageToGallery(Context context, Uri gifUri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/gif");
        values.put(MediaStore.MediaColumns.DATA, gifUri.getPath());
        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    /* gif sharing */
    public static void shareGif(Context context, Uri gifUri) {
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("image/gif");
        shareIntent.putExtra(Intent.EXTRA_STREAM, gifUri);
        context.startActivity(Intent.createChooser(shareIntent, "Share image"));
    }

    /* save gif to file and return uri */
    public static Uri makeGifFile(ByteArrayOutputStream baos) {
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File(root.getAbsolutePath() + "/gif");
        if (!dir.exists())
            dir.mkdir();
        File outFile = new File(dir, "output.gif");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outFile);
            // Put data in your baos
            baos.writeTo(fos);
        } catch (IOException ioe) {
            // Handle exception here
            ioe.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return Uri.fromFile(outFile);
    }

    public static double getSecFromMs(long milliseconds){
        return  ((double)milliseconds / 1000) ;
    }

    public static int getDelayOfFrame(int fps){
        return (int) (((double) 1 / fps) * 1000);
    }

    public static long msToUs(long timeMs) {
        return (timeMs * 1000);
    }

    public static int[] decodeYUV420SP( byte[] yuv420sp, int width, int height) {

        final int frameSize = width * height;

        int rgb[]=new int[width*height];
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) &
                        0xff00) | ((b >> 10) & 0xff);


            }
        }
        return rgb;
    }

    public static Bitmap outputBufferToFrame(ByteBuffer outputBuffer, MediaFormat format){
        byte[] byteArray = new byte[outputBuffer.remaining()];
        outputBuffer.get(byteArray);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        int[] rgbArray = Utils.decodeYUV420SP(byteArray, width, height);
        return Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888);
    }

}