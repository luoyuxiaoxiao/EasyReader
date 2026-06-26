# EasyReader 阅读页手势问题交接

日期：2026-06-25

## 当前状态

- 工作目录：`/home/luoyu/Projects/AndroidAPP/EasyReader`
- 当前改动尚未提交，工作树包含本轮阅读页手势、进度保护、图片预览尝试和测试改动。
- 真机 `H6TOMF49SGBU89GI` 已覆盖安装当前 debug 包，未删除 debug 包。
- debug 包路径：`app/build/outputs/apk/debug/app-debug.apk`
- 用户实测结论：手势误切章已经大幅改善，正常使用下基本可接受；仍可能存在边缘问题，暂时先收口。
- Bug 3 图片预览仍有问题，本轮明确先不处理。

## 本轮核心结论

最关键的结论是：很多“外层手势看起来没有切章，但页面仍跳到上一章/下一章”的问题不是 `ChapterSwipeDetector` 自己触发的，而是 Readium/WebView 子层在 `ACTION_UP` 阶段继续拿到手势后自行处理了横向翻页/切章。

因此现在的原则是：

- 章节切换只允许应用层 `ChapterSwipeDetector` 生效。
- 如果应用层判定切章，先给子 WebView 发 `ACTION_CANCEL`，再执行 `onNextChapter` / `onPreviousChapter`。
- 如果应用层判定不切章，但这次手势已经有明显横向位移，也要取消子 WebView 的 `ACTION_UP`，避免 Readium 子层另起一套规则。
- 竖向滚动、折返滚动、斜向噪声路径优先保护阅读连续性，宁可不切章，也不要误跳相邻章末尾。

详细根因记录见：`docs/reader-gesture-debugging-2026-06-25.md`

## 已完成的主要修复

### 1. 手势路径采样和折返识别

文件：`app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`

- `updateGesturePath()` 现在会读取 `MotionEvent` 的历史点：`getHistoricalX/Y`。
- 用 `pathAbsDx/pathAbsDy` 做累计路径判断，而不是只看终点净位移。
- 用 `minGestureY/maxGestureY` 识别竖向折返，避免“右下后右上”“上滑后下滑回原点”这类路径在松手时触发切章。
- 竖向锁定后如果发现折返路径夹带横向漂移，松手时发送 `ACTION_CANCEL`，不把 `ACTION_UP` 交给 Readium。

相关日志：

```text
gesture vertical-lock ...
gesture cancel folded vertical up ...
gesture suppress folded chapter swipe ...
```

相关测试：

- `foldedVerticalGestureWithHorizontalDriftCancelsChildUp`
- `foldedVerticalGestureSplitIntoSmallMovesCancelsChildUp`
- `foldedVerticalPathWithinOneGestureDoesNotSwitchChapter`
- `foldedDiagonalPathWithLargeHorizontalNetDoesNotSwitchChapter`

### 2. Readium 子层横向手势拦截

文件：`ReaderGestureLayout.kt`

- 横向锁定时立即 `cancelChildTouch(event.eventTime)`，让 Readium 不再继续处理这次横滑。
- 对“外层不认可为切章，但横向位移已经明显”的手势，也取消子层 `UP`。
- 这一步是解决“没有 `gesture switch`，但 locator 跳到相邻章”的关键。

相关日志：

```text
gesture cancel ambiguous child up ...
gesture cancel rejected horizontal child up ...
```

相关测试：

- `ambiguousDiagonalSwipeCancelsChildUpWithoutSwitchingChapter`
- `horizontalSwipeRejectedByAppCancelsChildUpWithoutSwitchingChapter`

### 3. 切章进度 100% 保护

文件：

- `ReaderActivity.kt`
- `ReaderScrollProgress.kt`
- `ReaderViewModel.kt`
- `ReaderChapterStartGuard.kt`

已做的保护：

- WebView scroll listener 传入 chapter-aware `syntheticNonScrollableProgression(...)`，避免非滚动章节默认变成 `1.0`。
- 切章后短窗口内只接受目标章节开头附近 locator / scroll sample，避免旧 WebView 或旧 locator 把上一章末尾写回。
- `findBestVisibleWebView(...)` 会优先匹配当前 reading order，识别出旧 WebView 样本时忽略。
- 竖向滚动后短时间内，如果 locator 跳到与滚动方向相反的相邻章节，会先忽略。

相关日志：

```text
chapter request delta=...
chapter opened current=... target=...
locator raw ...
locator ignored ...
locator ignored by direction ...
locator accepted ...
scroll sample ignored by webview ...
scroll sample index=... guard=... ignored=...
```

### 4. 当前手感参数

文件：`app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`

当前参数：

```kotlin
private val minHorizontalDistancePx = max(72f * density, screenWidthPx * 0.18f)
private val minFastHorizontalDistancePx = max(48f * density, screenWidthPx * 0.14f)
private val deliberateDirectionRatio = 2.0f
private val fastDirectionRatio = 3.0f
private val minGestureDurationMs = 180L
private val minFlingVelocityPxPerSecond = 1800f
```

含义：

