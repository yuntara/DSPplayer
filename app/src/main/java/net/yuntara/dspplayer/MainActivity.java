package net.yuntara.dspplayer;

import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.content.PermissionChecker;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import android.net.Uri;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.Cursor;
import android.widget.ListView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.MenuItem;
import android.view.View;
import net.yuntara.dspplayer.MusicService.MusicBinder;

public class MainActivity extends AppCompatActivity {

    private ArrayList<Song> songList;
    private ListView songView;
    private TextView tv;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound=false;
    private SeekBar seek ;
    private boolean seeking = false;
    private int REQUEST_CODE_STORAGE_PERMISSION = 0x01;
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_shuffle:
                //shuffle
                Collections.shuffle(songList);
                refreshSongView();
                musicSrv.setList(songList);

                musicSrv.Shuffle();

                break;
            case R.id.action_end:
                stopService(playIntent);
                musicSrv=null;
                System.exit(0);
                break;
        }
        return super.onOptionsItemSelected(item);

    }

    public void onPrevClicked(View view){
        musicSrv.prev();
    }
    public void onNextClicked(View view) {
        musicSrv.next();
    }
    public void onPauseClicked(View view){
        musicSrv.pause();
    }
    public void onPlayClicked(View view){
        musicSrv.play();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }
    public void setPlayingPosition(float prog){
        if(!seeking) {
            seek.setProgress((int) (prog * 1000));
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());
        seek = (SeekBar) findViewById(R.id.seekBar);

        seek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar seekBar,
                                                  int progress, boolean fromUser) {
                        // ツマミをドラッグしたときに呼ばれる
                        //tv0.setText("設定値:"+sb0.getProgress());
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // ツマミに触れたときに呼ばれる
                        seeking = true;
                    }

                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // ツマミを離したときに呼ばれる
                        musicSrv.setPosition( (seekBar.getProgress())/1000.0f);
                        seeking = false;
                    }
                }
        );

        songView = (ListView)findViewById(R.id.song_list);
        songList = new ArrayList<Song>();
        checkPermissons();

        //getSongList();

        //ByteBuffer bf = new ByteBuffer();



    }
    private void refreshSongView(){
        SongAdapter adapter = (SongAdapter)songView.getAdapter();
        adapter.refreshList(songList);
        //adapter.getFilter().filter(s);//フィルタ実行。ソートもここで実行する。再描画も担当する

    }
    private void registerListView(){
        //songView.setSelection(0);
        SongAdapter songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);
    }
    public void setPlayingSongPos(int songPos){
        //songView.
        //songView.setSelection(songPos);
        SongAdapter adapter = (SongAdapter)songView.getAdapter();
        adapter.setSelected(songPos);
        //adapter.getItem()
    }
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder)service;
            //get service
            musicSrv = binder.getService();
            //pass list
            musicSrv.setList(songList);
            musicSrv.loadFilter(MainActivity.this);
            musicSrv.setActivity(MainActivity.this);
            musicBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    public void songPicked(View view){
        tv.setText(view.getTag().toString());
        musicSrv.setSong(Integer.parseInt(view.getTag().toString()));
        musicSrv.playSong();
    }
    @Override
    protected void onDestroy() {
        stopService(playIntent);
        musicSrv=null;
        super.onDestroy();
    }

    private void checkPermissons(){
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            // 以前に許諾して、今後表示しないとしていた場合は、ここにはこない
            String[] permissions = new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_STORAGE_PERMISSION);
        } else {
            //  許諾されているので、やりたいことをする
            getSongList();
            registerListView();
        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length != 1 ||
                    grantResults[0] != PackageManager.PERMISSION_GRANTED) {
               //拒否

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {

                    new AlertDialog.Builder(this)
                            .setTitle("パーミッション取得エラー")
                            .setMessage("再試行する場合は、再度Requestボタンを押してください")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    // サンプルのため、今回はもう一度操作をはさんでいますが

                                }
                            })
                            .create()
                            .show();

                } else {

                    new AlertDialog.Builder(this)
                            .setTitle("パーミッション取得エラー")
                            .setMessage("今後は許可しないが選択されました。アプリ設定＞権限をチェックしてください（権限をON/OFFすることで状態はリセットされます）")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    //openSettings();
                                }
                            })
                            .create()
                            .show();
                }
            } else {
                //許可
                getSongList();
                registerListView();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    public void getSongList() {
        //retrieve song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        tv.setText(musicUri.toString());
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
        tv.setText(musicUri.toString());

        if(musicCursor!=null && musicCursor.moveToFirst()){
            //get columns

            int titleColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.ARTIST);
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                songList.add(new Song(thisId, thisTitle, thisArtist));
            }
            while (musicCursor.moveToNext());

            musicCursor.close();
        }

        Collections.sort(songList, new Comparator<Song>(){
            public int compare(Song a, Song b){
                return a.getTitle().compareTo(b.getTitle());
            }
        });
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
