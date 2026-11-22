# 真机测试记录

## 环境

| 项目 | 值 |
|---|---|
| 电视 | **雷鸟电视**（TCL / FFALCON `tcl_m7642`），Android 9 / SDK 28，1920×1080，`192.168.3.212` |
| 电视安装通道 | 多屏互动 `com.tcl.MultiScreenInteraction_TV`，TCP 6553 |
| 已知限制 | 该固件 `adb install` 被禁用：`install apk has be disabled from pm by system default!` |
| 手机 | 待填写（型号 / Android 版本 / 局域网 IP） |

## Phase 1：协议验证（电脑侧）

- [x] `scripts/tcast-push.py` 在本机对 `192.168.3.212:6553` 可用，电视弹「多屏互动」后确认即可安装
- [x] 端口探测：`nc -z -G 3 192.168.3.212 6553` 通过
- [x] 报文格式整理：见 `docs/tcast-protocol.md`

## Phase 2/3：App 构建

- [x] `./gradlew assembleDebug` 通过（Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.24，无警告）
- [x] 产物已签名并核对元数据：`dist/tv-apk-installer-0.1.0-debug.apk`
  - `package=com.example.tvinstaller`，`versionCode=1`，`versionName=0.1.0`
  - `minSdk=24`，`targetSdk=34`，大小约 15 MB
  - SHA-256 `f6ab03374a9e0158b02c68de2579051896e435770ea12548f6d009df8f04fa3d`
  - debug 证书 SHA-256 `ddcc0131301b9517a483348f5af7a5817abe0594f41cba6e720da8d336f4cc97`
- [x] 安装到手机可启动，无崩溃（2026-11-22 用户实测）
- [x] 文件选择器能读出 APK 包名 / versionCode / 大小（同上，实测通过）
- [x] 端到端：手机 App → 雷鸟电视 6553 → 电视从手机 HTTP 下载并安装成功（2026-11-22 用户实测）

### 构建环境（本机，无需 sudo）

| 组件 | 路径 / 版本 | 来源 |
|---|---|---|
| JDK | `~/android-tools/jdk-17`（Temurin 17.0.21.1） | 清华 `mirrors.tuna.tsinghua.edu.cn/Adoptium` |
| Android SDK | `~/android-tools/android-sdk`（platform-tools、platforms;android-34、build-tools;34.0.0） | 腾讯 `mirrors.cloud.tencent.com/AndroidSDK` |
| Gradle | wrapper 8.7，发行包走 `mirrors.cloud.tencent.com/gradle` | 腾讯镜像 |
| Maven 依赖 | 阿里云 `maven.aliyun.com/repository/*`，回退 google/mavenCentral | 阿里云 |

首次全量构建 2m13s，增量 15s。构建命令：

```bash
cd android
JAVA_HOME=$HOME/android-tools/jdk-17 ANDROID_HOME=$HOME/android-tools/android-sdk ./gradlew assembleDebug
```


## Phase 4：真机推送

| # | 场景 | 结果 | 备注 |
|---|---|---|---|
| 1 | 普通应用（如 `app.orchard.tv`） | 用户实测通过 | 雷鸟电视 `tcl_m7642` 上安装正常 |
| 2 | Launcher 类应用 | 待测 | |
| 3 | 同包名不同签名旧版 | 待测 | 预期失败，提示签名冲突 |
| 4 | 电视上点「拒绝」 | 待测 | 预期 App 显示「拒绝」并停止 |
| 5 | 电视关机 / 端口不可达 | 待测 | 预期连接阶段即报错 |
| 6 | 推送中取消 | 待测 | 预期停止服务、电视端无残留 |
| 7 | 手机锁屏 / 切后台 | 待测 | 前台服务应保持下载 |
| 8 | 路由器开 AP 隔离 | 待测 | 预期下载 0%，日志无电视请求 |

## 已知问题

（待测后补充）
