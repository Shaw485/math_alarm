# 极简数学题闹钟 App

一款单闹钟、极简设置、强约束关闭的工具型 Android App。闹钟响起后，必须答对一道数学题才能关闭，防止无意识关闭闹钟后继续入睡。

## 功能特点

- **单闹钟设计**：仅支持一个闹钟，极简操作
- **数学题关闭**：响铃后必须答对 `a×b + c×d` 形式的数学题才能关闭
- **三行滚轮选择**：时间选择采用三行滚轮样式，循环滚动
- **三种铃声可选**：清晨 / 曙光 / 山岚，支持试听
- **权限引导**：首次启动统一引导授予通知、精确闹钟、全屏通知权限
- **本地存储**：使用 DataStore 本地键值存储，无需联网

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **最低版本**：Android 8.0 (API 26)
- **目标版本**：Android 14 (API 34)
- **存储**：DataStore Preferences
- **闹钟调度**：AlarmManager.setAlarmClock()
- **响铃播放**：Foreground Service + MediaPlayer (STREAM_ALARM)
- **包名**：com.mira.mathalarm

## 目录结构

```
app/
├── src/main/
│   ├── java/com/mira/mathalarm/
│   │   ├── data/                    # 数据层
│   │   │   ├── AlarmState.kt        # 闹钟状态枚举
│   │   │   ├── RingtoneOption.kt    # 铃声选项枚举
│   │   │   └── AlarmDataStore.kt    # DataStore 存储管理
│   │   ├── math/                    # 数学题模块
│   │   │   └── MathProblemGenerator.kt  # 题目生成与校验
│   │   ├── alarm/                   # 闹钟调度模块
│   │   │   ├── AlarmScheduler.kt    # 闹钟调度管理器
│   │   │   ├── AlarmReceiver.kt     # 闹钟触发广播接收器
│   │   │   └── BootReceiver.kt      # 开机广播接收器
│   │   ├── service/                 # 服务层
│   │   │   └── RingtoneService.kt   # 响铃前台服务
│   │   ├── permission/              # 权限管理
│   │   │   └── PermissionChecker.kt # 权限检查工具
│   │   ├── ui/                      # UI 层
│   │   │   ├── home/                # 首页
│   │   │   │   ├── MainActivity.kt          # 首页 Activity
│   │   │   │   ├── HomeViewModel.kt         # 首页 ViewModel
│   │   │   │   ├── WheelPicker.kt           # 滚轮选择器组件
│   │   │   │   ├── RingtonePickerPopup.kt   # 铃声选择浮窗
│   │   │   │   ├── ViewProblemDialog.kt     # 查看题目弹窗
│   │   │   │   ├── DisableAlarmDialog.kt    # 失效确认弹窗
│   │   │   │   ├── OnboardingDialog.kt      # 首次启动引导弹窗
│   │   │   │   └── PermissionGuideDialog.kt # 权限引导弹窗
│   │   │   ├── ringing/             # 响铃答题页
│   │   │   │   └── RingingActivity.kt       # 响铃答题 Activity
│   │   │   └── theme/               # 主题
│   │   │       └── Theme.kt
│   │   └── util/                    # 工具类
│   │       └── TimeUtil.kt
│   ├── res/
│   │   ├── drawable/                # 图标资源
│   │   ├── mipmap-anydpi-v26/       # 自适应图标
│   │   ├── raw/                     # 铃声资源（占位）
│   │   ├── values/                  # 字符串、颜色、主题
│   │   └── xml/                     # 备份规则
│   └── AndroidManifest.xml
└── src/test/                        # 单元测试
    └── java/com/mira/mathalarm/
        └── MathProblemGeneratorTest.kt
```

## 核心功能说明

### 1. 数学题规则

- 题目形式：`a × b + c × d = ?`
- a、b、c、d 均为 1-9 的随机整数
- 结果范围：2 - 162
- 遵循先乘后加的运算优先级
- 答错后立即刷新新题，铃声不中断
- 答对后停止铃声，关闭答题页

### 2. 闹钟调度

- 使用 `AlarmManager.setAlarmClock()` 设置精确闹钟
- Doze 模式下也能准时触发
- 开机后自动恢复已设置的闹钟
- 支持跨天计算（如 23:00 设置次日 07:00）

### 3. 响铃机制

- 前台服务 + MediaPlayer 循环播放
- 音频通道：`AudioManager.STREAM_ALARM`（不受静音/勿扰影响）
- 最长响铃 2 分钟，超时自动停止
- 锁屏/息屏/后台状态下全屏拉起答题页

### 4. 状态机

```
未设置 ──确认设置──▶ 生效中 ──到达时间──▶ 响铃中 ──答对──▶ 未设置
                      │                                  │
                      └──主动失效──▶ 本次闹钟已失效 ──3秒后──▶ 未设置
                                            │
         正常切后台返回 ──回响铃答题页并保持响铃──┘
         异常终止 ──清理中间态回未设置──┘
```

### 5. 权限处理

按系统版本动态申请，低版本自动跳过：

| 权限 | 最低版本 | 说明 |
|------|---------|------|
| POST_NOTIFICATIONS | API 33 (Android 13) | 通知权限 |
| SCHEDULE_EXACT_ALARM | API 31 (Android 12) | 精确闹钟权限 |
| USE_FULL_SCREEN_INTENT | API 34 (Android 14) | 全屏通知权限 |

## 如何运行

1. 使用 Android Studio 打开项目
2. 等待 Gradle Sync 完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 按钮安装运行

## 替换铃声

当前铃声为占位用的正弦波短音频。如需替换为自己的铃声：

1. 准备三个 `.wav` 或 `.mp3` 格式的音频文件
2. 将文件重命名为：
   - `ringtone_qingchen`（清晨）
   - `ringtone_shuguang`（曙光）
   - `ringtone_shanlan`（山岚）
3. 替换 `app/src/main/res/raw/` 目录下的对应文件
4. 重新编译运行

> 注意：铃声文件名必须与 `RingtoneOption` 枚举中引用的资源 ID 一致。

## Dev 调试入口

调试版（debug build）右上角有 `[Dev] 模拟` 入口：

- **10 秒后触发闹钟**：快速验证锁屏响铃 → 全屏答题 → 答对关闭闭环

发布版（release build）自动隐藏此入口，由 `BuildConfig.ENABLE_DEV_MENU` 控制。

## 单元测试

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

覆盖内容：
- 数学题生成逻辑（数值范围、随机性）
- 答案计算正确性（运算优先级、边界值）
- 答案校验逻辑（正确/错误/空字符串/非数字/负数）
- 题目显示文本格式（乘号、问号、空格）

## 实现假设与说明

以下是 PRD 未明确、采用的常规实现方案：

1. **时间滚轮组件**：由于 Jetpack Compose 官方无滚轮组件，实现了简化版的三行展示 + 上下按钮切换的滚轮选择器，满足 PRD 三行展示、70% 透明度的视觉要求。可后续替换为更复杂的手势滑动版本。

2. **铃声格式**：使用 WAV 格式占位音频。如需 MP3 等其他格式，替换文件后需同步确认 `MediaPlayer` 支持该格式。

3. **失效回流**：PRD 规定失效状态停留 3 秒后自动回流到"未设置"，实现中通过 ViewModel 的延时协程完成。

4. **异常终止恢复**：通过 DataStore 中的 `isRinging` 标记判断异常终止场景。正常切后台返回仍在响铃状态，异常终止（进程被杀）则清理中间态回到未设置。

5. **Dev 菜单**：通过 BuildConfig 字段控制，发布版不暴露。
