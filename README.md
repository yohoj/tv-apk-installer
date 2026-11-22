# 电视 APK 安装助手

在手机上选一个 APK，一点推送到**雷鸟电视**（TCL / FFALCON，实测机型 `tcl_m7642`），用电视自带的「多屏互动」（T-Cast）通道完成安装 —— **不需要电脑、不需要 AI 对话**。

电视端只需要你拿起遥控器按一次「同意」。

- 目标电视：**雷鸟电视**（TCL 子品牌 FFALCON），实测机型 `tcl_m7642`，Android 9 / SDK 28，地址示例 `192.168.3.212`
- 发起端：Android 手机 / 平板（Android 7.0+，同一 Wi-Fi）
- 为什么能装上：TCL / 雷鸟国行固件默认封掉了 `pm install`（`install apk has be disabled from pm by system default!`），但多屏互动是系统应用，它会自己下载并安装 APK，绕过 `PackageInstaller` 的验证链

---

## 快速开始

### 1. 构建 APK

```bash
cd android
JAVA_HOME=$HOME/android-tools/jdk-17 ./gradlew assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
# 已构建好的可直接装：dist/tv-apk-installer-0.1.0-debug.apk
```

需要 JDK 17 与 Android SDK（`compileSdk 34`，路径写在 `android/local.properties` 的 `sdk.dir`）。
也可以直接用 Android Studio 打开 `android/` 目录。

本仓库默认就走国内镜像，海外网络同样可用：

| 用途 | 源 |
|---|---|
| Gradle 发行包 | `mirrors.cloud.tencent.com/gradle`（`gradle/wrapper/gradle-wrapper.properties`） |
| Maven 依赖（AGP / Compose） | 阿里云 `maven.aliyun.com/repository/{google,public,gradle-plugin}`，失败自动回退 `google()` / `mavenCentral()`（`settings.gradle.kts`） |

### 2. 装到手机

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

或者把 `app-debug.apk` 发到手机上手动安装。

### 3. 推送

1. 手机连上和电视**同一个** Wi-Fi（路由器不要开 AP 隔离 / 访客网络）。
2. 打开 App → 「选择 APK」选一个单文件 `.apk`。
3. 确认电视 IP（默认 `192.168.3.212`，可在输入框改）。
4. 点「推送安装」。
5. 电视右上角出现「多屏互动：正在发起应用推送」时，**按遥控器「右」选中「同意」，再按「确认/OK」**。
6. App 会显示电视下载进度和安装结果；日志面板里有原始报文，失败时先看它。

---

## 发布 Release

APK **不入库**（`.gitignore` 已排除 `dist/*.apk`），而是作为 GitHub Release 的附件发布。

### 一条命令发版

```bash
./scripts/release.sh 0.2.0
```

它会：检查工作区干净 → 本机构建校验 → 打 `v0.2.0` 标签并推送 → GitHub Actions 自动构建、把 APK 改名为 `tv-apk-installer-0.2.0-debug.apk`、生成 `SHA256SUMS.txt`、创建 Release。

- 构建进度：https://github.com/yohoj/tv-apk-installer/actions
- 发布页：https://github.com/yohoj/tv-apk-installer/releases

版本号由标签注入（`android/app/build.gradle.kts` 支持 `-PappVersionName` / `-PappVersionCode`），所以 Release 附件里的 APK `versionName` 与标签一致。

### 手动方式

打标签推送，效果和脚本一样（会自动构建并发 Release）：

```bash
git tag -a v0.2.0 -m "Release v0.2.0"
git push origin v0.2.0
```

或者在 GitHub 网页 **Releases → Draft a new release** 上传本地构建产物：

```bash
cd android
JAVA_HOME=$HOME/android-tools/jdk-17 ./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../dist/tv-apk-installer-0.2.0-debug.apk
```

> 目前是 debug 签名，便于侧载。若要长期正式分发，建议换成自己的 release keystore。

---

## 工作原理

```
手机 App                                     电视 (192.168.3.212)
────────                                      ────────────────────
1. 选中 APK，复制到 App 缓存并读出包名/版本
2. 启动单文件 HTTP 服务 0.0.0.0:8765（被占用自动顺延）
3. TCP 连接 电视:6553，发送 223>>INSTALL 报文
   └─ 带 APK 下载地址、包名、版本号、大小、URL 的 MD5
4.                                            右上角弹出「多屏互动」
                                              用户按遥控器：右 → 同意 → 确认
5.                                            系统应用从手机 HTTP 下载 APK
6. 解析 223>>PROGRESS 报文，显示下载/安装进度     └─ 不走 PackageInstaller 验证链
```

