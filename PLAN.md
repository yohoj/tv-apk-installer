# TV APK 安装助手 —— 项目计划

**目标电视**：TCL 电视（192.168.3.212，tcl_m7642）  
**目标平台**：安卓手机/平板（作为发起端，替代电脑 + AI）  
**备选平台**：网页 + 局域网后端（树莓派 / NAS / 常开电脑）

---

## 一、背景与目标

当前通过 OpenCode 内置的 `@tv-apk-installer` 子安装，需要：
1. 开电脑，运行 AI 对话；
2. AI 在电脑上起本地 HTTP 服务、建 `adb reverse` 反向隧道、通过 Socket 向电视 6553 端口发多屏互动安装请求；
3. 用户在电视上按遥控器「右」选「同意」，再按「确认」。

用户希望**不再依赖电脑和 AI**，直接在手机上装一个 APP（或通过网页），选 APK、输入电视 IP、点一下即可推送安装。

核心原理不变：**利用电视系统自带的“多屏互动”（T-Cast）通道**，电视端系统应用自行下载并安装 APK，**不走**电视的 `PackageInstaller` 验证链，从而绕过 TCL 国行固件常见的 `INSTALL_FAILED_VERIFICATION_FAILURE` /“安装被阻止”拦截。

---

## 二、技术方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|---|---|---|---|
| **安卓 APP（原生）** | 手机即发起端，无需电脑；可常驻后台 HTTP 服务；体验最接近原生 | 需要 Android Studio 开发；需申请网络/存储权限；不同 TCL 固件 6553 协议可能微差 | ⭐⭐⭐⭐⭐ |
| **网页 + 局域网后端** | 无需安装 APP，浏览器即可操作 | 浏览器沙箱无法监听 TCP 端口，必须配合局域网内常开的后端服务器（树莓派/NAS/手机常驻）；架构更复杂 | ⭐⭐⭐ |
| 纯网页（无后端） | 最轻量 | 浏览器无法作为 HTTP 服务器供电视下载，无法直接连 6553 端口，**不可行** | ❌ |

**结论**：主推 **安卓 APP**；网页方案仅作为后端已就绪时的轻量控制台。

---

## 三、核心原理（安卓端实现）

```
手机 APP                                  电视 (192.168.3.212)
─────────────────                         ─────────────────
1. 用户选择本地 APK                            │
2. 启动本地 HTTP 服务器 (0.0.0.0:8765)           │
   提供下载：http://<手机IP>:8765/install.apk    │
3. Socket 连接 电视IP:6553                       │
   发送多屏互动安装请求 (tcast-push 协议)         │
   └─ 携带 APK 下载地址、包名、应用名、超时时间    │
4. 电视弹出「多屏互动：正在发起应用推送」          │
   用户按遥控器「右」→ 同意 → 确认                 │
5. 电视系统应用从手机 HTTP 下载 APK → 安装          │
   └─ 不触发 PackageInstaller 验证链             │
6. 手机显示安装结果/日志                           │
```

**关键约束**：
- 手机和电视必须在**同一局域网**（同 Wi-Fi），且路由器未开启 AP 隔离。
- 电视需已开启 **ADB 调试/网络调试**，手机已获 ADB 授权（首次配对）。
- 电视上的「同意」弹框**必须由真人拿遥控器确认**，APP 只能提示，不能代替。
- 仅支持单文件 `.apk`，不支持 `.apks` / `.xapk` 分割包。
- 若电视已装同包名不同签名的旧版，安装会失败，需先卸载旧版。

---

## 四、功能模块

### 4.1 安卓 APP 模块

| 模块 | 职责 |
|---|---|
| **APK 选择器** | 通过 `Intent.ACTION_OPEN_DOCUMENT` 让用户选 APK，读取文件 URI（无需全盘存储权限） |
| **本地 HTTP 服务器** | NanoHTTPD / khttp / ServerSocket，监听 `0.0.0.0:8765`，提供 APK 下载，仅在推送期间启动 |
| **网络工具** | 获取 Wi-Fi 下的本机 IPv4；探测电视 6553 端口是否可达 |
| **多屏互动客户端** | Socket 客户端，连 `电视IP:6553`，发送 T-Cast 安装请求报文（参考 `tcast-push.py` 协议） |
| **安装引导页** | 推送前提示用户看电视、按遥控器「右」选同意、再按确认 |
| **进度与日志** | 显示推送状态、超时、安装成功/失败、原始错误码 |
| **设置页** | 默认电视 IP、HTTP 端口、请求超时时间、历史记录 |

### 4.2 网页 + 后端模块（备选）

