# EasyReader 阅读页手势 readest 迁移交接

日期：2026-06-26

## 当前状态

- 已把阅读页手势从 `ChapterSwipeDetector + ReaderGestureLayout` 内部状态判断，重构为 readest 风格的采样层、优先级拦截链和独立消费者。
- debug 包已构建成功：`app/build/outputs/apk/debug/app-debug.apk`
- 本轮没有自动安装到设备；用户将手动安装并真机验证。
- 工作树仍混有用户/历史未提交文件，提交时需要按文件范围挑选，不能直接 `git add .`。

## 架构现状

入口仍是 `ReaderGestureLayout.dispatchTouchEvent()`：

1. `TouchGestureSampler` 统一采样 Android `MotionEvent`。
   - 读取历史点，累计 `pathAbsDx/pathAbsDy`。
   - 记录 `maxAbsDx/maxAbsDy`、净位移、速度、tap candidate、系统返回边缘。
   - 恢复旧保护中的折返识别：用 `minY/maxY` 生成 sticky 的 `verticalReversed`。
2. `TouchInterceptorRegistry` 按优先级分发。
   - 高优先级消费者先看事件。
   - `CONSUMED` 会锁定 owner，后续 MOVE/UP/CANCEL 稳定交给同一消费者。
   - `HANDLED_BUT_PASS_TO_CHILD` 用于竖向滚动：阻止低优先级横滑接管，但正文滚动仍交给 WebView。
3. 当前消费者：
   - `VerticalScrollInterceptor`：优先级 80，识别竖向滚动并开启 450ms 横滑 suppress 窗口。
   - `ChapterSwipeInterceptor`：优先级 60，两阶段 arm/commit 横滑切章，arm 时取消子 WebView，commit 时才切章。
   - pinch 缩放暂时仍在 `ReaderGestureLayout` 入口层处理；多指后不进入章节手势。

## 阈值

集中在 `GestureThresholds`：

- 系统返回边缘：`32dp`
- 慢速横滑：`max(72dp, width * 0.18)`，方向比 `2.0`
- 快速短滑：`max(48dp, width * 0.14)`，方向比 `3.0`，速度 `1800px/s`
- 最小时长：`180ms`
- 切章冷却：`250ms`
- 竖向锁定：`pathAbsDy > pathAbsDx * 1.2 && pathAbsDy > 12dp`
- 竖向后 suppress：`450ms`
- tap：`8dp / 250ms`
- 内容点击延迟消费窗口：`180ms`

## 本轮新增修复：折返路径

用户反馈重构后老问题复现：折返路径仍可能切章。旧交接文档定位到以前的关键保护是 `minGestureY/maxGestureY`，而本轮 readest 迁移初版只保留了 `pathAbsDy`，确实丢了这部分折返信号。

已修复：

- `TouchGestureSampler` 新增 `verticalReversed`。
- 判定方式：
  - 从按下点向下超过 `12dp`，再从最大 Y 回撤超过 `12dp`。
  - 或从按下点向上超过 `12dp`，再从最小 Y 回撤超过 `12dp`。
  - 一旦成立，同一手势内保持 sticky，不被后续终点位置抵消。
- `ChapterSwipeInterceptor` 看到 `verticalReversed` 后禁止切章 commit。
- 如果折返路径仍有明显横向尾迹，UP 阶段返回 `CONSUMED(cancelChild = true)`，吞掉 Readium 子层的惯性/翻页机会。

验证过程：

- 先新增 `foldedVerticalGestureWithHorizontalDriftCancelsChildUpWithoutSwitchingChapter`，当前代码失败在 `nextChapters`，证明折返路径会被外层 commit 成切章。
- 修复后同一测试通过，并确认子层收到 `ACTION_CANCEL` 而不是 `ACTION_UP`。

### 2026-06-26 追加修复：竖向 owner 透传 UP

用户继续复现后确认：折返信号本身仍不够，另一个漏口在优先级链路里。

具体链路：

