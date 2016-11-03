package net.yuntara.dspplayer;


import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.content.Context;
import android.net.Uri;


/**
 * Created by yuntara on 2016/11/03.
 */

public class StreamPlayer {
    protected MediaExtractor extractor;
    protected MediaCodec codec;
    protected AudioTrack audioTrack;
    private boolean doStop;
    private Context songContext;
    private Uri songUri;
    protected int inputBufIndex;
    protected int bufIndexCheck;
    protected int lastInputBufIndex;
    private final int SIZEX = 65536;
    private Context mainContext;
    private double filR1_r[];
    private double filR2_r[];
    private double filL1_r[];
    private double filL2_r[];
    private double filR1_i[];
    private double filR2_i[];
    private double filL1_i[];
    private double filL2_i[];
    private short datachunk[];
    private FFT fft;
    double x[],y[];
    double c_r[];
    double c_i[];
    short before_dataL[],
            before_dataR[];
    short before_dataL2[],
            before_dataR2[];
    short audio_dataL[],
            audio_dataR[];
    short audio_dataLbuf[],
            audio_dataRbuf[];
    int audio_pos = 0;

    public enum State {
        Retrieving, // retrieving music (filling buffer)
        Stopped,    // player is stopped and not prepared to play
        Playing,    // playback active
    };
    private State mState = State.Retrieving;
    private String LOG_TAG = "StreamPlayer";
    public void setDataSource(Context context,Uri uri){
        songContext = context;
        songUri = uri;

    }
    public StreamPlayer(){
        //fft = new FFT(SIZEX*2);
    }
    public void loadFilter(Context context){
        ///*
        filR1_r = new double[SIZEX+1];
        filR1_i = new double[SIZEX+1];
        filR2_r = new double[SIZEX+1];
        filR2_i = new double[SIZEX+1];
        filL1_r = new double[SIZEX+1];
        filL1_i = new double[SIZEX+1];
        filL2_r = new double[SIZEX+1];
        filL2_i = new double[SIZEX+1];
        c_r = new double[SIZEX*2];
        c_i = new double[SIZEX*2];
        before_dataL = new short[SIZEX];
        before_dataR = new short[SIZEX];
        before_dataL2 = new short[SIZEX];
        before_dataR2 = new short[SIZEX];
        audio_dataL = new short[SIZEX];
        audio_dataR = new short[SIZEX];
        audio_dataLbuf = new short[SIZEX];
        audio_dataRbuf = new short[SIZEX];
        datachunk = new short[SIZEX*2];
        x = new double[SIZEX*2];
        y = new double[SIZEX*2];

        final int id = context.getResources().getIdentifier("bnnn.dat", "raw", context.getPackageName());
        if (id == 0) {    //エラーにはならない
            return;
            // throw new Exception("aa");
        }
        InputStream is = context.getResources().openRawResource(id);
        DataInputStream ds = new DataInputStream(is);
        try {


            //R1_r R1_i
            for (int i = 0; i <= SIZEX; i++) {
                filR1_r[i] = ds.readDouble();
                filR1_i[i] = ds.readDouble();
            }
            for (int i = 0; i <= SIZEX; i++) {
                filR2_r[i] = ds.readDouble();
                filR2_i[i] = ds.readDouble();
            }
            for (int i = 0; i <= SIZEX; i++) {
                filL1_r[i] = ds.readDouble();
                filL1_i[i] = ds.readDouble();
            }
            for (int i = 0; i <= SIZEX; i++) {
                filL2_r[i] = ds.readDouble();
                filL2_i[i] = ds.readDouble();
            }
        }catch(IOException e){
            return;
        }
        //*/
        // /return loadText(is, DEFAULT_ENCORDING);



    }
    private void queueChunk(short[] chunk,int csize) {
        //csize = (datalength) * (2channel)
        //畳みこみできるサイズになるまでキャッシュする
        double b_r,b_i;
        Boolean process = false;
        int samples = csize/2;
        for (int i = 0; i < samples; i++) {
            audio_dataL[audio_pos + i] = chunk[2 * (i)+1];
            audio_dataR[audio_pos + i] = chunk[2 * (i)];
            if (audio_pos + i >= SIZEX - 1) {
                audio_pos = i;
                process = true;
                break;
            }
        }
        //まだ足りない
        if (!process) {
            audio_pos += samples;
            return;
        }

        //畳みこみ切れない残りはとっておく
        for (int i = 0; i < samples - audio_pos - 1; i++) {
            audio_dataLbuf[i] = chunk[2 * (audio_pos + i + 1) + 1];
            audio_dataRbuf[i] = chunk[2 * (audio_pos + i + 1)];
        }

        audio_pos = samples - audio_pos - 1;
        for (int j = 0; j < SIZEX; j++) {
            before_dataL2[j] = before_dataL[j];
            before_dataR2[j] = before_dataR[j];
        }
        for (int j = 0; j < SIZEX; j++) {

            before_dataL[j] = audio_dataL[j];
            before_dataR[j] = audio_dataR[j];

        }

        for (int j = 0; j < SIZEX * 2; j++) {
            if (j<SIZEX) {
                x[j] = before_dataL2[j];
                y[j] = 0;
            }
            else {
                x[j] = audio_dataL[(j - SIZEX)];//
                y[j] = 0;
            }
        }
        fft.fft(x,y,false);
        for (int j = 0; j < SIZEX + 1; j++) {
            c_r[j] = x[j];
            c_i[j] = y[j];
            x[j] = (x[j] * filR2_r[j]) - (y[j] * filR2_i[j]);
            y[j] = (x[j] * filR2_i[j]) + (y[j] * filR2_r[j]);

        }
        fft.adjust(x,y);

        for (int j = 0; j < SIZEX + 1; j++) {

            b_r = (c_r[j] * filR1_r[j]) - (c_i[j] * filR1_i[j]);
            b_i = (c_r[j] * filR1_i[j]) + (c_i[j] * filR1_r[j]);

            x[j] = x[j] - b_i;
            y[j] = y[j] + b_r;
            if (j > 0 && j < SIZEX) {
                x[SIZEX*2-j] += b_i;
                y[SIZEX*2-j] += b_r;
            }
        }
        fft.ifft(x,y);
        for (int j = 0; j < SIZEX; j++) {
            datachunk[2*j] = (short)x[j];
            datachunk[2*j+1] = (short)y[j];
        }

       audioTrack.write(datachunk,0,2*SIZEX);
    }
    public void play()
    {
        mState = State.Retrieving;
        //mDelegateHandler.onRadioPlayerBuffering(MP3RadioStreamPlayer.this);

        doStop = false;
        bufIndexCheck = 0;
        lastInputBufIndex = -1;

        new DecodeOperation().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }
    private void decodeLoop() throws  IOException
    {

        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        // extractor gets information about the stream
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(songContext,songUri,null);
        } catch (Exception e) {

            return;
        }

        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);

        // the actual decoder
        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        // get the sample rate to configure AudioTrack
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        // create our AudioTrack instance
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize (
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT
                ),
                AudioTrack.MODE_STREAM
        );

        // start playing, we will feed you later
        audioTrack.play();
        extractor.selectTrack(0);

        // start decoding
        final long kTimeOutUs = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        int noOutputCounterLimit = 50;


        while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !doStop) {
            //Log.i(LOG_TAG, "loop ");
            noOutputCounter++;
            if (!sawInputEOS) {

                inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                bufIndexCheck++;
                // Log.d(LOG_TAG, " bufIndexCheck " + bufIndexCheck);
                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);

                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        Log.d(LOG_TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    // can throw illegal state exception (???)

                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);



                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
                else
                {

                }
            }

            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0) {
                //Log.d(LOG_TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);
                if (info.size > 0) {
                    noOutputCounter = 0;
                }

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                //byte[] chunk = new byte[info.size];
                short[] chunk = new short[info.size/2];
                buf.asShortBuffer().get(chunk);
                //buf.get(chunk);
                buf.clear();
                if(chunk.length > 0){
                    //audioTrack.write(chunk,0,chunk.length);
                    queueChunk(chunk,chunk.length);
                    /*
                    if(this.mState != State.Playing)
                    {
                        mDelegateHandler.onRadioPlayerPlaybackStarted(MP3RadioStreamPlayer.this);
                    }
                    */
                    this.mState = State.Playing;

                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();

            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();

            } else {

            }
        }

        this.mState = State.Stopped;
        doStop = true;


    }
    public void reset(){
        stop();
    }
    public void release()
    {
        stop();
        relaxResources(false);
    }

    private void relaxResources(Boolean release)
    {
        if(codec != null)
        {
            if(release)
            {
                codec.stop();
                codec.release();
                codec = null;
            }

        }
        if(audioTrack != null)
        {
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
    }
    public void stop()
    {
        doStop = true;


        while(mState == State.Playing) {
            try{
                Thread.sleep(10);
            }catch(InterruptedException e){
                break;
            }
        }
    }

    private class DecodeOperation extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... values) {
            try {
                StreamPlayer.this.decodeLoop();
            } catch (IOException e) {
                //TODO catch IOException at decode
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

}
