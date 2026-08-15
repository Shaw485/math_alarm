# 极简数学题闹钟 - 项目交接上下文（Agent Handoff）

> **版本**：v51.0
> **最后更新**：2026-08-15
> **GitHub**：https://github.com/Shaw485/math_alarm

---

## 一、用户画像与偏好（必须遵守）

### 用户身份
- **身份**：字节跳动员工，编程新手小白
- **沟通语言**：中文
- **需要**：极其详细的步骤指导、概念解释、手把手教学式引导

### 视觉审美偏好
- 纯黑背景 `#000000`，高饱和橙 `#FF9F0A` 主色调
- 严格遵循 Apple HIG 风格
- 所有交互必须有：弹性缩放（scale-95）+ 背景高亮反馈 + 透明度渐变
- 追求精致动画效果和毛玻璃质感

### 工作流强制规范
1. **改动记录必写**：每次代码改动后，必须在项目根目录 `改动记录.txt` 追加版本号（如 52.0）和改动说明
2. **代码展示格式**：三步教学式
   - ① 改动目标 + 根因分析
   - ② 文件路径 + 代码对比
   - ③ 逻辑说明 + 操作步骤
3. **调试风格**：日志驱动调试，要求日志信息量大、包含完整上下文（KV 格式），一次运行定位多类问题
4. **响应末尾**：追加准确的「本次token消耗 xx」

### 合规约束
- 在涉及公司内部项目时，倾向于使用纯脚本或官方工具，避免未经验证的第三方开源框架

---

## 二、项目基础信息

| 项 | 值 |
|----|----|
| 项目名 | 极简数学题闹钟 App |
| 包名 | `com.mira.mathalarm` |
| 技术栈 | Android + Kotlin + Jetpack Compose + DataStore + AlarmManager |
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 (Android 14) |
| compileSdk | 34 |
| Kotlin Compiler Extension | 1.5.8 |
| 签名 | `mathclock-release.jks`（本地，别名 mathclock，密码在 build.gradle.kts） |
| 分支 | main |

---

## 三、核心业务规则

### 3.1 数学题规则
- 格式：`a*b + c*d`
- **数字范围严格限制**：a, b, c, d ∈ [3, 9]（v24.0 从 1-9 修正）
- 先乘后加，答错立即刷新新题，铃声不中断
- 答对才停止

### 3.2 铃声配置（v23定名）
| 槽位 | 名称 | 音量系数 | 资源文件 | 来源 |
|------|------|---------|----------|------|
| 1 | 清晨 | 1.0（系统上限） | `ringtone_qingchen.mp3` | 小米MIUI晨露Dewdrops |
| 2 | 风来 | 0.70 | `ringtone_shanlan.mp3` | 小米Breeze |
| 3 | 钢琴 | 0.85 | `ringtone_shuguang.mp3` | 谷歌Pixel Zen |

⚠️ **关键修复**：MediaPlayer 必须用 `openRawResourceFd() + prepare()` 方式，不能用 `MediaPlayer.create()`（部分MP3会无声）

### 3.3 时间选择器
- 基于 `LazyColumn` 实现，三行显示
- 滑动灵敏度：0.4
- 特性：无限滚动、自动吸附、随距离变化的透明度/缩放效果
- **剩余时间提示规则**：
  - 普通状态滑动滚轮：不更新
  - 点击「修改闹钟」进入编辑模式：立即更新并实时随滑动刷新

### 3.4 状态机（核心！）
```
NOT_SET(未设置)
    ↓ confirmSetAlarm
ACTIVE(生效中)
    ↓ 到点触发
RINGING(响铃中)
    ↓ 答对题 / 10分钟超时 / (异常重启需判断证据)
NOT_SET(未设置)

ACTIVE(生效中)
    ↓ disableAlarm
DISABLED(本次闹钟已失效)
    ↓ 停留3秒自动回流
NOT_SET(未设置)
```

⚠️ **v51 关键修复（BugB）**：App冷启动时读到 DS=RINGING **不能无脑清**！
必须检查两条证据：
- 证据①：SP `createdTrigger == dsTrigger && triggerTime > 0`（RingingActivity 真 onCreate）
- 证据②：`PermissionChecker.canDrawOverlays() == true`（Overlay在挂/兜底中）
任一成立 → **KEEP-RINGING**（保留响铃状态，不清服务）；两条都不成立 → 才是真残留，clearAlarm()

