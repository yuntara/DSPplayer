package net.yuntara.dspplayer;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.Collections;

import android.os.Binder;
import android.os.PowerManager;
import android.util.Log;
import android.content.Context;

public class MusicService extends Service {
    //song list
    private ArrayList<Song> songs;
    //current position
    private int songPosn;
    private StreamPlayer streamPlayer;
    private MainActivity activity;
    private final IBinder musicBind = new MusicBinder();
    private Context mainContext;
    public void setActivity(MainActivity act){
        activity = act;
    }
    public MusicService() {

    }
    public void setSong(int songIndex){
        songPosn=songIndex;
    }
    public void playNext(){
        songPosn++;
        if(songPosn>=songs.size()){
            songPosn =0;
        }
        playSong();
    }
    public void Shuffle(){

        //streamPlayer.stop();
        //Collections.shuffle(songs);
        songPosn = 0;
        playSong();

    }
    public void OnPlayEnd(){
        playNext();
    }
    public void playSong(){
        if(activity != null){
            activity.setPlayingSongPos(songPosn);
        }
        streamPlayer.reset();
        //get song
        Song playSong = songs.get(songPosn);
        //get id
        long currSong = playSong.getID();
        //set uri
        Uri trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currSong);
        try{
            streamPlayer.setDataSource(getApplicationContext(), trackUri);
        }
        catch(Exception e){
            Log.e("MUSIC SERVICE", "Error setting data source", e);

        }

        streamPlayer.play();
    }

    @Override
    public boolean onUnbind(Intent intent){
        streamPlayer.stop();
        streamPlayer.release();
        return false;
    }

    public void setList(ArrayList<Song> theSongs){
        songs=theSongs;
    }
    public void loadFilter(Context context){
        streamPlayer.loadFilter(context);
    }
    @Override
    public void onCreate(){
        //create the service
        super.onCreate();
        //initialize position
        songPosn=0;
        streamPlayer = new StreamPlayer(this);

    }
    public void initMusicPlayer(){
        //set player properties
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }
    public class MusicBinder extends Binder {

        MusicService getService() {
            return MusicService.this;
        }


    }
}