- 慢速切章允许 2:1 横纵比，手感比 3:1 自然。
- 快速短横滑阈值降低到 `max(48dp, 屏宽 14%)`，更丝滑。
- 快速短滑仍要求 3:1 横纵比，避免竖向滚动甩动误触。

相关测试：

- `slowDiagonalSwipeSwitchesWithTwoToOneDirection`
- `fastShortHorizontalFlingSwitchesChapter`
- `mediumFastHorizontalFlingStaysReading`
- `fastFlingStillRequiresStrongHorizontalDirection`

## 调试设施

### 日志标签

- `EasyReaderGesture`：外层手势采样摘要，包括路径、时间、锁定状态、折返判断。
- `EasyReaderTrace`：外层切章请求、locator、WebView scroll sample、子层取消等关键事件。

### 手动清理并记录日志

开始记录前先清空 logcat：

```bash
adb logcat -c
```

开始记录：

```bash
adb logcat -v time EasyReaderGesture:D EasyReaderTrace:D AndroidRuntime:E chromium:W '*:S' > /tmp/easyreader-gesture.log
```

复现后用 `Ctrl+C` 停止记录。

快速过滤关键线索：

```bash
rg -n "gesture#|gesture vertical-lock|gesture horizontal-lock|gesture cancel|gesture suppress|gesture switch|chapter request|chapter opened|locator raw|locator ignored|locator accepted|scroll sample|scroll listener|AndroidRuntime|FATAL|chromium|tile memory" /tmp/easyreader-gesture.log
```

判断口径：

- 有 `gesture switch previous/next` 和 `chapter request`：说明是应用层主动切章，优先查 `ChapterSwipeDetector` 参数或 `ReaderGestureLayout` 状态机。
- 没有 `gesture switch`，但 locator 跳到相邻章：说明 Readium 子层仍拿到了某次 `UP`，优先查 `cancelChildTouch` 条件。
- 有 `gesture cancel rejected horizontal child up` 后仍跳章：说明取消子层事件没有覆盖到实际子 WebView，需要查事件分发或 Readium 内部触发链。
- 有 `scroll sample ignored by webview`：说明旧 WebView 样本被识别并挡住。
- 有 `locator ignored` 或 `locator ignored by direction`：说明章节起点/滚动方向保护正在工作。
- `chromium tile memory limits exceeded` 目前按 WebView 渲染缓存警告处理，不当作误切章根因。

## 验证记录

最近一次完整验证命令：

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
git diff --check
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p io.github.luoyuxiaoxiao.easyreader -c android.intent.category.LAUNCHER 1
```

结果：

- `:app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`
- `git diff --check`：无输出
- 真机：`H6TOMF49SGBU89GI device`
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`：`Success`
- `monkey` 启动事件注入成功

## 当前未解决/需继续观察

### 1. 仍可能存在边缘误判

用户反馈当前已大幅改善，但“非常刻意的折返路径”或其他未覆盖路径仍可能触发异常。下一轮不要先猜参数，优先抓日志确认：

- 这次异常有没有 `gesture switch`？
- 这次异常有没有 `chapter request`？
- 如果没有，Readium 是否仍收到 `UP`？
- 当前手势是否被 `cancel ambiguous` / `cancel rejected horizontal` / `cancel folded vertical` 取消？

### 2. 切到上一章后 100% 的问题需要继续用日志区分来源

现在有两类可能：

- 应用层主动切章后，旧 locator / 旧 WebView scroll sample 污染进度。
- 应用层没有切章，Readium 子层自己跳到上一章末尾。

本轮更强的判断是第二类是主要来源，但第一类保护也已加上。后续如果再出现，必须按日志区分，不要把两类问题混在一起调。

### 3. Bug 3 图片预览暂不处理

本轮曾加入 `ReaderImageTapScripts` 和相关测试，但用户已明确 Bug 3 先不管。已知方向是 reader WebView 内部 `fetch(src) -> blob -> dataURL`，但真实书籍上仍有问题，后续应单独开一轮排查。

## 关键文件清单

手势与切章：

- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureTraceRecorder.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderDebugTrace.kt`

进度和 locator 保护：

- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderScrollProgress.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderChapterStartGuard.kt`

图片预览相关，暂不继续：

- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderImageTapScripts.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderChrome.kt`

测试：

- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetectorTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayoutTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderScrollProgressTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderChapterStartGuardTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivityScrollProgressBindingTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureTraceRecorderTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderImageTapScriptsTest.kt`
- `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderContentInteractionTest.kt`

## 下一轮建议

1. 用户若再复现误切章，先让用户记录 `/tmp/easyreader-gesture.log`，不要先改阈值。
2. 按“是否有 `gesture switch` / `chapter request`”把问题分成应用层主动切章和 Readium 子层干扰两类。
3. 如果是应用层主动切章，再调 `ChapterSwipeDetector` 或 `ReaderGestureLayout` 的状态机。
4. 如果是 Readium 子层干扰，继续收紧 `cancelChildTouch` 条件，重点看 `ACTION_UP` 是否穿透。
5. 合并前至少保留当前单元测试覆盖，并重新跑完整 `:app:testDebugUnitTest :app:assembleDebug`。
