package net.yuntara.dspplayer

import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v4.content.PermissionChecker
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.widget.SeekBar
import android.widget.TextView

import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import android.net.Uri
import android.content.ContentResolver
import android.content.DialogInterface
import android.database.Cursor
import android.widget.ListView
import android.Manifest
import android.content.pm.PackageManager
import android.os.IBinder
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.view.MenuItem
import android.view.View
import net.yuntara.dspplayer.MusicService.MusicBinder

class MainActivity : AppCompatActivity() {

    private var songList: ArrayList<Song>? = null
    private var songView: ListView? = null
    private var tv: TextView? = null
    private var musicSrv: MusicService? = null
    private var playIntent: Intent? = null
    private var musicBound = false
    private var seek: SeekBar? = null
    private var seeking = false
    private val REQUEST_CODE_STORAGE_PERMISSION = 0x01
    internal var sharedPref: SharedPreferences? = null
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_shuffle -> {
                //shuffle
                Collections.shuffle(songList!!)
                refreshSongView()
                musicSrv!!.setList(songList!!)

                musicSrv!!.Shuffle()
            }
            R.id.action_end -> {
                stopService(playIntent)
                musicSrv = null
                System.exit(0)
            }
            R.id.action_fil -> showSetting()
        }
        return super.onOptionsItemSelected(item)

    }

    fun onPrevClicked(view: View) {
        musicSrv!!.prev()
    }

    fun onNextClicked(view: View) {
        musicSrv!!.next()
    }

    fun onPauseClicked(view: View) {
        musicSrv!!.pause()
    }

    fun onPlayClicked(view: View) {
        musicSrv!!.play()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    fun setPlayingPosition(prog: Float) {
        if (!seeking) {
            seek!!.progress = (prog * 1000).toInt()
        }
    }

    fun showSetting() {
        // インテントの生成
        val intent = Intent(this, Main2Activity::class.java)

        // SubActivity の起動
        startActivityForResult(intent, 2480)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        // Example of a call to a native method
        tv = findViewById(R.id.sample_text) as TextView
        tv!!.text = stringFromJNI()
        seek = findViewById(R.id.seekBar) as SeekBar

        seek!!.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar,
                                                   progress: Int, fromUser: Boolean) {
                        // ツマミをドラッグしたときに呼ばれる
                        //tv0.setText("設定値:"+sb0.getProgress());
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        // ツマミに触れたときに呼ばれる
                        seeking = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        // ツマミを離したときに呼ばれる
                        musicSrv!!.setPosition(seekBar.progress / 1000.0f)
                        seeking = false
                    }
                }
        )

        songView = findViewById(R.id.song_list) as ListView
        songList = ArrayList<Song>()
        checkPermissons()

        //getSongList();

        //ByteBuffer bf = new ByteBuffer();


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
        //SecondActivityから戻ってきた場合
            2480 -> if (resultCode == 2481) {
                //OKボタンを押して戻ってきたときの処理
                val filfile = data.getStringExtra("FILTERNAME")
                musicSrv!!.setFilter(this@MainActivity, filfile)
                val editor = sharedPref!!.edit()
                editor.putString("filter", filfile)
                editor.apply()
                tv!!.text = filfile
            }
            else -> {
            }
        }
    }

    private fun refreshSongView() {
        val adapter = songView!!.adapter as SongAdapter
        adapter.refreshList(songList!!)
        //adapter.getFilter().filter(s);//フィルタ実行。ソートもここで実行する。再描画も担当する

    }

    private fun registerListView() {
        //songView.setSelection(0);
        val songAdt = SongAdapter(this, songList)
        songView!!.adapter = songAdt
    }

    fun setPlayingSongPos(songPos: Int) {
        //songView.
        //songView.setSelection(songPos);
        val adapter = songView!!.adapter as SongAdapter
        adapter.setSelected(songPos)
        //adapter.getItem()
    }

    private val musicConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicBinder
            //get service
            musicSrv = binder.service
            //pass list
            musicSrv!!.setList(songList!!)
            val filfile = sharedPref!!.getString("filter", "outthree")
            tv!!.text = filfile
            musicSrv!!.setFilter(this@MainActivity, filfile)

            musicSrv!!.setActivity(this@MainActivity)
            musicBound = true

        }

        override fun onServiceDisconnected(name: ComponentName) {
            musicBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (playIntent == null) {
            playIntent = Intent(this, MusicService::class.java)
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE)
            startService(playIntent)
        }
    }

    fun songPicked(view: View) {
        tv!!.text = view.tag.toString()
        musicSrv!!.setSong(Integer.parseInt(view.tag.toString()))
        musicSrv!!.playSong()
    }

    override fun onDestroy() {
        stopService(playIntent)
        musicSrv = null
        super.onDestroy()
    }

    private fun checkPermissons() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 以前に許諾して、今後表示しないとしていた場合は、ここにはこない
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_STORAGE_PERMISSION)
        } else {
            //  許諾されているので、やりたいことをする
            getSongList()
            registerListView()
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                //拒否

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {

                    AlertDialog.Builder(this)
                            .setTitle("パーミッション取得エラー")
                            .setMessage("再試行する場合は、再度Requestボタンを押してください")
                            .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                                // サンプルのため、今回はもう一度操作をはさんでいますが
                            }
                            .create()
                            .show()

                } else {

                    AlertDialog.Builder(this)
                            .setTitle("パーミッション取得エラー")
                            .setMessage("今後は許可しないが選択されました。アプリ設定＞権限をチェックしてください（権限をON/OFFすることで状態はリセットされます）")
                            .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                                //openSettings();
                            }
                            .create()
                            .show()
                }
            } else {
                //許可
                getSongList()
                registerListView()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    fun getSongList() {
        //retrieve song info
        val musicResolver = contentResolver
        val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        tv!!.text = musicUri.toString()
        val musicCursor = musicResolver.query(musicUri, null, null, null, null)
        tv!!.text = musicUri.toString()

        if (musicCursor != null && musicCursor.moveToFirst()) {
            //get columns

            val titleColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
            val idColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
            val artistColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
            //add songs to list
            do {
                val thisId = musicCursor.getLong(idColumn)
                val thisTitle = musicCursor.getString(titleColumn)
                val thisArtist = musicCursor.getString(artistColumn)
                songList!!.add(Song(thisId, thisTitle, thisArtist))
            } while (musicCursor.moveToNext())

            musicCursor.close()
        }

        Collections.sort(songList!!) { a, b -> a.title.compareTo(b.title) }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