---

## 四、国产ROM终极防拦截机制（最核心！）

这是整个项目踩坑最多、积累最深的部分。小米 HyperOS / 华为 / OPPO / vivo 等国产ROM会**静默拦截后台 startActivity**（不抛异常，返回成功，但Activity永远不会onCreate）。

解决方案是 **A1 + A2 + A3 三层兜底防线**：

### A3层：startActivities 合成栈（Activity拉起重试）
- 核心函数：`AlarmReceiver.bringUpRingingActivity(context, triggerTime, attempt)`
- 每次用 `context.startActivities(arrayOf(MainActivity底栈, RingingActivity顶栈))` 构造完整返回栈（国产ROM对合成栈更宽容）
- **attempt=1/2/3 三种FLAG全家桶，必须全部执行！不能break！**（小米静默拦截不抛异常，attempt=1"成功"不代表真拉起）
  - attempt=1：`NEW_TASK | CLEAR_TOP | SINGLE_TOP`（保守组合）
  - attempt=2：`NEW_TASK | REORDER_TO_FRONT | RESET_TASK_IF_NEEDED`（重置任务）
  - attempt=3：`NEW_TASK | CLEAR_TASK | NEW_DOCUMENT | MULTIPLE_TASK`（全新独立文档栈）

**触发时机（共9次拉起尝试）**：
1. AlarmReceiver.onReceive 一开始循环 ×3次
2. Ping-pong 500ms 检查失败后再循环 ×3次
3. RingtoneService 1.5s 兜底循环 ×3次

### A2层：5步权限引导（用户授权）
权限顺序：
1. 通知权限（POST_NOTIFICATIONS，Android 13+）
2. 全屏通知权限（USE_FULL_SCREEN_INTENT，Android 14+）
3. 忽略电池优化（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，防Doze杀进程）
4. **悬浮窗权限**（SYSTEM_ALERT_WINDOW / ACTION_MANAGE_OVERLAY_PERMISSION）→ v48新增，A1层的前提
5. 后台保活 & 自启动 → v45新增，适配各ROM专属自启动页：
   - 小米/HyperOS：`miui.intent.action.OP_AUTO_START`
   - 华为/荣耀：`com.huawei.systemmanager.optimize.process.ProtectActivity`
   - OPPO/一加/realme：`com.coloros.safecenter.startupapp.StartupAppListActivity`
   - vivo/iQOO：`com.vivo.permissionmanager.activity.BgStartUpManagerActivity`
   - 兜底：应用详情页

⚠️ **v47修复**：电池优化授权后 Provider 写入有 150~400ms 延迟，必须用异步 SharedFlow 模型 + delay(450ms) 二次校验才能自动推进step

### A1层：WindowManager TYPE_APPLICATION_OVERLAY 全屏悬浮答题页（终极核武器）
- 文件：`app/src/main/java/com/mira/mathalarm/overlay/RingingOverlayController.kt`
- 触发时机：RingtoneService 2.5s 检查，没检测到RingingActivity.onCreate **且** canDrawOverlays=true → 直接 show()
- 不依赖Activity启动机制，通过系统窗口层直接绘制UI
- WindowManager.LayoutParams：
  - type：`TYPE_APPLICATION_OVERLAY`（Android 8.0+）
  - flags：`FLAG_SHOW_WHEN_LOCKED | FLAG_DISMISS_KEYGUARD | FLAG_TURN_SCREEN_ON | FLAG_KEEP_SCREEN_ON`（所有版本都加，系统不认识就忽略）
  - 尺寸：MATCH_PARENT × MATCH_PARENT
  - screenBrightness：1.0f（屏幕亮度拉满）
- 内部 ComposeView 挂载 RingingScreen，完全复用答题UI + RingingViewModel倒计时/校验/答错红色提示
- 答对题时 clearAlarmAndHide() 三件套：
  1. hide() → WindowManager.removeViewImmediate()
  2. `RingtoneService.forceStopRingingAndNotify()` → 停铃声+前台服务+双通知+SP标记
  3. `dataStore.clearAlarm()` → 状态回 NOT_SET
