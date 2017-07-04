package net.yuntara.dspplayer


import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import java.util.stream.Stream

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.AsyncTask
import android.os.Handler
import android.os.Message
import android.support.v4.media.session.PlaybackStateCompat

import android.util.Log
import android.content.Context
import android.net.Uri


/**
 * Created by yuntara on 2016/11/03.
 */

class StreamPlayer(private val msc: MusicService) {
    protected var extractor: MediaExtractor? = null
    protected var codec: MediaCodec? = null
    protected var audioTrack: AudioTrack? = null
    private var doStop: Boolean = false
    private var paused = false
    private var songContext: Context? = null
    private var songUri: Uri? = null
    protected var inputBufIndex: Int = 0
    protected var bufIndexCheck: Int = 0
    protected var lastInputBufIndex: Int = 0
    private var finished = true
    private val SIZEX = 65536/2
    private var stopped = false
    private val mainContext: Context? = null
    private var datachunk: FloatArray? = null
    private var x: FloatArray? = null
    private var y: FloatArray? = null
    private var filterName = "outthree"
    private var before_dataL: ShortArray? = null
    private var before_dataR: ShortArray? = null
    private var audio_dataL: ShortArray? = null
    private var audio_dataR: ShortArray? = null
    private var audio_pos = 0
    private var seekTo = -1f

    enum class State {
        Retrieving, // retrieving music (filling buffer)
        Stopped, // player is stopped and not prepared to play
        Playing
        // playback active
    }

    private var mState = State.Stopped
    private val LOG_TAG = "StreamPlayer"


    fun setDataSource(context: Context, uri: Uri) {
        songContext = context
        songUri = uri

    }

    fun setFilter(context: Context, filname: String) {
        paused = true
        filterName = filname
        loadFilter(context)
        paused = false
    }

    init {
        //native_lib initialize func
        cfftinit(SIZEX * 2)
    }

    fun setPosition(seekTo: Float) {
        this.seekTo = seekTo
    }

    fun loadFilter(context: Context) {
        ///*
        val filR1_r: FloatArray
        val filR2_r: FloatArray
        val filL1_r: FloatArray
        val filL2_r: FloatArray
        val filR1_i: FloatArray
        val filR2_i: FloatArray
        val filL1_i: FloatArray
        val filL2_i: FloatArray
        filR1_r = FloatArray(SIZEX + 1)
        filR1_i = FloatArray(SIZEX + 1)
        filR2_r = FloatArray(SIZEX + 1)
        filR2_i = FloatArray(SIZEX + 1)
        filL1_r = FloatArray(SIZEX + 1)
        filL1_i = FloatArray(SIZEX + 1)
        filL2_r = FloatArray(SIZEX + 1)
        filL2_i = FloatArray(SIZEX + 1)

        before_dataL = ShortArray(SIZEX)
        before_dataR = ShortArray(SIZEX)

        audio_dataL = ShortArray(SIZEX)
        audio_dataR = ShortArray(SIZEX)

        datachunk = FloatArray(SIZEX * 2)
        x = FloatArray(SIZEX * 2)
        y = FloatArray(SIZEX * 2)

        val id = context.resources.getIdentifier(filterName, "raw", context.packageName)
        if (id == 0) {    //エラーにはならない
            return
        }
        val `is` = context.resources.openRawResource(id)
        val ds = DataInputStream(`is`)
        try {

            for (i in 0..SIZEX) {
                filR1_r[i] = ds.readDouble().toFloat()
                filR1_i[i] = ds.readDouble().toFloat()
            }
            for (i in 0..SIZEX) {
                filR2_r[i] = ds.readDouble().toFloat()
                filR2_i[i] = ds.readDouble().toFloat()
            }
            for (i in 0..SIZEX) {
                filL1_r[i] = ds.readDouble().toFloat()
                filL1_i[i] = ds.readDouble().toFloat()
            }
            for (i in 0..SIZEX) {
                filL2_r[i] = ds.readDouble().toFloat()
                filL2_i[i] = ds.readDouble().toFloat()
            }
            csetfil(filR1_r, filR1_i, filR2_r, filR2_i, filL1_r, filL1_i, filL2_r, filL2_i)
        } catch (e: IOException) {

            Arrays.fill(filR1_r, 0f)
            Arrays.fill(filR1_i, 0f)
            Arrays.fill(filL1_r, 0f)
            Arrays.fill(filL1_i, 0f)
            Arrays.fill(filR2_r, 0f)
            Arrays.fill(filR2_i, 0f)
            Arrays.fill(filL2_r, 0f)
            Arrays.fill(filL2_i, 0f)
            csetfil(filR1_r, filR1_i, filR2_r, filR2_i, filL1_r, filL1_i, filL2_r, filL2_i)
        }

    }

