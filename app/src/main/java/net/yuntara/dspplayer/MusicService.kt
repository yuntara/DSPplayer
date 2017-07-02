package net.yuntara.dspplayer

import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.IBinder

import java.util.ArrayList
import java.util.Collections

import android.os.Binder
import android.os.PowerManager
import android.util.Log
import android.content.Context

class MusicService : Service() {
    //song list
    private var songs: ArrayList<Song>? = null
    //current position
    private var songPosn: Int = 0
    private var streamPlayer: StreamPlayer? = null
    private var activity: MainActivity? = null
    private val musicBind = MusicBinder()
    private val mainContext: Context? = null
    fun setActivity(act: MainActivity) {
        activity = act
    }

    fun setSong(songIndex: Int) {
        songPosn = songIndex
    }

    fun playNext() {
        songPosn++
        if (songPosn >= songs!!.size) {
            songPosn = 0
        }
        playSong()
    }

    fun setFilter(context: Context, filname: String) {
        streamPlayer!!.setFilter(context, filname)
    }

    fun Shuffle() {

        //streamPlayer.stop();
        //Collections.shuffle(songs);
        songPosn = 0
        playSong()

    }

    fun play() {
        playSong()
    }

    fun pause() {
        streamPlayer!!.pause()
    }

    operator fun next() {
        playNext()
    }

    fun prev() {
        songPosn--
        if (songPosn < 0) {
            songPosn = songs!!.size - 1
        }
        playSong()
    }

    fun setPosition(position: Float) {
        streamPlayer!!.setPosition(position)
    }

    fun OnPlayEnd() {
        playNext()
    }

    fun onProgress(prog: Float) {
        activity!!.setPlayingPosition(prog)
    }

    fun playSong() {
        if (activity != null) {
            activity!!.setPlayingSongPos(songPosn)
        }
        streamPlayer!!.reset()
        //get song
        val playSong = songs!![songPosn]
        //get id
        val currSong = playSong.id
        //set uri
        val trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currSong)
        try {
            streamPlayer!!.setDataSource(applicationContext, trackUri)
        } catch (e: Exception) {
            Log.e("MUSIC SERVICE", "Error setting data source", e)

        }

        streamPlayer!!.play()
    }

    override fun onUnbind(intent: Intent): Boolean {
        streamPlayer!!.stop()
        streamPlayer!!.release()
        return false
    }

    fun setList(theSongs: ArrayList<Song>) {
        songs = theSongs
    }

    fun loadFilter(context: Context) {
        streamPlayer!!.loadFilter(context)
    }

    override fun onCreate() {
        //create the service
        super.onCreate()
        //initialize position
        songPosn = 0
        streamPlayer = StreamPlayer(this)

    }

    fun initMusicPlayer() {
        //set player properties
    }

    override fun onBind(intent: Intent): IBinder? {
        return musicBind
    }

    inner class MusicBinder : Binder() {

        internal val service: MusicService
            get() = this@MusicService


    }
}
