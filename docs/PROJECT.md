# EasyReader 项目文档

## 当前定位

EasyReader 是一个本地 EPUB 阅读器。当前版本聚焦导入、本地书架、Readium 阅读、阅读进度恢复、桌面快捷方式和稳定的移动端阅读手势。

暂不包含 PDF、云同步、账号、笔记、高亮、全文搜索、TTS、OPDS 和翻页模式。

## 主要模块

- `MainActivity`：应用入口，承载书架 UI。
- `ui/bookshelf`：书架列表、导入入口和桌面快捷方式入口。
- `ui/reader`：阅读页 Activity、Chrome、手势层、字体缩放和进度显示。
- `reader/readium`：Readium 服务、EPUB session、章节权重估算。
- `reader/gesture`：章节横滑识别。
- `data/local`：Room 数据库、书籍与进度持久化。
- `domain/importer`：EPUB 导入、SHA-256 去重。
- `shortcut`：Android pinned shortcut 协议封装。

## 阅读器交互

- 顶部 Chrome 只由明确点击切换，滚动、章节切换和 locator 更新都不能自动显示顶部 Chrome。
- 点击页面切换全局 Chrome；手势层消费点击时会取消子 WebView touch，避免短点击触发文本选择。
- 滚动时只临时显示底部进度 Chrome；手指离开后的惯性滚动会继续刷新底部进度和隐藏倒计时。
- 横向切章保留左右 32dp 系统返回区域，并用方向阈值区分章节切换和竖向阅读滚动。
- 顶部 Chrome 控件区域会透传给按钮，返回按钮和后续顶部功能按钮可以正常接收点击。

## 进度显示策略

底部进度显示不使用 Readium locator 百分比。locator 只用于保存和恢复阅读位置。

- 单章进度：来自当前 WebView 的滚动桥。
- 总进度：使用 EPUB spine 章节权重 + 当前章节滚动桥进度。
- 章节权重：从 EPUB spine XHTML 估算，统计可见文本、图片/SVG 和脚注链接，给每章至少 1 的权重。
- 切换章节成功后会立即显示章节起点进度，避免等待下一次滚动事件。
- 首页通常是封面，切章起点显示为 0%；如果测量到首页不可滚动，不覆盖这个显示。
- 最后一页如果测量到不可滚动，会补成 100%，避免总进度和单章进度停在起点。

## 发布流程

发布使用仓库根目录脚本：

```bash
./EasyReaderAPK --check 0.1.2
./EasyReaderAPK --remote 0.1.2
```

脚本会更新版本号、创建发布提交、打 `v<version>` tag 并 push。GitHub Actions 负责 release APK 构建、签名、SHA-256 和 GitHub Release 上传。

详细流程见 `docs/releases/github-actions-apk-release.md`。

## 验证基线

发布前至少运行：

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

真机重点回归：

- 导入 EPUB、重复导入去重。
- 阅读页点击不会选中文字。
- 滚动时顶部 Chrome 不自动出现，底部进度实时更新。
- 横向切章立即刷新底部进度。
- 首页/封面显示 0%，最后不可滚动页显示 100%。
- 顶部返回按钮可用。