- 亮屏&解锁：FULL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP + KeyguardLock.disableKeyguard()
- 音量：STREAM_ALARM 拉到 max

### Activity启动配置（v48）
AndroidManifest.xml 中 RingingActivity：
```xml
android:launchMode="singleInstance"
android:taskAffinity="${applicationId}.ringing"
android:showOnLockScreen="true"
android:showWhenLocked="true"
android:turnScreenOn="true"
android:excludeFromRecents="true"
```
独立任务栈更易被国产ROM放行。

---

## 五、响铃链路细节

### 5.1 AlarmScheduler 双保险通路
- 主通路：`setAlarmClock()`（系统闹钟栏最高优先级，显示在锁屏闹钟图标）
- 兜底通路：`setExactAndAllowWhileIdle()`（独立requestCode=1002，Doze模式独立唤醒）
- 设置后立刻反查 `alarmManager.nextAlarmClock` 对比 triggerTime，差>60秒打警告日志
- cancel/disable 时同时取消双PendingIntent + 调用 `forceStopRingingAndNotify()`

### 5.2 音量双保险（v49修复"用户说没听见"）
**根因**：USAGE_ALARM 在部分小米HyperOS上映射到STREAM_MUSIC媒体流（用户媒体静音了就听不到）
修复：
1. startRinging() 开头强制把 `STREAM_ALARM`（系统闹钟独立音量流）拉到 max
2. 同时把 `STREAM_MUSIC` 拉到 85%（兜底）
3. 双API绑定音频流：先废弃的 `setAudioStreamType(STREAM_ALARM)` → 再新API `setAudioAttributes(USAGE_ALARM, CONTENT_TYPE_SONIFICATION)`
4. volumeScale 强制置为 1.0

### 5.3 MediaPlayer创建顺序（必须和RingtonePickerPopup一致）
```kotlin
// 顺序不能乱！
val player = MediaPlayer()
@Suppress("DEPRECATION")
player.setAudioStreamType(AudioManager.STREAM_ALARM)  // 老API兜底
player.setAudioAttributes(
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)  // 注意：不是MUSIC
        .build()
)
player.isLooping = true
val afd = resources.openRawResourceFd(resId)
player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
afd.close()
player.prepare()  // 必须prepare！不能用create()
player.setVolume(volume, volume)
player.start()
// 失败降级：MediaPlayer.create(this, resId) 兜底
```
主线程同步执行，不能放Dispatchers.IO！

### 5.4 重入保护（v51 BugA修复"响一下就没"）
**根因**：HyperOS双通路setAlarmClock+setExact都在1.6s内触发，startId=1的MP刚响就被startId=2进来的旧MP释放干掉
修复：ACTION_START_RINGING首行幂等判定，两条任一跳过：
1. `mediaPlayer?.isPlaying == true`
2. 同一triggerTime 且 距上次启动 < 3s
记录 `lastStartedTrigger / lastStartedAtMs`，跳过直接打SKIP-DUP=Y

### 5.5 Handler防泄露（v51 BugC修复）
所有 postDelayed 的 Runnable 必须持有引用：
- `scheduled1500`（1.5s A3兜底检查）
- `scheduled2500`（2.5s A1 Overlay兜底检查）
- `scheduledMP300`（MediaPlayer start后300ms状态dump）
- `scheduledMP1000`（MediaPlayer start后1000ms状态dump+自动重试）
- `timeoutRunnable`（10分钟超时兜底）
stopRinging() 第一时间全部 `removeCallbacks()` 并置null，同时重置 lastStartedTrigger/lastStartedAtMs

### 5.6 响铃时长
- MAX_RINGING_DURATION_MS = 10分钟（v10从2分钟改为10分钟）
- 到点自动 clearAlarm + stopRinging + stopSelf

---

## 六、关键文件索引