| 模块 | 职责 |
|---|---|
| **前端网页** | 上传 APK / 选择已上传 APK、输入电视 IP、点击推送、查看进度日志 |
| **后端 API** | 接收 APK、启动本地 HTTP 服务、向电视 6553 发推送请求、返回 SSE/WebSocket 进度 |
| **部署目标** | 树莓派、NAS、或局域网内常开的手机/电脑 |

---

## 五、开发阶段与里程碑

### Phase 1：原理验证（PoC）
- [ ] 在电脑上用 `tcast-push.py` 手动给 192.168.3.212 装一个测试 APK，确认多屏互动通道可用
- [ ] 抓包/阅读 `tcast-push.py`，确定 6553 端口请求报文格式（字段名、编码、超时字段等）
- [ ] 交付物：可运行的 Python 脚本 + 抓包笔记

### Phase 2：安卓 APP 骨架
- [ ] Android Studio 新建项目（Kotlin + Jetpack Compose 或 View）
- [ ] 实现 APK 选择器（存储访问框架）
- [ ] 实现本地 HTTP 服务器（启动/停止、提供下载）
- [ ] 实现 Socket 客户端，向电视 6553 发推送请求
- [ ] 交付物：可编译的 APP 原型，能在真机手机上运行

### Phase 3：完整交互与 UI
- [ ] 输入电视 IP（可保存常用 IP，如 192.168.3.212）
- [ ] 推送按钮 + 状态机（闲置 → 推送中 → 等待用户确认 → 成功/失败）
- [ ] 提示弹框：电视右上角出现多屏互动请求时，引导用户按「右」→ 同意 → 确认
- [ ] 日志面板：显示每一步的原始输出
- [ ] 交付物：可用的 APP 安装包（APK）

### Phase 4：真机测试与稳定性
- [ ] 在 192.168.3.212 上安装多个测试 APK（普通应用、launcher 类、签名冲突场景）
- [ ] 测试不同 Wi-Fi 环境（AP 隔离开/关）
- [ ] 处理超时、断连、用户拒绝等异常流程
- [ ] 交付物：测试报告 + 已知问题清单

### Phase 5：网页后端（可选，按需）
- [ ] Node.js / Python 后端，提供上传 + 推送 API
- [ ] 简单前端网页，连接后端
- [ ] 交付物：可在树莓派/NAS 上运行的后端 + 网页控制台

---

## 六、目录结构建议（安卓 APP）

```
tv-apk-installer-app/
├── PLAN.md                 # 本计划文档
├── README.md               # 构建、运行、使用说明
├── android/                # Android Studio 项目
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/example/tvinstaller/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── TcastClient.kt        # Socket 向 6553 发推送
│   │   │   │   ├── LocalHttpServer.kt    # 本地 HTTP 提供下载
│   │   │   │   ├── NetworkUtils.kt       # 获取本机 IP、端口探测
│   │   │   │   └── ApkPicker.kt          # 存储访问框架选 APK
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── docs/
│   ├── tcast-protocol.md   # 6553 端口协议笔记（从 tcast-push.py 移植）
│   └── test-results.md     # 真机测试记录
└── scripts/                # 辅助脚本（如抓包、模拟请求）
```

---

## 七、权限与配置（AndroidManifest）

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<!-- 通过 ACTION_OPEN_DOCUMENT 选文件，无需 MANAGE_EXTERNAL_STORAGE -->
```

---

## 八、验收标准

1. 在手机上安装 APP 后，能在同一 Wi-Fi 下发现/连接 192.168.3.212。
2. 选择任意单文件 `.apk`，点击推送后，电视右上角出现「多屏互动：正在发起应用推送」。
3. 用户按遥控器「右」选「同意」再按「确认」后，APK 在电视上成功安装。
4. 手机 APP 显示安装成功，并能通过 adb 拉回包名/版本与预期一致。
5. 异常场景（用户拒绝、断连、超时、签名冲突）有明确提示，不崩溃、不自动重装。

---

## 九、已知风险与限制

- **固件差异**：不同 TCL 型号/固件的 6553 协议可能不完全一致，需以 `tcast-push.py` 抓包为准进行适配。
- **无法自动点同意**：电视弹框必须人工确认，APP 不能替代遥控器操作。
- **AP 隔离**：部分酒店/公共 Wi-Fi 开启客户端隔离，手机与电视互不可见，必须使用家庭 Wi-Fi。
- **不支持分割 APK**：`.apks` / `.xapk` 需先拆分为单文件 APK。
- **签名冲突**：同包名不同签名的旧版需先手动卸载。

---

## 十、下一步行动

1. 确认本计划无异议后，开始 **Phase 1**：在电脑上用 `tcast-push.py` 跑通一次 192.168.3.212 的真机安装，并记录 6553 协议细节。
2. 若需要，我可以继续生成安卓 APP 的初始代码框架（`MainActivity.kt`、`TcastClient.kt`、`LocalHttpServer.kt` 等）。
