# 你的 App 还能在后台启动 Activity 吗（非 AndroidQ 适配）
**先说结论：在 Oppo、vivo、小米等机型上如果你没有开启后台弹出界面权限，当你的 App 处于后台时，将无法通过 `startActivity` 方式启动页面**。

这一权限在不同型号的手机中的名称不同，以下我们统称为后台弹出界面权限。对业务背景和问题定位不感兴趣的话，可以直接拉到问题解决这一段落。
## 一、业务背景
我们的 App 中有这样一个场景：当收到推送或者长连接消息的时候，需要启动一个 Activity 来展示相关的信息，在 Activity 展示后回复服务端 ACK 表示页面正常展示。

统计数据显示，ACK 与 消息发送总量的比例只有 80% 左右，产品经理不干了：“你这不行啊，没法开展业务啦，巴拉巴拉...”。

## 二、问题定位

为了更详细的定位问题，我们重新梳理了代码流程，对一些关键节点（推测可能造成异常，数据丢失的地方）进行埋点。**结果：从线上埋点数据来看，我们调用了 `startActivity` 方法，但是确没有任何在目标页面 `onCreate` 方法中的埋点数据**。

碰巧这时候产品同学找到我：“有一个新的业务也需要在收到长连接消息的时候展示页面...”，希望我给他展示一下已有的功能。

这个简单啊，我把测试机拿给产品：“你盯着屏幕，我发一条消息，你就能看见展示的页面了”。之后我在云平台上发了一条长连接消息，结果过了半天也没见有页面展示，真是尴尬，不过也因此复现了**收到长连接消息却没有页面展示**这一问题。

反复试了几次发现，当 App 在前台可见时是可以展示页面的，但是当按下 Home 键返回桌面，App 处于后台时，收到再多消息也没有了反应。

## 三、问题分析
现在有一个可以明确的点是，在我的测试机上（vivo Z1），App 处于后台时，收到消息无法展示页面，说白了就是在后台无法通过 `startActivity` 的方式来启动一个新页面

这个时候我们需要考虑：
1. 该问题和机型有没有关系
2. 该问题和 Android 系统版本有没有关系
3. 该问题是不是只在 App 处于后台时发生


### 机型问题
后期通过我们更详细的数据聚合分析，发现此类问题大量出现在 OPPO，vivo 手机上，也有少量的小米机型。

我从测试那里拿了一些主流的机型和用户使用比较多的机型进行测试，发现 OPPO，vivo 的手机确实有这个问题，华为和三星倒没这个问题。
### Android 系统版本问题
通过数据分析发现，发生此问题的手机 Android 系统版本分布很均匀，从 Android 6.0 到 Android 9.0 都有发生（当时 Android Q 还没有推出），因此和 Android 系统版本应该没有关系。

### App 前后台问题
在对少量的异常数据和重复消息进行过滤后发现，在调用 `startActivity` 方法的时候，App 确实都处于后台。

在测试的过程中发现有一台 OPPO 手机可以正常展示，我们通过对比这台 OPPO 手机和其他 OPPO 手机的各种开关、配置后发现，在这台 OPPO 手机设置中打开了一个叫做 **xxx** 的权限开关，我们又去查看 vivo 和 小米手机发现都有类似的权限开关。


## 四、问题解决
我对网上提到的一些方法和自己的一些想法进行一一验证，测试机型为：
* OPPO R17
* vivo Z1
* 小米  6

### 测试代码
5s 后将启动 StartFromBackActivity 这个 Activity，测试的时候需要手动将 App 切换到后台。
``` kotlin 
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onDelayStartClick(view: View) {
        val intent = Intent(this@MainActivity, StartFromBackActivity::class.java)

        view.postDelayed({
            Log.d("realxz","startActivity")
            startActivity(intent)
        }, 5000)
    }
}

class StartFromBackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_from_back)
        Log.d("realxz", "StartFromBackActivity onCreate")
    }
}
```

### 前台 Service 启动
我试想构建一个**前台 Service**，能否绕过这个限制，在这个 Service 的 onStartCommand 方法中延迟启动 Activity：

``` java
class ForegroundService : Service() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("realxz", "onCreate()")
        createNotificationChannel(this, "Test", "Test", NotificationManager.IMPORTANCE_HIGH)
        val builder =
            Notification.Builder(this, "Test").setSmallIcon(R.drawable.ic_launcher_background)
        startForeground(1, builder.build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("realxz", "onStartCommand")
        Handler().postDelayed({
            startActivity(Intent(this, StartFromBackActivity::class.java))
        }, 5000)
        return super.onStartCommand(intent, flags, startId)
    }
}
```
通过 adb shell dumpsys activity services com.example.realxz 命令查看当我们的 Service 确实是一个前台 Service
![前台 Service][1]
但是页面没有启动成功，这个方案 pass 掉，通过 logcat 日志可以发现我们启动页面的行为被系统拦截了：
![OppoAppStartupManager][2]

