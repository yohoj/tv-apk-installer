# 多屏互动（T-Cast）安装协议

来源：TCL 多屏互动 5.30（`com.tcl.MultiScreenInteraction_TV`）在 TCL MT5879 国行固件上的实测抓包，
以及 `scripts/tcast-push.py`。App 端实现见 `android/app/src/main/java/com/example/tvinstaller/core/TcastClient.kt`。

## 传输层

- TCP，端口 **6553**，明文，无握手/无鉴权。
- 每条消息：**4 字节大端有符号整数 = 正文长度**，紧跟 UTF-8 正文。
- 正文是 `>>` 分隔的字段列表；第一字段是消息类型。

## 会话

| 方向 | 报文 | 说明 |
|---|---|---|
| 手机 → 电视 | `159>>1>>0` | 打开会话（不加密）。每次连接先发。 |
| 手机 → 电视 | `150>>` | 心跳。电视回 `150>>YES`；**安静超过约 5 秒会话会被电视断开**，因此每 5 秒发一次。 |
| 电视 → 手机 | `150>>YES` | 心跳应答，可忽略。 |

## 发起安装

手机 → 电视：

```
223>>INSTALL>>{package}>>{label}>>{url}>>2>>{versionCode}>>{sizeMB}>>{appMd5}>>{signatureMd5}>>{icon}>>{apkPathMd5}
```

| 序号 | 字段 | 取值 |
|---|---|---|
| 0 | 类型 | 固定 `223` |
| 1 | 动作 | 固定 `INSTALL` |
| 2 | 包名 | APK 的 `packageName` |
| 3 | 应用名 | 电视上显示的名字，不能含 `>>` |
| 4 | 下载地址 | 电视可直接访问的 **HTTP** 地址，如 `http://192.168.3.100:8765/app.apk` |
| 5 | 来源 | `2` = 手机本地 APK。配合字段 11 时跳过应用商店查询，直接询问用户 |
| 6 | versionCode | APK 的版本号，未知填 `0` |
| 7 | 大小 | MB，整数（不足 1 按 1）。只用于展示 |
| 8 | appMd5 | APK 文件 MD5；**实测可留空** |
| 9 | signatureMd5 | 签名 MD5；**实测可留空** |
| 10 | icon | 图标（URL 或路径）；**实测可留空** |
| 11 | apkPathMd5 | `MD5(字段 4 的下载地址字符串)`，十六进制小写。用于标识这次推送 |

> 若填了字段 8/9/10，电视可能会校验下载结果；当前实现留空以避免额外的失败点。
> 自测过 standard / guard 两类 APK 均可安装。

## 电视的回应

手机按 `parts[2] == package` 过滤，避免串话。

### 状态

```
223>>INSTALL>>{package}>>{label}>>{status}>>{apkPathMd5}
```

| status | 含义 | App 行为 |
|---|---|---|
| `confirm_pending` | 电视已弹窗，等遥控器确认 | 进入「等待确认」，提示用户按「右」→「同意」→「确认」 |
| `denied` | 用户拒绝 | 失败 |
| `installing` | 开始安装 | 进入安装阶段 |
| `installed` | 安装完成 | 成功 |
| `failed` | 安装失败 | 失败 |

### 进度

```
223>>PROGRESS>>{package}>>{label}>>{stage}>>{percent}>>{apkPathMd5}
```

| stage | 含义 |
|---|---|
| `1` | 电视正在下载 APK |
| `2` | 下载或安装失败 |
| `3` | 正在安装 |
| `5` | 已安装 |

`percent` 为 0–100，未知时常为 `0` 或 `-1`。

## 电视端下载行为要点

- 电视是用**自己的网络**去 GET 字段 4 的地址，因此手机必须在局域网内监听 `0.0.0.0`，
  不能只绑 `127.0.0.1`（电脑版脚本用 `adb reverse` 才能绑回环地址）。
- 电视可能重试、可能带 `Range` 请求头：服务端应支持 `Range: bytes=` 与 `HEAD`。
- 响应 **Content-Type** 用 `application/vnd.android.package-archive`，带准确的 `Content-Length`。
- 手机与电视必须同网段且未被 AP 隔离；手机侧 VPN 可能把电视的请求路由走，导致 0% 卡住。

## 与电脑版脚本的差异

| 项目 | 电脑版 `tcast-push.py` | 手机 App |
|---|---|---|
| APK 来源 | 本地文件 + `python3 -m http.server` | 文件选择器 → App 缓存 |
| 电视如何访问 | `adb reverse tcp:8765` 后给 `http://127.0.0.1:8765/...` | 手机 Wi-Fi IPv4，如 `http://192.168.3.100:8765/...` |
| 包名/版本 | `apkmeta.py` 解析二进制 AndroidManifest | `PackageManager.getPackageArchiveInfo` |
| 心跳 | 5 秒 | 5 秒 |
| 超时 | 参数，默认 180 秒 | 180 秒（`SettingsStore.DEFAULT_TIMEOUT_SECONDS`） |

## 参考

- `scripts/tcast-push.py`、`scripts/apkmeta.py`（纯 Python，无依赖，可在任意机器上先验证通道是否通）
- 快速验证：`nc -z -G 3 192.168.3.212 6553`