### 核心链路文件
| 文件 | 作用 |
|------|------|
| `app/src/main/AndroidManifest.xml` | 权限声明、Activity/Service/Receiver注册 |
| `app/src/main/java/com/mira/mathalarm/alarm/AlarmScheduler.kt` | 闹钟调度（双通路setAlarmClock+setExact） |
| `app/src/main/java/com/mira/mathalarm/alarm/AlarmReceiver.kt` | 广播接收器（WakeLock、全屏通知、A3拉起×9、Ping-pong校验） |
| `app/src/main/java/com/mira/mathalarm/service/RingtoneService.kt` | 前台响铃服务（MediaPlayer、1.5s/2.5s兜底、幂等防重入） |
| `app/src/main/java/com/mira/mathalarm/overlay/RingingOverlayController.kt` | A1终极兜底：WindowManager全屏悬浮答题页 |
| `app/src/main/java/com/mira/mathalarm/ui/ringing/RingingActivity.kt` | 正常的答题页Activity（onCreate写SP标记） |
| `app/src/main/java/com/mira/mathalarm/ui/home/HomeViewModel.kt` | 首页ViewModel（状态机、refreshAlarmState、RINGING判定逻辑） |
| `app/src/main/java/com/mira/mathalarm/permission/PermissionChecker.kt` | 5项权限检查+各ROM自启动页Intent适配 |
| `app/src/main/java/com/mira/mathalarm/ui/home/PermissionGuideDialog.kt` | 权限引导弹窗（异步SharedFlow推进step） |
| `app/src/main/java/com/mira/mathalarm/util/AppLogger.kt` | 全链路日志系统（6大dump工具+event KV日志） |
| `app/src/main/java/com/mira/mathalarm/data/AlarmDataStore.kt` | DataStore存储（状态、时间、铃声、引导标记） |

### UI组件文件
| 文件 | 作用 |
|------|------|
| `app/src/main/java/com/mira/mathalarm/ui/home/MainActivity.kt` | 首页Activity（onResume调refreshAlarmState+refreshPermissionStatus） |
| `app/src/main/java/com/mira/mathalarm/ui/home/WheelPicker.kt` | 三行无限滚动时间滚轮（灵敏度0.4，吸附效果） |
| `app/src/main/java/com/mira/mathalarm/ui/components/PressFeedback.kt` | Apple HIG按压反馈（scale+颜色+透明度） |
| `app/src/main/java/com/mira/mathalarm/ui/home/RingtonePickerPopup.kt` | 铃声选择弹窗（openRawResourceFd+prepare试听） |
| `app/src/main/java/com/mira/mathalarm/math/MathProblemGenerator.kt` | 数学题生成（a*b+c*d，数字3-9） |
| `app/src/main/java/com/mira/mathalarm/ui/theme/Color.kt` | 颜色定义（纯黑背景、橙色主色） |

---

## 七、日志系统（v50全链路增强）

### AppLogger核心API
```kotlin
AppLogger.d(tag, msg)                    // 普通debug日志
AppLogger.w(tag, msg, t)                 // 警告
AppLogger.e(tag, msg, t)                 // 错误
AppLogger.event(tag, "key1" to v1, "key2" to v2)    // KV事件日志
AppLogger.eventW(tag, "key" to v)        // KV警告事件日志

// 6大dump工具，每个都打印多项关键状态：
AppLogger.dumpIntent(tag, prefix, intent)              // Intent flags 19项中文名解码+所有extras
AppLogger.dumpAudioStats(tag, prefix, context)         // ALARM/MUSIC/RING/NOTIF 4流音量+ringerMode 9项
AppLogger.dumpWmParams(tag, prefix, params, view)      // Window type/flags 17项解码+View状态
AppLogger.dumpWakeLockScreen(tag, prefix, ctx, extraWl)// 屏亮/Doze/锁屏/勿扰 10项+WakeLock状态
AppLogger.dumpDataStoreAll(tag, prefix, ds, triggerHumanT)  // DS 11项+人类可读时间
AppLogger.dumpRingtoneAndPlayer(tag, prefix, opt, mp)  // 铃声名/isPlaying/curPos 8项
RingingActivity.dumpRuntimePrefs(tag, prefix, ctx)     // SP onCreate标记dump
```