### PendingIntent 启动
>[方案来源](https://www.jianshu.com/p/47e67e03cba2)

通过 PendingIntent 的 `send()` 方法来执行相关操作
``` java
/**
     * Perform the operation associated with this PendingIntent.
     *
     * @see #send(Context, int, Intent, android.app.PendingIntent.OnFinished, Handler)
     *
     * @throws CanceledException Throws CanceledException if the PendingIntent
     * is no longer allowing more intents to be sent through it.
     */
    public void send() throws CanceledException {
        send(null, 0, null, null, null, null, null);
    }
```

简单的通过一个点击事件来延迟发送一个 PendingIntent:
``` java
    fun onPendingClick(view: View) {
        view.postDelayed({
            val intent = Intent(this, StartFromBackActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
            pendingIntent.send()
            Log.e("realxz","pendingIntent.send()")
        }, 10 * 1000)
    }
```
此方案在 OPPO R17 上可行，但是在 vivo Z1 上失败了，logcat 日志显示启动 StartFromBackActivity 是不允许的，原因是 App 在 fobid 这个列表中，这应该是禁止后台启动的应用列表。
![PermissionNotificationService][3]

### 播放音频
以前做保活的时候，我们尝试通过播放一段无声的音频，期望能提高 App 进程的优先级，我们来尝试一下这么操作对于启动 Activity 有没有帮助
``` java
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
```
此方案同样在 OPPO R17 上可行，vivo Z1 上仍然不行，logcat 日志与之前相同。看上去 OPPO 的限制要小一点，而 vivo 的限制更严格一点。

### 尝试获取系统权限
我们换了一种想法，能否通过 Hack 的方式来修改手机的权限设置（vivo、小米等厂商并没有提供获取相关权限的 API），上网搜了一下，发现有人研究过这个问题，以 vivo Z1 为例：

>[Android 破解vivo手机权限管理](https://juejin.im/post/5d24a23a51882502e5233571)

#### 获取 vivo 系统权限设置的 APK
打开手机到具体的权限设置页面，通过 **adb** 命令，`adb shell dumpsys activity top` 来获取当前栈顶 Activity 的包名相关信息，如图可知 vivo Z1 这款手机的权限管理的包名为 **PermissionManager**。
![15751861136670.jpg-262.8kB][4]

然后通过 Android Studio 的 Device File Explorer 工具来打开 **PermissionManager** 路径，将需要的 **apk**、**vdex**、**odex** 文件拷贝出来。
![Device File Explorer][5]

#### 通过 **jadx-gui** 打开 apk 文件
按照文章中所说，打开 apk 的清单文件，可以找到如下的权限定义和 Provider 声明。
``` java
<permission android:label="provider write pomission"
android:name="com.vivo.permissionmanager.provider.write"
android:protectionLevel="signatureOrSystem"/>

<provider android:name=".provider.PermissionProvider"
android:writePermission="com.vivo.permissionmanager.provider.write"
android:exported="true"
android:authorities="com.vivo.permissionmanager.provider.permission"/>
```
可以看见，只有系统应用或者和系统应用有相同签名的应用，才能够有写入数据的权限，到这里基本上可以确定这个方案 GG 了。

#### 通过 **jadx-gui** 打开 dex 文件
![dex][6]

>[Android 破解vivo手机权限管理](https://juejin.im/post/5d24a23a51882502e5233571) 这篇文章的作者在 Github 上提供了相关代码来进行测试，我对代码进行简单的修改，来测试我们需要读取的权限

``` java
public static int getVivoApplistPermissionStatus(Context context) {
        Uri uri2 = Uri.parse("content://com.vivo.permissionmanager.provider.permission/start_bg_activity");
        try {
            Cursor cursor = context.getContentResolver().query(uri2, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (cursor.moveToNext()) {
                    String pkgName = cursor.getString(cursor.getColumnIndex("pkgname"));
                    String currentState = cursor.getString(cursor.getColumnIndex("currentstate"));
                    Log.e("realxz", "----------------" + "\n");
                    Log.e("realxz", "pkg name is  " + pkgName);
                    Log.e("realxz", "current state is " + currentState);
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return -1;
    }
```
通过日志可以看到，我们 App 的 state 为 1，这个时候**后台启动 App 权限为关闭状态**，手动打开权限后，这个 state 会变为 0。

![start_bg_activity][7]

如果我们尝试去修改 Provider 的内容时：
``` java
 Uri uri2 = Uri.parse("content://com.vivo.permissionmanager.provider.permission/start_bg_activity");
 ContentValues contentValues = new ContentValues();
 contentValues.put("currentstate", 0);
 context.getContentResolver().update(uri2, contentValues, "pkgname=?", new String[]{"com.example.realxz.startfromback"});
```

可以在 logcat 中看到以下崩溃信息：
``` java
2019-12-01 17:20:11.641 5050-5068/? E/DatabaseUtils: Writing exception to parcel
    java.lang.SecurityException: Permission Denial: writing com.vivo.permissionmanager.provider.PermissionProvider uri content://com.vivo.permissionmanager.provider.permission/start_bg_activity from pid=20117, uid=10299 requires com.vivo.permissionmanager.provider.write, or grantUriPermission()
        at android.content.ContentProvider.enforceWritePermissionInner(ContentProvider.java:851)
        at android.content.ContentProvider$Transport.enforceWritePermission(ContentProvider.java:593)
        at android.content.ContentProvider$Transport.update(ContentProvider.java:390)
        at android.content.ContentProviderNative.onTransact(ContentProviderNative.java:211)
        at android.os.Binder.execTransact(Binder.java:708)
```

那么这个方案到这也就被 Pass 了。
## 五、临时方案
以上的方案全部以失败告终，这时我们已经准备和产品商量改变业务模式来避免这个问题，这个时候我们有了一个新的想法，既然在后台无法启动 App，那有没有办法将 App 移动或者说切换到前台呢？

>[Android 将后台应用切换到前台](https://blog.csdn.net/wangmx1993328/article/details/83007883)

### 判断应用是否在前台

``` java
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
```
1. ActivityManager 的 getRunningAppProcesses 方法，会返回一个在当前设备上运行的应用进程列表，或者返回 null 而不会返回一个 Empty List，经测试发现，此方法仅能获取自己的 App 信息
2. 通过比对进程的优先级，来判断 App 是否运行在前台，importance 是一个枚举值，定义了我们 App 是在前台运行，或是在后台运行，又或是有前台 Service 在运行：

   ```java
   @IntDef(prefix = { "IMPORTANCE_" }, value = {
                IMPORTANCE_FOREGROUND,
                IMPORTANCE_FOREGROUND_SERVICE,
                IMPORTANCE_TOP_SLEEPING,
                IMPORTANCE_VISIBLE,
                IMPORTANCE_PERCEPTIBLE,
                IMPORTANCE_CANT_SAVE_STATE,
                IMPORTANCE_SERVICE,
                IMPORTANCE_CACHED,
                IMPORTANCE_GONE,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Importance {}
   ```
### 将应用切换至前台

``` java
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
```
1. ActivityManager 的 getRunningTasks 方法虽然已标注为 Deprecated，但是仍能返回调用者自己，也就是我们自己 App 的 Task 信息
2. 然后调用 moveTaskToFront 方法，将我们 Task 移动到栈顶，按照方法的注释所说 “Ask that the task associated with a given task ID be moved to the front of the stack, so it is now visible to the user.” 这样做我们的 App 就可以对用户可见了

### 检查消息
经过上面的操作：如果应用在前台，那么我们可以直接启动 Activity，如果应用不再前台，我们可以通过 ActivityManager 提供的方法将 App 移动到前台。

在这之后我们有两种方式来启动页面
1. 在基类的 onResume 方法中，来编写读取有效消息，并启动页面的逻辑
2. 轮询检测，在收到消息后采用轮训的方式来将 App 切换到前台，并启动页面

我采用的是轮训的方式：
``` java
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

```

这种方式通过了我手中所有的测试机的测试，有个小问题是，Vivo 手机调用一次 moveAppToFront 方法就可以切换到前台，Oppo R17 的表现不太固定，有时候可能需要调用三次。

在我们的项目中，我配置的启动次数是 3 次。文章开头所说 “ACK 与 消息发送总量的比例只有 80% 左右”，这一比例在采用这种方式后上升到了 97% 左右。

## 六、Android Q
**以上所有方式在 Android Q 均失效，Google 在 Android Q 中增加了[从后台启动 Activity 的限制](https://developer.android.com/guide/components/activities/background-starts?hl=zh-CN)，无法访问的话，可能需要科学上网”**。


目前我们的后台统计还没有发现使用 Android Q 设备的用户（用户群体比较特殊），但不可避免的随着时间的推移，越来越多的用户更新自己的设备，这一问题会彻底暴露，看样子只能通过其他的表现形式来实现这一功能了。

不知道大家是否用这种强制提醒的业务需求，在 Android Q 下又是怎么适配或实现的呢？


  [1]: http://static.zybuluo.com/xiezhen/n29m4bgbi3p3l74unvf6qpls/15749311007568.jpg
  [2]: http://static.zybuluo.com/xiezhen/isi11ylqjj45ky3f23ut1xw4/15749312226423.jpg
  [3]: http://static.zybuluo.com/xiezhen/z9acl2adq31bwoqd26tjr80l/15749334470000.jpg
  [4]: http://static.zybuluo.com/xiezhen/mppesvstkf6secmwlx323rs5/15751861136670.jpg
  [5]: http://static.zybuluo.com/xiezhen/rbn7zbpo20o2rdbaz4561lga/15751859627762.jpg
  [6]: http://static.zybuluo.com/xiezhen/zss4wey733ff4woi3ovtzd78/15751881348164.jpg
  [7]: http://static.zybuluo.com/xiezhen/inx69rkmfcb3do4571u5gubf/15751900603059.jpg