1. 手势早期被 `VerticalScrollInterceptor` 锁定为竖向滚动。
2. registry 因 `HANDLED_BUT_PASS_TO_CHILD` 不会把 vertical 设为独占 owner。
3. 后续 UP 仍先由 `VerticalScrollInterceptor` 处理，并返回 `HANDLED_BUT_PASS_TO_CHILD`。
4. `ChapterSwipeInterceptor` 没机会看到这个 UP。
5. `ReaderGestureLayout` 把 UP 继续交给子 WebView，Readium 子层拿到完整 DOWN/MOVE/UP 后按自己的阈值结算章节导航。

已修复：

- `VerticalScrollInterceptor` 在“已竖向锁定 + `verticalReversed` + 明显横向尾迹”的 UP 上返回 `CONSUMED(cancelChild = true)`。
- 正常竖向滚动仍保持 `HANDLED_BUT_PASS_TO_CHILD`，不影响正文滚动。
- 新增 `lockedVerticalFoldWithHorizontalDriftCancelsChildUpWithoutSwitchingChapter`，先证明旧代码把 UP 透传给 child，再验证修复后 child 收到 `ACTION_CANCEL`。
- 新增 `VerticalScrollInterceptorTest.lockedFoldedVerticalScrollWithHorizontalTailCancelsChildUp` 锁住该职责。

这个修复的边界是：外层无法阻止 Readium 子层已有的内部手势逻辑，只能保证危险路径不让子层拿到完整 UP。更彻底的根除需要进入 Readium/iframe 层禁用或接管子层翻页手势，那是另一层 bridge/capture 架构。

### 2026-06-26 发布前透传审计

发布前继续枚举 `ReaderGestureLayout` 中所有 `super.dispatchTouchEvent(event)` 路径，并补齐剩余漏口：

- 竖向滚动后的 suppress 窗口内，如果新手势带明显横向尾迹，`ChapterSwipeInterceptor` 在 UP 阶段 `CONSUMED(cancelChild = true)`。
- 系统返回边缘内的横向尾迹不触发外层切章，但也不再把 UP 交给 Readium。
- 双指缩放从 `ACTION_POINTER_DOWN` 起给 child 发一次 `ACTION_CANCEL`，后续多指 MOVE/POINTER_UP/UP 由外层吞掉；外层字体缩放仍正常。
- 顶部 chrome 可见时，只透传点击级别事件给工具栏；横向拖动超过 tap slop 后取消 child，避免绕过手势链。

剩余有意透传路径：

- 普通正文竖向滚动：WebView 必须收到 MOVE/UP 才能滚正文。
- 普通正文 tap：先交给内容层，等待 JS/Readium 标记图片、脚注、链接消费。
- 顶部 chrome 的点击级别事件：用于返回按钮等 toolbar 控件。

新增测试：

- `ReaderGestureLayoutTest.horizontalTailDuringPostVerticalSuppressCancelsChildUpWithoutSwitchingChapter`
- `ReaderGestureLayoutTest.systemBackEdgeHorizontalTailCancelsChildUpWithoutSwitchingChapter`
- `ReaderGestureLayoutTest.topChromeHorizontalDragCancelsChildUp`
- `ReaderGestureLayoutTest.multiTouchGestureDoesNotSwitchChapter`
- `ChapterSwipeInterceptorTest.postVerticalSuppressConsumesHorizontalTailWithoutChapterCommit`
- `ChapterSwipeInterceptorTest.systemBackEdgeSwipeConsumesHorizontalTailWithoutChapterCommit`

## Debug 日志

debug 日志只在 debug 版输出：

- `ReaderGestureTraceRecorder` 默认 `enabled = BuildConfig.DEBUG`。
- `ReaderDebugTrace` 也只在 `BuildConfig.DEBUG` 时调用 `Log.d`。
- release 版不会实际写这些 tag。

本轮修复了 readest 迁移后的日志断链：

