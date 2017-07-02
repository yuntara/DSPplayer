package net.yuntara.dspplayer

import android.widget.BaseAdapter
import java.util.ArrayList
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup

/**
 * Created by yuntara on 2016/11/03.
 */

class SongAdapter(c: Context, private var songs: ArrayList<Song>?) : BaseAdapter() {
    private val songInf: LayoutInflater
    private var selPos: Int = 0

    init {
        songInf = LayoutInflater.from(c)
        selPos = -1
    }

    override fun getCount(): Int {
        return songs!!.size
    }

    override fun getItem(arg0: Int): Any? {

        return null
    }

    override fun getItemId(arg0: Int): Long {

        return 0
    }

    fun setSelected(position: Int) {
        selPos = position
        this.notifyDataSetChanged()
    }

    fun refreshList(theSongs: ArrayList<Song>) {
        //songs.clear();
        songs = theSongs
        this.notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        //map to song layout
        val songLay: LinearLayout
        if (convertView == null) {
            songLay = songInf.inflate(R.layout.song, parent, false) as LinearLayout
        } else {
            songLay = convertView as LinearLayout
        }

        //get title and artist views
        val songView = songLay.findViewById(R.id.song_title) as TextView
        val artistView = songLay.findViewById(R.id.song_artist) as TextView
        val img = songLay.findViewById(R.id.playingIcon) as ImageView


        //get song using position
        val currSong = songs!![position]

        //get title and artist strings
        songView.text = currSong.title
        artistView.text = currSong.artist
        if (position == selPos) {
            img.visibility = View.VISIBLE
        } else {
            img.visibility = View.INVISIBLE
        }
        //set position as tag
        songLay.tag = position



        return songLay
    }
}
