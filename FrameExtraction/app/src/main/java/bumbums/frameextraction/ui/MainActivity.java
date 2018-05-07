package bumbums.frameextraction.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import bumbums.frameextraction.R;
import bumbums.frameextraction.extractor.ExtractHandler;
import bumbums.frameextraction.extractor.GifExtractorEXO;
import bumbums.frameextraction.extractor.GifExtractorFMMR;
import bumbums.frameextraction.extractor.GifExtractorMC;
import bumbums.frameextraction.extractor.VideoDecoderActivity;
import bumbums.frameextraction.utilities.Utils;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_END;
import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_FPS;
import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_START;
import static bumbums.frameextraction.extractor.GifExtractorFMMR.GIF_EXTRACT_VIDEO_URI;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, ExtractHandler, LoaderManager.LoaderCallbacks<Uri> {

    private static final String TAG = MainActivity.class.getSimpleName();

    //Loader ID
    private static final int LOADER_EXTRACT_GIF_FMMR = 1;
    private static final int LOADER_EXTRACT_GIF_MEDIACODEC = 2;

    //OnActivityResult Constant
    private static final int REQUEST_TAKE_GALLERY_VIDEO = 1;

    //view
    private Button mPickBtn, mStartBtn, mEndBtn, mExtractBtn;
    private EditText mEtStart, mEtEnd, mEtFps;
    private ImageView mFrameView;
    private SimpleExoPlayer mPlayer;
    private TextureView mTextureView;
    private GifImageView mGifView;
    private PlayerView mPlayerView;

    //clipped video will be loaded here and this view's visibility == GONE.
    private PlayerView mExtractView;

    //when load video from gallery , it is stored here.
    private MediaSource mOrigin;

    //GIF extractor
    private GifExtractorEXO mExtractor;

    //Uri videoUri
    private Uri mPickedVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* setting UI */
        mPickBtn = (Button) findViewById(R.id.btn_pick);
        mStartBtn = (Button) findViewById(R.id.btn_start);
        mEndBtn = (Button) findViewById(R.id.btn_end);
        mExtractBtn = (Button) findViewById(R.id.btn_get_gif);
        mEtStart = (EditText) findViewById(R.id.et_start);
        mEtEnd = (EditText) findViewById(R.id.et_end);
        mEtFps = (EditText) findViewById(R.id.et_fps);
        mPlayerView = (PlayerView) findViewById(R.id.ev);
        mExtractView = (PlayerView) findViewById(R.id.ev_extract_view);
        mFrameView = (ImageView) findViewById(R.id.iv_frame);
        mTextureView = (TextureView) findViewById(R.id.tv);
        mGifView = (GifImageView) findViewById(R.id.gif_view);

        /* setting OnClickListener */
        mPickBtn.setOnClickListener(this);
        mStartBtn.setOnClickListener(this);
        mEndBtn.setOnClickListener(this);
        mExtractBtn.setOnClickListener(this);

        /* Player Init */
        mPlayer = ExoPlayerFactory.newSimpleInstance(this, Utils.getDefaultTrackSelector());
        mPlayerView.setPlayer(mPlayer);

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {

            /* Pick video from internal storage */
            case R.id.btn_pick:
                Intent intent = new Intent();
                intent.setType("video/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_TAKE_GALLERY_VIDEO);
                break;

            /* Set startPos */
            case R.id.btn_start:
                mEtStart.setText(String.valueOf(mPlayer.getCurrentPosition()));
                break;

            /* Set endPos */
            case R.id.btn_end:
                mEtEnd.setText(String.valueOf(mPlayer.getCurrentPosition()));
                break;

            /* Extract GIF */
            case R.id.btn_get_gif:
                if (mOrigin != null) {
                    //getGifWithTextureView();
                    getGif(LOADER_EXTRACT_GIF_MEDIACODEC);
                    //getGifWithMMR();

                } else {
                    Toast.makeText(this, "no video", Toast.LENGTH_SHORT).show();
                }
                break;

        }
    }

    public void getGifWithTextureView() {
        if (mExtractor != null)
            mExtractor.release();

        /* init GifExtractor */
        mExtractor = new GifExtractorEXO(this, mExtractView, mTextureView);

        /* set fps */
        int fps = Integer.parseInt(mEtFps.getText().toString());
        mExtractor.setFps(fps);

        /* cut video and run */
        long startPos = Long.parseLong(mEtStart.getText().toString());
        long endPos = Long.parseLong(mEtEnd.getText().toString());
        MediaSource extractedVideo = Utils.getExtractedVideo(mOrigin, startPos, endPos);
        mExtractor.run(extractedVideo);
    }

    public void getGif(int loaderId){
        Bundle extractInfo = getExtractBundle();
        LoaderManager loaderManager = getSupportLoaderManager();
        Loader loader = getSupportLoaderManager().getLoader(loaderId);
        if (loader == null)
            loaderManager.initLoader(loaderId, extractInfo,this );
        else
            loaderManager.restartLoader(loaderId, extractInfo, this);
    }

    /* This method is called after creating gif file. */
    @Override
    public void onExtractionFinished(Uri gifUri) {
        setGif(gifUri);
        Utils.addImageToGallery(this, gifUri);
        Utils.shareGif(this, gifUri);
    }

    /* This method is called whenever extract frame from video */
    @Override
    public void onFrameExtracted(final Bitmap frame) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFrameView.setImageBitmap(frame);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                Uri selectedVideoUri = data.getData();

                if (selectedVideoUri != null) {
                    setVideo(selectedVideoUri);
                    mPickedVideo = selectedVideoUri;

                     Intent i = new Intent(this,VideoDecoderActivity.class);
                /*    i.putExtra("video_uri",selectedVideoUri.toString());
                    startActivity(i);*/
                }
            }
        }
    }

    public void setVideo(Uri videoUri) {
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "yourApplicationName"), new DefaultBandwidthMeter());

        mOrigin = new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(videoUri);

        mPlayer.prepare(mOrigin);
        mPlayer.setPlayWhenReady(false);
    }


    public void setGif(final Uri gifUri) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    GifDrawable gifUriDrawable = new GifDrawable(null, gifUri);
                    gifUriDrawable.setLoopCount(0);
                    mGifView.setImageDrawable(gifUriDrawable);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPlayer.release();
        if (mExtractor != null)
            mExtractor.release();
    }


    @Override
    public Loader<Uri> onCreateLoader(int loaderId, Bundle extractInfo) {
        switch (loaderId){
            case LOADER_EXTRACT_GIF_FMMR:
                return new GifExtractorFMMR(this, extractInfo);
            case LOADER_EXTRACT_GIF_MEDIACODEC:
                return new GifExtractorMC(this, extractInfo);
            default:
                return null;
        }
    }
    /* called when end making gif */
    @Override
    public void onLoadFinished(Loader<Uri> loader, Uri gifUri) {
        Log.d(TAG,"onLoadFinished..");

        setGif(gifUri);
    }

    @Override
    public void onLoaderReset(Loader<Uri> loader) {

    }
    public Bundle getExtractBundle(){
        /* this bundle will be a argument for loader */
        Bundle extractInfo = new Bundle();

         /* set fps */
        int fps = Integer.parseInt(mEtFps.getText().toString());
        extractInfo.putInt(GIF_EXTRACT_FPS,fps);

        /* set video range */
        long startPos = Long.parseLong(mEtStart.getText().toString());
        long endPos = Long.parseLong(mEtEnd.getText().toString());
        extractInfo.putLong(GIF_EXTRACT_START,startPos);
        extractInfo.putLong(GIF_EXTRACT_END,endPos);

        /* set video uri */
        extractInfo.putString(GIF_EXTRACT_VIDEO_URI, mPickedVideo.toString());

        return extractInfo;
    }
}