    //private void queueChunk(short[] chunk,int csize) {
    private fun queueChunk(shortBuffer: java.nio.ShortBuffer, csize: Int) {
        //csize = (datalength) * (2channel)
        //畳みこみできるサイズになるまでバッファに貯める
        var process = false

        val samples = csize / 2

        //変数名のLRが間違ってますが、フィルタ作成の方のプログラムから間違っているので、そのうちrefactorします
        for (i in 0..samples - 1) {
            //audio_dataL[audio_pos + i] = chunk[2 * (i) + 1];//右音声
            //audio_dataR[audio_pos + i] = chunk[2 * (i)];    //左音声

            audio_dataR!![audio_pos + i] = shortBuffer.get()    //左音声
            audio_dataL!![audio_pos + i] = shortBuffer.get()   //右音声
            if (audio_pos + i >= SIZEX - 1) {
                audio_pos = i
                process = true
                break
            }
        }
        //まだ足りない
        if (!process) {
            audio_pos += samples
            return
        }

        //overlap-save法のために、2ブロック分のデータが必要　　(overlap-addだと畳みこみ後に連結処理が入るので面倒くさいです)
        for (j in 0..SIZEX * 2 - 1) {
            //前半ブロックは前回のデータ
            if (j < SIZEX) {
                x!![j] = before_dataL!![j].toFloat()
                y!![j] = before_dataR!![j].toFloat()
            } else {
                x!![j] = audio_dataL!![j - SIZEX].toFloat()//
                y!![j] = audio_dataR!![j - SIZEX].toFloat()
            }//後半ブロックが今回のデータ
        }

        //次回の畳みこみに使う分をコピーしておく
        System.arraycopy(audio_dataL!!, 0, before_dataL!!, 0, SIZEX)
        System.arraycopy(audio_dataR!!, 0, before_dataR!!, 0, SIZEX)

        //native_lib.cpp で畳みこみします
        ccomboluteRL(x!!, y!!)

        //直線畳みこみ（ifftの前半部分）しか使わない
        for (j in 0..SIZEX - 1) {
            datachunk!![2 * j] = x!![j]
            datachunk!![2 * j + 1] = y!![j]
        }

        /*
          AudioTrack.WRITE_BLOCKING だとバッファーが書き込める状態になるまでブロックされるので、特別に処理をしなくても連続で再生される。
          ただし、書き込むサイズとAudioTrackのバッファーサイズ違うと面倒くさいので合わせておく。
        */
        audioTrack!!.write(datachunk!!, 0, 2 * SIZEX, AudioTrack.WRITE_BLOCKING)

        //次回の書き込むオフセット
        audio_pos = samples - audio_pos - 1

        //今回使わなかった分をバッファーの先頭にとっておきます。
        for (i in 0..audio_pos - 1) {
            audio_dataR!![i] = shortBuffer.get()
            audio_dataL!![i] = shortBuffer.get()
        }


    }

    fun pause() {
        paused = !paused
    }

