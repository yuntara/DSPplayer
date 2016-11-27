package net.yuntara.dspplayer;


import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

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
    private boolean paused = false;
    private Context songContext;
    private Uri songUri;
    protected int inputBufIndex;
    protected int bufIndexCheck;
    protected int lastInputBufIndex;
    private boolean finished = true;
    private final int SIZEX = 65536;
    private boolean stopped = false;
    private Context mainContext;
    private MusicService msc;
    private float datachunk[];
    private float x[],y[];

    private short before_dataL[],
            before_dataR[];
    private short audio_dataL[],
            audio_dataR[];
    private int audio_pos = 0;
    private float seekTo = -1;
    public enum State {
        Retrieving, // retrieving music (filling buffer)
        Stopped,    // player is stopped and not prepared to play
        Playing,    // playback active
    };
    private State mState = State.Stopped;
    private String LOG_TAG = "StreamPlayer";


    public void setDataSource(Context context,Uri uri){
        songContext = context;
        songUri = uri;

    }
    public StreamPlayer(MusicService m){
        //native_lib initialize func
        cfftinit(SIZEX*2);
        msc = m;
    }
    public void setPosition(float seekTo){
        this.seekTo = seekTo;
    }
    public void loadFilter(Context context){
        ///*
        float filR1_r[];
        float filR2_r[];
        float filL1_r[];
        float filL2_r[];
        float filR1_i[];
        float filR2_i[];
        float filL1_i[];
        float filL2_i[];
        filR1_r = new float[SIZEX+1];
        filR1_i = new float[SIZEX+1];
        filR2_r = new float[SIZEX+1];
        filR2_i = new float[SIZEX+1];
        filL1_r = new float[SIZEX+1];
        filL1_i = new float[SIZEX+1];
        filL2_r = new float[SIZEX+1];
        filL2_i = new float[SIZEX+1];

        before_dataL = new short[SIZEX];
        before_dataR = new short[SIZEX];

        audio_dataL = new short[SIZEX];
        audio_dataR = new short[SIZEX];

        datachunk = new float[SIZEX*2];
        x = new float[SIZEX*2];
        y = new float[SIZEX*2];

        final int id = context.getResources().getIdentifier("out", "raw", context.getPackageName());
        if (id == 0) {    //エラーにはならない
            return;
        }
        InputStream is = context.getResources().openRawResource(id);
        DataInputStream ds = new DataInputStream(is);
        try {

            for (int i = 0; i <= SIZEX; i++) {
                filR1_r[i] = (float)ds.readDouble();
                filR1_i[i] = (float)ds.readDouble();
            }
            for (int i = 0; i <= SIZEX; i++) {
                filR2_r[i] = (float)ds.readDouble();
                filR2_i[i] = (float)ds.readDouble();
            }
            for (int i = 0; i <= SIZEX; i++) {
                filL1_r[i] = (float)ds.readDouble();
                filL1_i[i] = (float)ds.readDouble();
            }
            for (int i = 0; i <= SIZEX; i++) {
                filL2_r[i] = (float)ds.readDouble();
                filL2_i[i] = (float)ds.readDouble();
            }
            csetfil(filR1_r,filR1_i,filR2_r,filR2_i,filL1_r,filL1_i,filL2_r,filL2_i);
        }catch(IOException e){

            Arrays.fill(filR1_r,0);
            Arrays.fill(filR1_i,0);
            Arrays.fill(filL1_r,0);
            Arrays.fill(filL1_i,0);
            Arrays.fill(filR2_r,0);
            Arrays.fill(filR2_i,0);
            Arrays.fill(filL2_r,0);
            Arrays.fill(filL2_i,0);
            csetfil(filR1_r,filR1_i,filR2_r,filR2_i,filL1_r,filL1_i,filL2_r,filL2_i);
        }

    }
    //private void queueChunk(short[] chunk,int csize) {
    private void queueChunk(java.nio.ShortBuffer shortBuffer,int csize) {
        //csize = (datalength) * (2channel)
        //畳みこみできるサイズになるまでバッファに貯める
        boolean process = false;

        int samples = csize / 2;

        //変数名のLRが間違ってますが、フィルタ作成の方のプログラムから間違っているので、そのうちrefactorします
        for (int i = 0; i < samples; i++) {
            //audio_dataL[audio_pos + i] = chunk[2 * (i) + 1];//右音声
            //audio_dataR[audio_pos + i] = chunk[2 * (i)];    //左音声

            audio_dataR[audio_pos + i] = shortBuffer.get();    //左音声
            audio_dataL[audio_pos + i] = shortBuffer.get();   //右音声
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

        //overlap-save法のために、2ブロック分のデータが必要　　(overlap-addだと畳みこみ後に連結処理が入るので面倒くさいです)
        for (int j = 0; j < SIZEX * 2; j++) {
            //前半ブロックは前回のデータ
            if (j<SIZEX) {
                x[j] = before_dataL[j];
                y[j] = before_dataR[j];
            }
            //後半ブロックが今回のデータ
            else {
                x[j] = audio_dataL[(j - SIZEX)];//
                y[j] = audio_dataR[(j - SIZEX)];
            }
        }

        //次回の畳みこみに使う分をコピーしておく
        System.arraycopy(audio_dataL, 0, before_dataL, 0, SIZEX);
        System.arraycopy(audio_dataR, 0, before_dataR, 0, SIZEX);

        //native_lib.cpp で畳みこみします
        ccomboluteRL(x,y);

        //直線畳みこみ（ifftの前半部分）しか使わない
        for (int j = 0; j < SIZEX; j++) {
            datachunk[2*j]   = x[j];
            datachunk[2*j+1] = y[j];
        }

        /*
          AudioTrack.WRITE_BLOCKING だとバッファーが書き込める状態になるまでブロックされるので、特別に処理をしなくても連続で再生される。
          ただし、書き込むサイズとAudioTrackのバッファーサイズ違うと面倒くさいので合わせておく。
        */
        audioTrack.write(datachunk,0,2*SIZEX,AudioTrack.WRITE_BLOCKING);

        //次回の書き込むオフセット
        audio_pos = samples - audio_pos - 1;

        //今回使わなかった分をバッファーの先頭にとっておきます。
        for (int i = 0; i < audio_pos; i++) {
            audio_dataR[i] = shortBuffer.get();
            audio_dataL[i] = shortBuffer.get();
        }



    }
    public void pause(){
        paused = !paused;
    }
    public void play()
    {

        mState = State.Retrieving;
        //mDelegateHandler.onRadioPlayerBuffering(MP3RadioStreamPlayer.this);

        doStop = false;
        finished = false;
        bufIndexCheck = 0;

        lastInputBufIndex = -1;
        DecodeOperation dcd = new DecodeOperation();
        dcd.setOnCallBack(new CallBackTask(){

            @Override
            public void CallBack() {

                super.CallBack();


                msc.OnPlayEnd();
                stopped = false;


            }

        });
        dcd.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }
    private void decodeLoop(DecodeOperation parent) throws  IOException
    {


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

        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        //int bits = format.getInteger(MediaFormat.KEY_PCM_ENCODING);

        if(sampleRate != 44100 ){
            this.mState = State.Stopped;
            return;
        }


        //int maxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);


        // create our AudioTrack instance
        if(audioTrack == null) {

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                /*AudioTrack.getMinBufferSize (
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT
                )
                */
                    //2channel * bufferlength * sizeof(float)
                    2 * SIZEX * 4
                    ,
                    AudioTrack.MODE_STREAM
            );
        }

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
        long duration = format.getLong(MediaFormat.KEY_DURATION);

        while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !doStop) {
            while(paused && !doStop){
                try {
                    Thread.sleep(100);
                    //info.


                }catch(InterruptedException e){

                }
            }

            if(this.seekTo>0){
                long pos = (long) (duration * this.seekTo/1.0f);
                Log.i(LOG_TAG,"seek to "+pos +" = "+duration +"*"+seekTo+"/1000.0f" );
                this.seekTo=-1;
                if(pos<duration) {

                    //extractor.
                    //extractor.seekTo(pos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    long starttime = System.currentTimeMillis();

                    long presentationTimeUs = extractor.getSampleTime();
                    while(presentationTimeUs < pos && System.currentTimeMillis() < starttime + 3000) {
                        extractor.advance();
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    //extractor.
/*
I/StreamPlayer: seek to 7960 = 274506666*0.029/1000.0f
E/FileSource: seek to 12238042466020 failed
E/FileSource: seek to 12238042458078 failed
 */
                }

            }

            //Log.i(LOG_TAG, "loop ");
            noOutputCounter++;
            if (!sawInputEOS) {

                inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                bufIndexCheck++;
                // Log.d(LOG_TAG, " bufIndexCheck " + bufIndexCheck);
                if (inputBufIndex >= 0) {


                    ByteBuffer dstBuf = codec.getInputBuffer(inputBufIndex);

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);
                    //extractor.
                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        Log.d(LOG_TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    // can throw illegal state exception (???)

                    //Progress
                    float prog =  ((float)presentationTimeUs / duration);
                    parent.progress(prog);

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
                if (info.size > 0) {
                    noOutputCounter = 0;
                }
                int outputBufIndex = res;

                ByteBuffer buf = codec.getOutputBuffer(outputBufIndex);
                if(info.size > 0){
                    queueChunk(buf.asShortBuffer(),info.size/2);
                    this.mState = State.Playing;
                }
                buf.clear();
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();

            } else {

            }
        }
        if(doStop){
            parent.setUseCallBack(false);
        }
        this.mState = State.Stopped;
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

        if(mState != State.Stopped){
            stopped = true;
        }
        while(mState != State.Stopped) {
            try{
                Thread.sleep(10);
            }catch(InterruptedException e){
                break;
            }

        }

    }
    public static class CallBackTask {
        public void CallBack() {
        }
    }
    private class DecodeOperation extends AsyncTask<Void, Float, Void> {
        private CallBackTask callbacktask;
        private boolean useCallBack = true;
        @Override
        protected Void doInBackground(Void... values) {
            try {
                StreamPlayer.this.decodeLoop(this);
            } catch (IOException e) {
                //TODO catch IOException at decode
            }
            finished=true;
            return null;
        }
        public void progress(float prg){
            onProgressUpdate(prg);
        }
        @Override
        protected void onPostExecute(Void result)  {
            super.onPostExecute(null);
            if(useCallBack) {
                callbacktask.CallBack();
            }
        }
        public boolean getUseCallBack(){
            return useCallBack;
        }
        public void setUseCallBack(boolean useCallBack){
            this.useCallBack = useCallBack;
        }
        public void setOnCallBack(CallBackTask _cbj) {
            callbacktask = _cbj;
        }
        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Float... values) {
            StreamPlayer.this.msc.onProgress(values[0]);

        }
    }
    public native void cfftinit(int size);
    //public native void cfft(float x[],float y[],boolean isReverse);
    //public native void cifft(float x[],float y[]);
    public native void csetfil(float r1r[],float r1i[],float r2r[],float r2i[],
                                  float l1r[],float l1i[],float l2r[],float l2i[]
                               );
    public native void ccomboluteRL(float x[],float y[]);
    //public native void cadjust(float x[],float y[]);

    static {
        System.loadLibrary("native-lib");
    }
}
