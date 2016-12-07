package net.yuntara.dspplayer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class Main2Activity extends AppCompatActivity {
    private ArrayList<String> filList;
    Intent intent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ListView filview =  (ListView)findViewById(R.id.filter_list);
        filList = new ArrayList<String>();
        filList.add("out");
        filList.add("outtwo");
        filList.add("outthree");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,R.layout.rowtext, filList);
        filview.setAdapter(adapter);
        intent = new Intent();
        filview.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                                              //リスト項目が選択された場合(端末の十字キー等でリストの項目がフォーカスされた時)の処理
                                              @Override
                                              public void onItemSelected(AdapterView<?> parent, View view,int position, long id) {
                                                  // ここに処理を記述します。
                                                 intent.putExtra("FILTERNAME",filList.get(position));
                                                  setResult(2481,intent);
                                                  finish();
                                              }

                                              //リスト項目が何も選択されない場合の処理
                                              //（初期状態では、呼び出されず、選択がなくなった時に呼ばれる）
                                              @Override
                                              public void onNothingSelected(AdapterView<?> adapterview) {

                                              }
                                          }

        );
        filview.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override

            public void onItemClick(AdapterView<?> parent, View view,int position, long id) {
                intent.putExtra("FILTERNAME",filList.get(position));
                setResult(2481,intent);
                finish();
            }
        });
    }
    public void onFilterClick(View view){

    }
}
