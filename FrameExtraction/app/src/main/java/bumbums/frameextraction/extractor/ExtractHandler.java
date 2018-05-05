package bumbums.frameextraction.extractor;

/**
 * Created by hanseungbeom on 2018. 5. 5..
 */


import android.graphics.Bitmap;
import android.net.Uri;

public interface ExtractHandler{
    /* This method is called after creating gif file. */
    void onExtractionFinished(Uri gifUri);
    /* This method is called whenever extract frame from video */
    void onFrameExtracted(Bitmap frame);
}
