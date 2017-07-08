package net.yuntara.dspplayer

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView

import java.util.ArrayList

class Main2Activity : AppCompatActivity() {
    private var filList: ArrayList<String>? = null
    internal var intent: Intent? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        val filview = findViewById(R.id.filter_list) as ListView
        filList = ArrayList<String>()
        filList!!.add("out")
        //filList!!.add("outtwo")
        filList!!.add("outthree")
        //filList!!.add("outfour")
        //filList!!.add("outfive")
        //filList!!.add("outsix")
        val adapter = ArrayAdapter(this, R.layout.rowtext, filList!!)
        filview.adapter = adapter
        intent = Intent()
        filview.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            //リスト項目が選択された場合(端末の十字キー等でリストの項目がフォーカスされた時)の処理
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                // ここに処理を記述します。
                intent!!.putExtra("FILTERNAME", filList!![position])
                setResult(2481, intent)
                finish()
            }

            //リスト項目が何も選択されない場合の処理
            //（初期状態では、呼び出されず、選択がなくなった時に呼ばれる）
            override fun onNothingSelected(adapterview: AdapterView<*>) {

            }
        }
        filview.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            intent!!.putExtra("FILTERNAME", filList!![position])
            setResult(2481, intent)
            finish()
        }
    }

    fun onFilterClick(view: View) {

    }
}
