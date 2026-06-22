# EasyReader v0.2.0 真机安装交接

日期：2026-06-22

## 当前结论

已在真机 `H6TOMF49SGBU89GI`（型号 `PFDM00`）清理导致签名冲突的旧 EasyReader，并重新安装 GitHub Release `v0.2.0` 的 release APK。

当前设备上安装的是：

- 包名：`io.github.luoyuxiaoxiao.easyreader`
- 版本：`versionName=0.2.0`
- 版本号：`versionCode=4`
- 签名方式：`apkSigningVersion=3`
- 当前签名摘要：`[5622498a]`
- release 包状态：未发现 `DEBUGGABLE` 标记

## 签名冲突原因

旧版本并不在主用户空间 `User 0`，而是在系统克隆用户 `User 10: system_clone` 中保留了一份旧安装：

- 旧版本：`versionName=0.1.1`
- 旧签名摘要：`[bde9f188]`
- 旧包带有 `DEBUGGABLE`

因此直接安装 release APK 时触发签名冲突。处理重点是检查并清理 Android 多用户/克隆空间里的同包名安装。

## 已执行操作摘要

下载并校验 GitHub Release `v0.2.0` APK：

```bash
sha256sum -c EasyReader-v0.2.0-release.apk.sha256
```

校验结果为 `OK`。本次使用的临时目录为：

```text
/tmp/easyreader-v0.2.0.qOoEe3
```

清理旧包：

```bash
adb shell pm uninstall --user 10 io.github.luoyuxiaoxiao.easyreader
```

结果：

```text
Success
```

确认旧包已不可见：

```bash
adb shell pm list packages -u
adb shell dumpsys package io.github.luoyuxiaoxiao.easyreader
```

安装 release APK：

```bash
adb install /tmp/easyreader-v0.2.0.qOoEe3/EasyReader-v0.2.0-release.apk
```

结果：

```text
Performing Streamed Install
Success
```

## 安装后验证

验证命令：

```bash
adb shell dumpsys package io.github.luoyuxiaoxiao.easyreader
```

关键结果：

```text
versionCode=4 minSdk=26 targetSdk=35
versionName=0.2.0
apkSigningVersion=3
signatures=[5622498a]
User 0 installed=true
User 10 installed=true
```

注意：安装完成后 `User 10` 也显示 `installed=true`，这是设备系统克隆用户同步安装 release 包后的结果；当前 `User 10` 已不再是旧 debug 签名包，因此不再构成签名冲突。

## 后续排查提示

如果以后再次遇到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 或签名冲突，优先检查多用户与克隆空间：

```bash
adb shell pm list users
adb shell pm list packages -u | rg 'easyreader|luoyuxiaoxiao'
adb shell dumpsys package io.github.luoyuxiaoxiao.easyreader
```

需要清理指定用户空间时，先确认用户 ID，再执行：

```bash
adb shell pm uninstall --user <USER_ID> io.github.luoyuxiaoxiao.easyreader
```

