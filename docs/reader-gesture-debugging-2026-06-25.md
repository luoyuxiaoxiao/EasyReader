# Reader 手势误切章调试记录 2026-06-25

## 背景

本轮问题集中在阅读页的横向切章、竖向滚动、Readium 子层手势三者的仲裁。用户在真机上反复复现后，确认两个高风险场景：

- 快速竖向滚动或竖向折返路径中夹带横向位移，会误触发切章。
- 斜向横滑切到上一章后，上一章进度会跳到接近 100%，而纯水平横滑正常。

Bug 3 图片预览问题暂时不在本轮处理范围。

## 关键证据

真机日志中需要同时看两类标签：

- `EasyReaderGesture`：完整手势轨迹、持续时间、净位移、累计路径、锁定状态。
- `EasyReaderTrace`：外层手势锁定、切章请求、locator、WebView 滚动采样。

有效过滤命令：

```bash
rg -n "gesture#|gesture vertical-lock|gesture horizontal-lock|gesture cancel folded|gesture cancel ambiguous|gesture switch|chapter request|chapter opened|locator raw|locator ignored|locator accepted|scroll sample|scroll listener|AndroidRuntime|FATAL|chromium|tile memory" /tmp/easyreader-gesture.log
```

`chromium tile memory limits exceeded` 是 WebView/Chromium 渲染层警告，表示页面 tile 缓存超过内存预算，可能导致局部延迟绘制。它不是应用崩溃，也不是本轮误切章的直接根因。误切章的关键证据是 locator 在某次手势后跳到相邻章节，并且进度从 `0.0` 很快变成 `0.98+`。

## 根因 1：竖向折返路径把 UP 交给 Readium

用户复现的“右下后右上再松手”路径具有以下特征：

- `verticalLocked=true`
- `verticalReversed=true`
- 没有外层 `gesture switch`
- 松手后 Readium 仍可能在子层把这次手势结算为章节导航

早期判断只看单段 `dy > slop`，但 Android 会把快速手势拆成多个历史点，每个小段可能都小于 `slop`。因此改为用同一次手势的 `minY/maxY` 计算累计回撤：

- 从按下点向下超过 `slop` 后，又从最大 `Y` 回撤超过 `slop`，判定为折返。
- 从按下点向上超过 `slop` 后，又从最小 `Y` 回撤超过 `slop`，判定为折返。

修复策略：

- 竖向锁定后，如果折返路径夹带横向漂移，松手时给子 WebView 发送 `ACTION_CANCEL`。
- 不把 `ACTION_UP` 交给 Readium，避免它在 UP 阶段按自己的规则翻页/切章。

对应测试：

- `foldedVerticalGestureWithHorizontalDriftCancelsChildUp`
- `foldedVerticalGestureSplitIntoSmallMovesCancelsChildUp`

## 根因 2：斜向横滑外层不切章，但子层 Readium 会切章

后续真机日志显示，`切上一章变 100%` 不一定来自外层 `goToRelativeChapter(-1)`。典型日志链路：

```text
gesture#152 ... netDx=387 netDy=-176 pathRatio=2.137 verticalReversed=false
locator accepted ... index=6 chapter=0.0
locator accepted ... index=6 chapter=0.987...
```

这类手势没有 `gesture switch previous`，也没有 `chapter request`。也就是说：

1. 外层手势层认为方向不够纯，没有主动切章。
2. 因为没有横向锁定，也没有竖向锁定，`ACTION_UP` 继续传给 Readium。
3. Readium 子层按自己的横向规则切到上一章。
4. Readium 进入上一章时定位在上一章末尾，locator 报告 `chapter=0.98+`，UI 看起来就是上一章 100%。

修复策略：

- 外层不认可为切章的“大横向斜滑”也要消费掉，不能把 `UP` 留给 Readium。
- 条件是：不是点击、没有外层横向锁定、横向位移已经明显、竖向噪声也明显，但横向方向比没有达到外层切章阈值。
- 处理方式仍是向子层发送 `ACTION_CANCEL`，并由外层返回已处理。

后续继续收口为更强的规则：

- 章节切换只能由 `ChapterSwipeDetector` 生效。
- 如果 `ChapterSwipeDetector` 返回 `KeepReading`，但手势已经有明显横向位移，也要取消子层 `UP`。
- 这样 Readium 子层不会再获得“外层不切章，但子层自己切章”的机会。

对应测试：

- `ambiguousDiagonalSwipeCancelsChildUpWithoutSwitchingChapter`
- `foldedDiagonalPathWithLargeHorizontalNetDoesNotSwitchChapter`
- `horizontalSwipeRejectedByAppCancelsChildUpWithoutSwitchingChapter`

## 设计原则

- 应用层必须是唯一的章节切换入口。出现 `chapter request` 才代表外层主动切章。
- Readium/WebView 子层只负责正文滚动和内容点击，不允许在方向不明确的斜向横滑里自行切章。
- 纯水平且满足外层阈值的手势仍由 `ChapterSwipeDetector` 处理，保留快速短距离横滑。
- 竖向滚动和折返滚动优先保护阅读连续性，宁可不切章，也不要误切到相邻章末尾。

## 后续排查口径

如果再次出现误切章，先看日志中是否有：

- `gesture switch previous/next` 和 `chapter request`：说明是外层主动切章，检查 `ChapterSwipeDetector`。
- 没有 `gesture switch`，但 locator 跳到相邻章：说明 UP 仍穿透给 Readium，需要继续收紧子层取消条件。
- `scroll sample ignored by webview`：说明旧 WebView 样本已被挡住。
- `locator ignored`：说明切章起点或方向保护正在生效。

期望修复后的关键日志：

```text
gesture cancel folded vertical up ...
gesture cancel ambiguous child up ...
gesture cancel rejected horizontal child up ...
```

这两类日志出现时，后面不应再出现同一次手势导致的 `chapter request`，也不应由 Readium 子层跳到相邻章节末尾。