    fun play() {

        mState = State.Retrieving
        //mDelegateHandler.onRadioPlayerBuffering(MP3RadioStreamPlayer.this);

        doStop = false
        finished = false
        bufIndexCheck = 0

        lastInputBufIndex = -1

        val dcd = DecodeOperation()
        dcd.setOnCallBack(object : CallBackTask() {

            override fun CallBack() {

                super.CallBack()


                msc.OnPlayEnd()
                stopped = false


            }

        })
        dcd.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)

    }

    @Throws(IOException::class)
    private fun decodeLoop(parent: DecodeOperation) {


        // extractor gets information about the stream
        extractor = MediaExtractor()
        try {
            extractor!!.setDataSource(songContext!!, songUri!!, null)
        } catch (e: Exception) {

            return
        }
        val format = extractor!!.getTrackFormat(0)
        val mime = format.getString(MediaFormat.KEY_MIME)

        // the actual decoder
        codec = MediaCodec.createDecoderByType(mime)
        codec!!.configure(format, null, null, 0 /* flags */)/* surface *//* crypto */
        codec!!.start()

        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

        //int bits = format.getInteger(MediaFormat.KEY_PCM_ENCODING);

        if (sampleRate != 44100) {
            this.mState = State.Stopped
            return
        }


        //int maxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);


        // create our AudioTrack instance
        if (audioTrack == null) {

            audioTrack = AudioTrack(
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
                    2 * SIZEX * 4,
                    AudioTrack.MODE_STREAM
            )
        }

        // start playing, we will feed you later
        audioTrack!!.play()
        extractor!!.selectTrack(0)

        // start decoding
        val kTimeOutUs: Long = 10000
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        var noOutputCounter = 0
        val noOutputCounterLimit = 50
        val duration = format.getLong(MediaFormat.KEY_DURATION)

        while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !doStop) {
            while (paused && !doStop) {
                try {
                    Thread.sleep(100)
                    //info.


                } catch (e: InterruptedException) {

                }

            }

            if (this.seekTo > 0) {
                val pos = (duration * this.seekTo / 1.0f).toLong()
                Log.i(LOG_TAG, "seek to $pos = $duration*$seekTo/1000.0f")
                this.seekTo = -1f
                if (pos < duration) {

                    //extractor.
                    //extractor.seekTo(pos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    val starttime = System.currentTimeMillis()

                    var presentationTimeUs = extractor!!.sampleTime
                    while (presentationTimeUs < pos && System.currentTimeMillis() < starttime + 3000) {
                        extractor!!.advance()
                        presentationTimeUs = extractor!!.sampleTime
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
            noOutputCounter++
            if (!sawInputEOS) {

                inputBufIndex = codec!!.dequeueInputBuffer(kTimeOutUs)
                bufIndexCheck++
                // Log.d(LOG_TAG, " bufIndexCheck " + bufIndexCheck);
                if (inputBufIndex >= 0) {


                    val dstBuf = codec!!.getInputBuffer(inputBufIndex)

                    var sampleSize = extractor!!.readSampleData(dstBuf!!, 0 /* offset */)
                    //extractor.
                    var presentationTimeUs: Long = 0

                    if (sampleSize < 0) {
                        Log.d(LOG_TAG, "saw input EOS.")
                        sawInputEOS = true
                        sampleSize = 0
                    } else {
                        presentationTimeUs = extractor!!.sampleTime
                    }
                    // can throw illegal state exception (???)

                    //Progress
                    val prog = presentationTimeUs.toFloat() / duration
                    parent.progress(prog)

                    codec!!.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            if (sawInputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)



                    if (!sawInputEOS) {
                        extractor!!.advance()
                    }
                } else {

                }
            }

            val res = codec!!.dequeueOutputBuffer(info, kTimeOutUs)

            if (res >= 0) {
                if (info.size > 0) {
                    noOutputCounter = 0
                }
                val outputBufIndex = res

                val buf = codec!!.getOutputBuffer(outputBufIndex)
                if (info.size > 0) {
                    queueChunk(buf!!.asShortBuffer(), info.size / 2)
                    this.mState = State.Playing
                }
                buf!!.clear()
                codec!!.releaseOutputBuffer(outputBufIndex, false /* render */)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {

                    sawOutputEOS = true
                }
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val oformat = codec!!.outputFormat

            } else {

            }
        }
        if (doStop) {
            parent.useCallBack = false
        }
        this.mState = State.Stopped
    }

    fun reset() {
        stop()
    }

    fun release() {
        stop()
        relaxResources(false)
    }

    private fun relaxResources(release: Boolean) {
        if (codec != null) {
            if (release) {
                codec!!.stop()
                codec!!.release()
                codec = null
            }

        }
        if (audioTrack != null) {
            audioTrack!!.flush()
            audioTrack!!.release()
            audioTrack = null
        }
    }

    fun stop() {
        doStop = true

        if (mState != State.Stopped) {
            stopped = true
        }
        while (mState != State.Stopped) {
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                break
            }

        }

    }

    open class CallBackTask {
        open fun CallBack() {}
    }

    private inner class DecodeOperation : AsyncTask<Void, Float, Void>() {
        private var callbacktask: CallBackTask? = null
        var useCallBack = true
        override fun doInBackground(vararg values: Void): Void? {
            try {
                this@StreamPlayer.decodeLoop(this)
            } catch (e: IOException) {
                //TODO catch IOException at decode
            }

            finished = true
            return null
        }

        fun progress(prg: Float) {
            onProgressUpdate(prg)
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            if (useCallBack) {
                callbacktask?.CallBack()
            }
        }

        fun setOnCallBack(_cbj: CallBackTask) {
            callbacktask = _cbj
        }

        override fun onPreExecute() {}

        protected fun onProgressUpdate(vararg values: Float) {
            this@StreamPlayer.msc.onProgress(values[0])

        }
    }

    external fun cfftinit(size: Int)
    //public native void cfft(float x[],float y[],boolean isReverse);
    //public native void cifft(float x[],float y[]);
    external fun csetfil(r1r: FloatArray, r1i: FloatArray, r2r: FloatArray, r2i: FloatArray,
                         l1r: FloatArray, l1i: FloatArray, l2r: FloatArray, l2i: FloatArray
    )

    external fun ccomboluteRL(x: FloatArray, y: FloatArray)

    companion object {
        //public native void cadjust(float x[],float y[]);

        init {
            System.loadLibrary("native-lib")
        }
    }
}
