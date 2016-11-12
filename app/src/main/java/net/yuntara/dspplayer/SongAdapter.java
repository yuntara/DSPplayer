package net.yuntara.dspplayer;

import android.widget.BaseAdapter;
import java.util.ArrayList;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by yuntara on 2016/11/03.
 */

public class SongAdapter extends BaseAdapter {

    private ArrayList<Song> songs;
    private LayoutInflater songInf;
    private int selPos;
    public SongAdapter(Context c, ArrayList<Song> theSongs){
        songs=theSongs;
        songInf=LayoutInflater.from(c);
        selPos=-1;
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Object getItem(int arg0) {

        return null;
    }

    @Override
    public long getItemId(int arg0) {

        return 0;
    }
    public void setSelected(int position){
        selPos = position;
        this.notifyDataSetChanged();
    }
    public void refreshList(ArrayList<Song> theSongs){
        //songs.clear();
        songs = theSongs;
        this.notifyDataSetChanged();
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //map to song layout
        LinearLayout songLay;
        if(convertView==null) {
            songLay = (LinearLayout) songInf.inflate(R.layout.song, parent, false);
        }else {
            songLay = (LinearLayout)convertView;
        }

        //get title and artist views
        TextView songView = (TextView) songLay.findViewById(R.id.song_title);
        TextView artistView = (TextView) songLay.findViewById(R.id.song_artist);
        ImageView img = (ImageView) songLay.findViewById(R.id.playingIcon);


                //get song using position
        Song currSong = songs.get(position);

        //get title and artist strings
        songView.setText(currSong.getTitle());
        artistView.setText(currSong.getArtist());
        if(position == selPos){
            img.setVisibility(View.VISIBLE);
        }else{
            img.setVisibility(View.INVISIBLE);
        }
        //set position as tag
        songLay.setTag(position);



        return songLay;
    }
}