### 日志落盘
- Application.onCreate()初始化，同时输出Logcat + 手机本地log文件
- 文件策略：找logs目录下最新的.log文件，<5MB就继承追加（头部标注重启分隔符），>5MB自动换新文件
- 首页右下角「导出日志」按钮：FileProvider.getUriForFile + Intent.ACTION_SEND 弹系统分享（微信/QQ直接发log）
- 启动header打印：制造商/产品/硬件/主板/指纹/开机时长/屏亮/Doze/锁屏/勿扰/响铃模式/三向音量

### 关键日志打点位置
- AlarmReceiver：onReceive首行dump全量 → WakeLock → A3循环每次attempt → Ping 500ms/1500ms每步 → 释放前后
- RingtoneService：onCreate → onStartCommand → startForeground → startRinging 0ms/300ms/1000ms → 1.5s/2.5s检查
- OverlayController：show前dump → 亮屏解锁后 → addView前后 → OnViewAttached/Detached → 300ms/800ms延迟dump
- HomeViewModel：refreshPermissionStatus → refreshAlarmState 每个分支决策 → saveAlarm/clearAlarm

---

## 八、历史踩坑记录（必读！）

### 已修复的致命Bug
1. **v12**：MediaPlayer.create() 对部分MP3无声 → 改用openRawResourceFd+prepare()
2. **v31**：AlarmReceiver启动Service/Activity从协程异步执行 → 改为onReceive同步执行，解决杀进程后不响铃
3. **v35**：setAlarm()没权限还返回成功写DataStore → 返回SET_ALARM_FAILED(-1)，触发时弹权限引导
4. **v36**：RingtoneService播放放Dispatchers.IO导致无声 → 改到Dispatchers.Main.immediate主线程同步执行
5. **v42**：FOREGROUND_SERVICE_MEDIA_PLAYBACK加了maxSdkVersion=34 → Android 15+直接不授予权限startForeground抛SecurityException
6. **v42**：startForeground用2参数版 → Android 14+必须用3参数版传MEDIA_PLAYBACK(2)|SPECIAL_USE(1073741824)，且用int字面量不能引用ServiceInfo常量（厂商ROM VerifyError）
7. **v44**：全屏通知只显示在通知栏不弹Activity → 无论nm.notify()是否抛异常都强制再context.startActivity一次
8. **v46**：startActivity说成功但没真弹（静默拦截）→ SP createdTrigger铁证 + Coroutine ping-pong 500ms/1500ms双重校验重试
9. **v47**：电池优化授权后Provider写入延迟150-400ms → 异步SharedFlow+delay(450ms)二次校验
10. **v49**：3种FLAG全家桶只跑attempt=1就break → 删除break，1/2/3强制全跑（小米静默拦截不抛异常）
11. **v49**：USAGE_ALARM映射到STREAM_MUSIC用户静音听不到 → STREAM_ALARM拉满+STREAM_MUSIC拉85%
12. **v51 BugA**：双通路触发startId重入导致响一下就没 → 幂等判定（isPlaying=true或同trigger<3s跳过）
13. **v51 BugB**：冷启动App读到DS=RINGING就清闹钟导致Overlay/答题页瞬间消失 → 检查两条证据（createdTrigger匹配或canDrawOverlays=true）才KEEP-RINGING
14. **v51 BugC**：Handler重复schedule/stop后仍执行 → 5个Runnable全持有引用，stopRinging第一时间removeCallbacks

### 不能做的事情（Don't Do This）
- ❌ 不能在RingtoneService里用协程Dispatchers.IO播放MediaPlayer，必须主线程
- ❌ 不能引用ServiceInfo.FOREGROUND_SERVICE_TYPE_*常量，必须写int字面量（厂商ROM类加载VerifyError）
- ❌ 不能attempt=1拉起"成功"（无异常）就break，必须1/2/3全跑
- ❌ 不能冷启动时读到DS=RINGING就无脑clearAlarm，必须检查两条证据
- ❌ 不能用MediaPlayer.create()作为主要播放方式，只能兜底
- ❌ 不能把STREAM_ALARM音量设置交给用户，响铃时必须强制拉满
- ❌ 不能上传mathclock-release.jks到Git（已在.gitignore排除）

---

## 九、构建与调试

### 构建命令
```bash
# Debug构建
./gradlew assembleDebug

# Release构建（已配置签名）
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
# v51后自动命名：根目录 数学闹钟-正式版.apk + 数学闹钟-正式版-N.apk（自动递增N）
```