协议细节见 [`docs/tcast-protocol.md`](docs/tcast-protocol.md)。电脑端的等价脚本保留在 [`scripts/tcast-push.py`](scripts/tcast-push.py)（排查问题时很好用）。

### 报文一句话版

- TCP **6553**，每条消息 = 4 字节大端长度 + UTF-8 文本，字段用 `>>` 分隔
- `159>>1>>0` 开会话；`150>>` 每 5 秒心跳，安静超过几秒电视会断
- `223>>INSTALL>>包名>>应用名>>下载地址>>2>>版本号>>大小MB>>appMd5>>签名md5>>图标>>APK路径MD5` 发起安装
- 电视回 `223>>INSTALL>>…>>confirm_pending|denied|installing|installed>>…` 与 `223>>PROGRESS>>…>>阶段>>百分比>>…`

---

## 权限

| 权限 | 用途 |
|---|---|
| `INTERNET` | 连接电视 6553、手机本地 HTTP 服务 |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 读取手机局域网 IPv4 |
| `FOREGROUND_SERVICE`(`_DATA_SYNC`) + `POST_NOTIFICATIONS` | 推送期间保持下载服务存活 |
| `WAKE_LOCK` | 电视下载时 CPU 不休眠 |

选 APK 用系统文件选择器（`ACTION_OPEN_DOCUMENT`），**不需要**存储权限。

---

## 已知限制

- **必须人工确认**：电视弹框只能拿遥控器点，App 不能代按。等待超过 180 秒会超时。
- **单文件 APK**：`.apks` / `.xapk` 不支持，需要先拆成单个 APK。
- **签名冲突**：电视上已有同包名不同签名的版本时，电视端会装失败；先卸载旧版再推。
- **AP 隔离**：酒店/公共 Wi-Fi 常见客户端隔离，手机与电视互相看不见，只能用家庭 Wi-Fi。
- **固件差异**：不同 TCL / 雷鸟型号的 6553 报文可能略有出入。本 App 按 `tcast-push.py` 实测协议实现（实测机型：雷鸟 `tcl_m7642`），异常时看日志面板的原始报文。
- **进程存活**：推送期间请把 App 留在前台或让通知常驻；手动「强行停止」App 会中断下载。

## 排查

| 现象 | 处理 |
|---|---|
| 「连不上 192.168.3.212:6553」 | 电视是否开机？手机是否同一 Wi-Fi？IP 是否变了（电视：设置 → 关于 → 本机信息）？ |
| 电视没有弹窗 | 报文可能没发出去，看日志里的 `223` 往返；确认电视多屏互动服务在运行 |
| 一直停在「等待确认」 | 弹窗可能已超时消失，重新推送一次；注意电视右上角 |
| 卡在下载 0% | 手机本地服务没被电视访问到：检查路由器 AP 隔离、手机是否开了 VPN/防火墙 |
| 电视报告安装失败 | 签名冲突、APK 损坏、或该 APK 不支持电视架构；换包或先卸载旧版 |

## 目录

```
tv-apk-installer-app/
├── PLAN.md
├── README.md
├── android/                     # Android 工程（Kotlin + Jetpack Compose）
│   └── app/src/main/java/com/example/tvinstaller/
│       ├── MainActivity.kt      # 入口、文件选择、通知权限
│       ├── MainViewModel.kt     # UI 状态与动作
│       ├── core/
│       │   ├── TcastClient.kt   # 6553 协议客户端
│       │   ├── LocalApkServer.kt# 单文件 HTTP 服务（支持 Range）
│       │   ├── PushEngine.kt    # 状态机：准备 → 连接 → 等待确认 → 下载 → 安装
│       │   ├── PushService.kt   # 前台服务 + 通知 + WakeLock
│       │   ├── ApkInspector.kt  # 复制 APK、读包名/版本/MD5
│       │   ├── NetworkUtils.kt  # 局域网 IPv4、端口探测
│       │   ├── SettingsStore.kt # 记住电视 IP
│       │   └── Models.kt
│       └── ui/                  # Compose 界面
├── docs/
│   ├── tcast-protocol.md
│   └── test-results.md
└── scripts/                     # 电脑端对照脚本
```

`dist/` 里放已签名（debug keystore）的可安装产物。