- `ReaderGestureLayout` 重新接回 `ReaderGestureTraceRecorder`。
- `ReaderGestureTraceRecorder.record(...)` 会展开 `MotionEvent` historical points，避免日志漏掉批量派发的中间轨迹。
- 新增 `ReaderGestureTraceRecorderTest.expandsHistoricalMovePointsInGestureSummary`。

建议抓日志命令保持一行：

```bash
adb logcat -c
adb logcat -v time EasyReaderGesture:D EasyReaderTrace:D AndroidRuntime:E chromium:E '*:S' > /tmp/easyreader-gesture.log
```

如果 shell 或 adb 对 tag 报警，先确认 `chromium:E` 没有被换行拆成两个参数。

## 多触点结论

多触点可能是真机误触诱因，但这次自动化复现的主因不是多触点本身。

当前保护：

- `ACTION_POINTER_DOWN` 后 `ReaderGestureLayout` 进入 `scaling`，后续不会走章节横滑。
- `TouchGestureSampler` 只有 `pointerCount == 1` 才允许 `isChapterSwipeAllowed`。
- `ChapterSwipeInterceptor` 对 `pointerCount > 1` 直接 `PASS`。
- 已加 `multiTouchGestureDoesNotSwitchChapter`，使用真实双 pointer `MotionEvent` 覆盖多触点路径。

## 关键测试

新增/更新的重点测试：

- `TouchGestureSamplerTest.verticalReversalIsDetectedFromGestureExtremes`
- `ChapterSwipeInterceptorTest.foldedVerticalPathConsumesHorizontalTailWithoutChapterCommit`
- `ReaderGestureLayoutTest.foldedVerticalGestureWithHorizontalDriftCancelsChildUpWithoutSwitchingChapter`
- `ReaderGestureLayoutTest.lockedVerticalFoldWithHorizontalDriftCancelsChildUpWithoutSwitchingChapter`
- `ReaderGestureLayoutTest.multiTouchGestureDoesNotSwitchChapter`
- `ReaderGestureLayoutTest.historicalMoveArmsChapterSwipeAndCancelsChildBeforeUp`
- `VerticalScrollInterceptorTest.lockedFoldedVerticalScrollWithHorizontalTailCancelsChildUp`
- `ReaderGestureTraceRecorderTest.expandsHistoricalMovePointsInGestureSummary`

已执行并通过：

```bash
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.reader.gesture.TouchGestureSamplerTest --tests io.github.luoyuxiaoxiao.easyreader.reader.gesture.ChapterSwipeInterceptorTest --tests io.github.luoyuxiaoxiao.easyreader.ui.reader.ReaderGestureLayoutTest
```

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

```bash
git diff --check
```

## 手动测试建议

真机测试重点：

1. 普通点击正文：只切换 chrome，不误触切章。
2. 顶部 chrome 可见时点击工具栏区域：事件应透传给 toolbar 控件。
3. 正常竖向滚动、下滚后立即上滚：不切章。
4. 右下后右上、左下后左上等折返路径：不切章，不跳到相邻章末尾。
5. 大横向但方向不明确的斜滑：外层不切章，Readium 也不应自行切章。
6. 明确横向长滑：只切一次章节，底部进度进入目标章节开头附近。
7. 左右 `32dp` 边缘返回手势：阅读器不抢切章。
8. 双指缩放或误触第二指：不切章，缩放结束回调正常。
9. 切章后进度：上一章/下一章不应跳到 100% 附近，除非章节本身不可滚动且确实位于末章。

如果真机仍复现误切章，先区分两类：

- 有外层 `onNextChapter/onPreviousChapter`：说明 `ChapterSwipeInterceptor` commit 过宽。
- 外层没切章但 locator 跳到相邻章：说明某个路径仍把 UP 留给 Readium，需要继续扩大 cancel 条件。

## 主要文件

手势核心：

- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptor.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptorRegistry.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchGestureSampler.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/VerticalScrollInterceptor.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeInterceptor.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/GestureThresholds.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`

测试：

- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptorRegistryTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchGestureSamplerTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/VerticalScrollInterceptorTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeInterceptorTest.kt`
- `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayoutTest.kt`
