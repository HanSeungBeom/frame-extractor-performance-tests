package bumbums.frameextraction.extractor;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.view.TextureView;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import bumbums.frameextraction.encoder.GifEncoder;
import bumbums.frameextraction.utilities.RealPathUtil;
import bumbums.frameextraction.utilities.Utils;

import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_END;
import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_FPS;
import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_START;
import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_VIDEO_URI;


/**
 * Created by hanseungbeom on 2018. 5. 5..
 */

/* GIF extractor using MediaCodec */

public class GifExtractorMC extends AsyncTaskLoader<Uri> {

    private static final String TAG = GifExtractorMC.class.getSimpleName();

    private static final String VIDEO = "video/";
    private static final int TIME_OUT_US = -1;

    //textureview
    private TextureView mTextureView;

    //context
    private Context mContext;

    //data for AsyncTaskLoader
    private Bundle mExtractInfo;

    //callback
    private ExtractHandler mHandler;

    //about gif encoder
    private GifEncoder mGifEncoder;
    private ArrayList<Long> mFramePos;
    private ByteArrayOutputStream mBaos;
    private ArrayList<Bitmap> mExtractedFrameList;

    //about mediacodec
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;

    public GifExtractorMC(Context context, Bundle extractInfo, TextureView textureView) {
        super(context);
        mContext = context;
        mHandler = (ExtractHandler) context;
        mExtractInfo = extractInfo;
        mTextureView = textureView;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        //get extractInfo from bundle
        Uri videoUri = Uri.parse(mExtractInfo.getString(GIF_EXTRACT_VIDEO_URI));
        long startPos = mExtractInfo.getLong(GIF_EXTRACT_START);
        long endPos = mExtractInfo.getLong(GIF_EXTRACT_END);
        int fps = mExtractInfo.getInt(GIF_EXTRACT_FPS);

        try {
            ready(mContext, videoUri, startPos, endPos, fps);
            forceLoad();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public Uri loadInBackground() {
        return extract();
    }

    public void ready(Context context, Uri videoUri, long startPos, long endPos, int fps) throws Exception {
        Log.d(TAG, "ready..");

        /* setting MediaCodec */
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(RealPathUtil.getRealPath(context, videoUri));

        /* get viedo track */
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(VIDEO)) {
                mExtractor.selectTrack(i);
                mDecoder = MediaCodec.createDecoderByType(mime);
                try {
                    Log.d(TAG, "format : " + format);
                    mDecoder.configure(format, null, null, 0);


                } catch (IllegalStateException e) {
                    Log.e(TAG, "codec '" + mime + "' failed configuration. " + e);
                }
                mDecoder.start();
                break;
            }
        }


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
        Log.d(TAG, "initEncoder..");

        //about encoder
        mGifEncoder = new GifEncoder();
        mBaos = new ByteArrayOutputStream();
        mGifEncoder.start(mBaos);
        mGifEncoder.setDelay(Utils.getDelayOfFrame(fps));
    }

    public Uri extract() {
        Log.d(TAG, "startExtract..");

        mExtractedFrameList = new ArrayList<>();

        for (int i = 0; i < mFramePos.size(); i++) {
            Log.d(TAG, "extractFrame..(" + (i + 1) + "/" + mFramePos.size() + ")");
            Long nextPos = mFramePos.get(i);
            Bitmap bitmap = getFrame(nextPos);
            mHandler.onFrameExtracted(bitmap);
            mExtractedFrameList.add(bitmap);
        }

        for (Bitmap frame : mExtractedFrameList)
            mGifEncoder.addFrame(frame);

        Log.d(TAG, "encodeGif..");

        mDecoder.stop();
        mDecoder.release();
        mExtractor.release();

        mGifEncoder.finish();

        return Utils.makeGifFile(mBaos);
    }

    public Bitmap getFrame(long positionMs) {
        Bitmap frame = null;

        int inputIndex = mDecoder.dequeueInputBuffer(TIME_OUT_US);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();


        /* put data to inputBuffer using MediaExtractor */
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputIndex);
            //TODO it have to change because it seek to only key frame near positionMs.
            mExtractor.seekTo(Utils.msToUs(positionMs), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            //mExtractor.advance();
            int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

            if (sampleSize > 0) {
                mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
            }
        }

        while (true) {
            /* get data from outputBuffer */
            int outIndex = mDecoder.dequeueOutputBuffer(info, TIME_OUT_US);
            if (outIndex >= 0) {
                ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outIndex);
                MediaFormat format = mDecoder.getOutputFormat(outIndex); //
                frame = Utils.outputBufferToFrame(outputBuffer, format);
                mDecoder.releaseOutputBuffer(outIndex, false);
                return frame;
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mDecoder.getOutputFormat();
                continue;
            }

        }

    }

}
