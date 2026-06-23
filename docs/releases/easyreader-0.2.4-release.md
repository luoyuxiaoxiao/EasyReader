# EasyReader 0.2.4 发布记录

## 发布信息

- 版本号：0.2.4
- Tag：v0.2.4
- versionCode：8
- 发布方式：EasyReaderAPK 自动发布脚本
- 发布模式：本地 Gradle 预检 + 远端 GitHub Actions

## 本次改动

- 阅读页支持点击图片后全屏预览，可放大缩小查看细节，再次点击关闭预览。
- 阅读页支持点击书中注解后用底部弹层查看内容，避免直接跳转或无响应。
- 主界面“整理”入口调整为“设置”，排序方式和主题分别作为二级入口，具体选项作为三级界面。
- 新增全局主题设置，支持跟随系统、白色、黑色，并同步阅读页主题。
- 优化阅读手势判断，避免下滚后立即上滚时误触发切章，同时保留正常上下滚动体验。

## 发布流程

1. 本地脚本确认版本号并创建发布提交。
2. 本地脚本推送 main 和 v0.2.4。
3. GitHub Actions 通过 .github/workflows/release-apk.yml 构建、签名并上传 APK。

## 校验方式

GitHub Release 会生成：

- EasyReader-v0.2.4-release.apk
- EasyReader-v0.2.4-release.apk.sha256
