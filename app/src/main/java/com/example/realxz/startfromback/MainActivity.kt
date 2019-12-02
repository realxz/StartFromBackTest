package com.example.realxz.startfromback

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onDelayStartClick(view: View) {
        val intent = Intent(this@MainActivity, StartFromBackActivity::class.java)

        view.postDelayed({
            Log.d("realxz", "startActivity")
            startActivity(intent)
        }, 2000)
    }

    fun onServiceClick(view: View) {
        view.postDelayed({
            startService(Intent(this@MainActivity, ForegroundService::class.java))
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this@MainActivity, ForegroundService::class.java))
    }

    fun onPendingClick(view: View) {
        view.postDelayed({
            val intent = Intent(this, StartFromBackActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
            pendingIntent.send()
            Log.e("realxz", "pendingIntent.send()")
        }, 10 * 1000)
    }

    fun onMusicClick(view: View) {
        val mediaPlayer = MediaPlayer.create(this, R.raw.meglive_mouth_open)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

        view.postDelayed({
            val intent = Intent(this, StartFromBackActivity::class.java)
            startActivity(intent)
            Log.e("realxz", "onMusicClick startActivity")
        }, 10 * 1000)
    }

    private fun isAppRunningForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcessList = activityManager.runningAppProcesses ?: return false

        Log.e("realxz", "running app process list size is ${runningAppProcessList.size}")
        runningAppProcessList.forEach {
            Log.e(
                "realxz",
                "running app process name is ${it.processName} and importance is ${it.importance}"
            )
            if (it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && it.processName == context.applicationInfo.processName
            ) {
                return true
            }
        }
        return false
    }

    private fun moveAppToFront(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.getRunningTasks(100)
        for (taskInfo in runningTasks) {
            if (taskInfo.topActivity!!.packageName == context.packageName) {
                activityManager.moveTaskToFront(taskInfo.id, 0)
                break
            }
        }
    }

    @SuppressLint("CheckResult")
    fun onForegroundClick(view: View) {
        Observable.intervalRange(1, 3, 3, 3, TimeUnit.SECONDS)
            .subscribe(object : Observer<Long> {
                lateinit var disposable: Disposable
                override fun onSubscribe(d: Disposable) {
                    disposable = d
                }

                override fun onNext(t: Long) {
                    Log.e("realxz", "interval long value is $t")
                    val isRunningForeground = isAppRunningForeground(this@MainActivity)
                    if (isRunningForeground) {
                        disposable.dispose()
                        // todo 读取缓存数据，并启动页面
                    } else {
                        moveAppToFront(this@MainActivity)
                    }
                }

                override fun onComplete() {
                }

                override fun onError(e: Throwable) {
                }

            })
    }

}
