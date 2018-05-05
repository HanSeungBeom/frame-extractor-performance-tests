package bumbums.frameextraction.extractor;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import bumbums.frameextraction.encoder.GifEncoder;
import bumbums.frameextraction.utilities.RealPathUtil;
import bumbums.frameextraction.utilities.Utils;
import wseemann.media.FFmpegMediaMetadataRetriever;


/**
 * Created by hanseungbeom on 2018. 5. 4..
 */

/* GIF extractor using FFMpegMediaMetadataRetriever */

public class GifExtractorFMMR extends AsyncTaskLoader<Uri> {

    //log tag
    private final String TAG = GifExtractorFMMR.class.getSimpleName();

    //bundle key constants
    public static final String GIF_EXTRACT_VIDEO_URI = "gif_extract_video_uri";
    public static final String GIF_EXTRACT_START = "gif_extract_start";
    public static final String GIF_EXTRACT_END = "gif_extract_end";
    public static final String GIF_EXTRACT_FPS = "gif_extract_fps";

    //context
    private Context mContext;

    //data for AsyncTaskLoader
    private Bundle mExtractorInfo;

    //callback
    private ExtractHandler mHandler;

    //about gif encoder
    private GifEncoder mGifEncoder;
    private ArrayList<Long> mFramePos;
    private ByteArrayOutputStream mBaos;
    private ArrayList<Bitmap> mExtractedFrameList;

    //MediaMetadataRetriever
    private FFmpegMediaMetadataRetriever mFFmpecRetriever;
    //private MediaMetadataRetriever mRetriever;


    public GifExtractorFMMR(Context context, Bundle extractInfo) {
        super(context);
        mContext = context;
        mHandler = (ExtractHandler) context;
        mExtractorInfo = extractInfo;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        //get extractInfo from bundle
        Uri videoUri = Uri.parse(mExtractorInfo.getString(GIF_EXTRACT_VIDEO_URI));
        long startPos = mExtractorInfo.getLong(GIF_EXTRACT_START);
        long endPos = mExtractorInfo.getLong(GIF_EXTRACT_END);
        int fps = mExtractorInfo.getInt(GIF_EXTRACT_FPS);

        try {
            ready(mContext, videoUri, startPos, endPos, fps);
            forceLoad();
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public Uri loadInBackground() {
        Log.d(TAG,"loadInBackground..");
        return extract();
    }

    public void ready(Context context, Uri videoUri, long startPos, long endPos, int fps) throws Exception {
        Log.d(TAG,"ready..");

        //retriever
        /*
        mRetriever = new MediaMetadataRetriever();
        mRetriever.setDataSource(context, videoUri);
        */
        mFFmpecRetriever = new FFmpegMediaMetadataRetriever();
        String path = RealPathUtil.getRealPath(context,videoUri);
        mFFmpecRetriever.setDataSource(path);

        long totalTimeMs = endPos - startPos;

        //calculate total count of frames we need and delay between frames.
        int neededFrame = (fps * (Utils.getSecFromMs(totalTimeMs)));
        long addRate = totalTimeMs / neededFrame;

        mFramePos = new ArrayList<>();

        //calculate frame Position needed
        //ensure the onRenderedFirstTime() is always called
        for (long pos = startPos; pos < endPos; pos += addRate) {
            mFramePos.add(pos);
        }

        initEncoder(fps);
    }

    public void initEncoder(int fps) {
        Log.d(TAG,"initEncoder..");

        //about encoder
        mGifEncoder = new GifEncoder();
        mBaos = new ByteArrayOutputStream();
        mGifEncoder.start(mBaos);
        mGifEncoder.setDelay(Utils.getDelayOfFrame(fps));
    }

    public Uri extract() {
        Log.d(TAG,"startExtract..");

        mExtractedFrameList = new ArrayList<>();

        for (int i=0;i<mFramePos.size();i++) {
            Log.d(TAG,"extractFrame..("+(i+1)+"/"+mFramePos.size()+")");
            Long nextPos = mFramePos.get(i);
            //Bitmap bitmap = mRetriever.getFrameAtTime(C.msToUs(nextPos), MediaMetadataRetriever.OPTION_CLOSEST);
            Bitmap bitmap = mFFmpecRetriever.getFrameAtTime(Utils.msToUs(nextPos), FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
            mHandler.onFrameExtracted(bitmap);
            mExtractedFrameList.add(bitmap);
        }

        for (Bitmap frame : mExtractedFrameList)
            mGifEncoder.addFrame(frame);

        Log.d(TAG,"encodeGif..");
        mFFmpecRetriever.release();
        //mRetriever.release();
        mGifEncoder.finish();

        return Utils.makeGifFile(mBaos);
    }


}
