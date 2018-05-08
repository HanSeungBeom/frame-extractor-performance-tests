package bumbums.frameextraction.extractor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.view.Surface;
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
    private static final int TIME_OUT_US = 10000;

    //textureview for capture frame
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

    //for pick range
    private long mAddRate;

    //gif Uri
    private Uri mResult;

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

        //prevent reload
        if (mResult != null) {
            deliverResult(mResult);
            return;
        }
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
        String path = RealPathUtil.getRealPath(context, videoUri);
        mExtractor.setDataSource(path);

        /* get viedo track */
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(VIDEO)) {
                mExtractor.selectTrack(i);
                mDecoder = MediaCodec.createDecoderByType(mime);
                try {
                    Log.d(TAG, "format : " + format);
                    SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
                    Surface surface = new Surface(surfaceTexture);
                    mDecoder.configure(format, surface, null, 0);

                } catch (IllegalStateException e) {
                    Log.e(TAG, "codec '" + mime + "' failed configuration. " + e);
                }
                mDecoder.start();
                break;
            }
        }


        long totalTimeMs = endPos - startPos;

        //calculate total count of frames we need and delay between frames.
        int neededFrame = (int) (fps * (Utils.getSecFromMs(totalTimeMs)));
        long addRate = totalTimeMs / neededFrame;
        mAddRate = addRate;

        mFramePos = new ArrayList<>();

        //calculate frame Position needed
        for (long pos = startPos; pos < endPos; pos += addRate) {
            mFramePos.add(pos);
        }

        //move to the start position.
        mExtractor.seekTo(startPos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

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

        //extract with mediacodec
        mExtractedFrameList = getExtractedFrames();

        //encode bitmap frames
        Log.d(TAG, "encodeGif..");
        for (Bitmap frame : mExtractedFrameList)
            mGifEncoder.addFrame(frame);

        mGifEncoder.finish();
        Log.d(TAG, "encodeGif Finished..");

        mDecoder.stop();
        mDecoder.release();
        mExtractor.release();

        mResult = Utils.makeGifFile(mBaos);

        return mResult;
    }

    public ArrayList<Bitmap> getExtractedFrames() {
        ArrayList<Bitmap> extractedFrames = new ArrayList<>();

        int nextPosPointer = 0;
        boolean isInput = true;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();


        while (nextPosPointer < mFramePos.size()) {
            if (isInput) {
                int inputIndex = mDecoder.dequeueInputBuffer(TIME_OUT_US);

                /* put data to inputBuffer using MediaExtractor */
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputIndex);

                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                    if (mExtractor.advance() && sampleSize > 0) {
                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);

                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput = false;
                    }
                }
            }

            /* get data from outputBuffer */
            int outIndex = mDecoder.dequeueOutputBuffer(info, TIME_OUT_US);
            if (outIndex >= 0) {

                long presentationTimeUs = info.presentationTimeUs;
                //Log.d(TAG, "presentationTimeUs:" + String.valueOf(presentationTimeUs));

                //break when presentationTimeUs > end Position
                if (presentationTimeUs > Utils.msToUs(mExtractInfo.getLong(GIF_EXTRACT_END))) {
                    break;
                }

                //ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outIndex);
                //MediaFormat format = mDecoder.getOutputFormat(outIndex);
                mDecoder.releaseOutputBuffer(outIndex, true);
                try {
                    long nextPos = mFramePos.get(nextPosPointer);
                    if (isRange(nextPos, presentationTimeUs)) {
                        Log.d(TAG, "extractFrame..(" + (nextPosPointer + 1) + "/" + mFramePos.size() + ")");

                        Bitmap frame = mTextureView.getBitmap();
                        //mHandler.onFrameExtracted(frame);
                        extractedFrames.add(frame);
                        nextPosPointer++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }


            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mDecoder.getOutputFormat();
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }

        return extractedFrames;

    }

    public boolean isRange(long nextPos, long presentationTimeUs) {
        long presentationTime = presentationTimeUs / 1000;
        return ((nextPos - (double) mAddRate * 0.75) <= presentationTime) &&
                (presentationTime < (nextPos + (double) mAddRate * 0.75));
    }


}