### Dev调试入口
- Debug版右上角有「[Dev] 模拟」按钮：10秒后触发闹钟
- Release版自动隐藏（build.gradle.kts里 ENABLE_DEV_MENU 控制）

### 真机测试重点
1. 杀App后测试闹钟是否能响+弹答题页（国产ROM后台启动拦截核心场景）
2. 锁屏状态下测试
3. 开启勿扰模式测试
4. 媒体音量设为0测试（验证STREAM_ALARM独立音量流生效）
5. 授予悬浮窗权限后杀App测试（验证A1 Overlay兜底）

### 日志导出
- App右下角「导出日志」按钮直接分享
- 或者adb pull：`adb pull /sdcard/Android/data/com.mira.mathalarm/files/logs/`

---

## 十、项目目录结构

```
clock/
├── .gitignore                     # Git忽略（签名密钥、APK、build、SDK、IDE）
├── HANDOFF.md                     # 本文档（交接上下文）
├── README.md                      # 项目说明（部分内容过时，以本文档为准）
├── 改动记录.txt                   # v1.0~v51.0完整版本历史（必读！）
├── build.gradle.kts               # 根构建脚本
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradle/wrapper/      # Gradle Wrapper
├── local.properties               # 本地SDK路径（不提交Git）
├── mathclock-release.jks          # 签名密钥（本地，不提交Git）
├── 数学闹钟-正式版.apk             # 最新Release APK（不提交Git）
├── 数学闹钟-正式版-1.apk           # 版本化APK（不提交Git）
├── website/                       # 配套部署文档和网页预览
│   ├── index.html
│   ├── css/, js/
│   ├── 改动记录.txt
│   ├── 部署-A-妙搭预览版步骤.txt
│   └── 部署-B-备案通过后绑定火山TOS步骤.txt
└── app/
    ├── build.gradle.kts           # App构建配置（含签名配置）
    ├── proguard-rules.pro
    ├── build/                     # 构建产物（不提交Git）
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/mira/mathalarm/
            │   ├── MathAlarmApp.kt            # Application（初始化AppLogger）
            │   ├── alarm/                     # 闹钟调度
            │   ├── data/                      # DataStore
            │   ├── math/                      # 数学题生成
            │   ├── overlay/                   # A1 Overlay终极兜底
            │   ├── permission/                # 权限检查
            │   ├── service/                   # RingtoneService
            │   ├── ui/
            │   │   ├── components/            # 通用组件（按压反馈）
            │   │   ├── home/                  # 首页
            │   │   ├── ringing/               # 答题页
            │   │   └── theme/                 # 主题颜色
            │   └── util/                      # AppLogger、TimeUtil
            └── res/
                ├── drawable/                  # 图标矢量图
                ├── mipmap-anydpi-v26/         # 自适应图标
                ├── raw/                       # 铃声MP3 + miui11.zip
                ├── values/                    # colors/strings/themes
                └── xml/                       # backup_rules/file_paths等
```

---

## 十一、后续可能的任务方向
- 修复真机用户反馈的任何Bug（先导出日志看全链路dump）
- 添加新铃声（注意替换文件后音量系数适配）
- UI/动画优化（遵循Apple HIG + 黑橙配色）
- 多闹钟支持（当前单闹钟，架构需要重构）
- 闹钟标签/备注功能
- 振动强度调节
- 渐进式响铃（音量从小到大）

---

## 十二、Git配置
- 用户名：Shaw485
- 邮箱：767271878@qq.com
- 远程：origin → https://github.com/Shaw485/math_alarm.git
- 默认分支：main
- 后续提交命令：
  ```bash
  cd /Users/bytedance/Documents/trae_projects/clock
  git add .
  git commit -m "vXX.X 改动说明"
  git push
  ```

---

> **交接Checklist**：
> - [x] 已读用户偏好和工作流规范
> - [x] 理解三层兜底机制（A1/A2/A3）
> - [x] 理解状态机和v51 RINGING判定逻辑
> - [x] 理解14个历史踩坑和不能做的事情
> - [x] 理解日志系统如何dump全链路状态
> - [x] 改动后必须更新改动记录.txt
