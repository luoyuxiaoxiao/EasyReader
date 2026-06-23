# EasyReader 0.2.5 发布记录

## 发布信息

- 版本号：0.2.5
- Tag：v0.2.5
- versionCode：9
- 发布方式：EasyReaderAPK 自动发布脚本
- 发布模式：本地 Gradle 预检 + 远端 GitHub Actions

## 发布流程

1. 本地脚本确认版本号并创建发布提交。
2. 本地脚本推送 main 和 v0.2.5。
3. GitHub Actions 通过 .github/workflows/release-apk.yml 构建、签名并上传 APK。

## 校验方式

GitHub Release 会生成：

- EasyReader-v0.2.5-release.apk
- EasyReader-v0.2.5-release.apk.sha256
