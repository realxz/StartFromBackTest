package com.example.realxz.startfromback

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class StartFromBackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_from_back)
        Log.d("realxz", "StartFromBackActivity onCreate")
    }
}
