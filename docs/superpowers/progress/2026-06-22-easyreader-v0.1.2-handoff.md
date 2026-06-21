# EasyReader v0.1.2 交接文档

## 当前状态

- 工作区：`/home/luoyu/Projects/AndroidAPP/EasyReader`
- 分支：`main`
- 目标版本：`v0.1.2`
- 发布脚本：`./EasyReaderAPK --remote 0.1.2`
- Android 包名：`io.github.luoyuxiaoxiao.easyreader`

## 本次完成

- 阅读手势：
  - 放宽横向切章识别，改善水平左右滑动难以切章的问题。
  - 保留系统返回区域，降低横滑与 Android 返回手势冲突。
  - 页面短点击切换 Chrome 时取消 WebView touch，避免触发文本选择菜单。
- Chrome 行为：
  - 顶部 Chrome 只允许明确点击切换。
  - 滚动、章节切换、locator 更新都不会自动恢复顶部 Chrome。
  - 顶部 Chrome 控件区域透传点击，返回按钮恢复可用。
  - 去掉底部 Chrome 多余半透明背景。
- 进度显示：
  - locator 只用于保存和恢复位置，不再参与底部百分比显示。
  - 单章进度使用 WebView 滚动桥。
  - 总进度使用章节权重 + 当前章节滚动桥。
  - 章节权重由 EPUB spine 内容估算，考虑文本、图片/SVG 和脚注链接。
  - 切章后立即刷新底部进度，不再等待下一次滚动事件。
  - 首页/封面起点显示 0%；最后不可滚动页测量后补为 100%。

## 关键文件

- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderScrollProgress.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/readium/EpubChapterWeightEstimator.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`

## 验证记录

已运行：

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

结果：`BUILD SUCCESSFUL`。

已安装 debug 包到真机：

```bash
adb -s H6TOMF49SGBU89GI install -r app/build/outputs/apk/debug/app-debug.apk
```

结果：`Success`。

用户已确认真机测试通过。

## 发布前检查

发布前需要：

1. 提交本次代码和文档。
2. 确认工作区干净。
3. 执行 `./EasyReaderAPK --check 0.1.2`。
4. 执行 `EASYREADERAPK_CONFIRM=yes ./EasyReaderAPK --remote 0.1.2`。
5. 在 GitHub Actions 和 Release 页面确认 APK 与 SHA-256 上传完成。

## 后续建议

- 增加阅读页真机或 instrumentation 覆盖：切章后进度即时刷新、最后不可滚动页 100%、顶部按钮点击。
- 继续观察章节权重在图片密集 EPUB 中的误差，必要时调整图片和脚注权重。
- 后续新增顶部 Chrome 功能时，保持“只由明确点击显示”的状态边界。
