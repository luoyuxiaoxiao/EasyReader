# EasyReader 阅读页手势架构迁移设计

日期：2026-06-26

## 背景

EasyReader 阅读页当前运行在 Android + Readium WebView 之上。历史问题集中在外层阅读手势、Readium/WebView 子层手势、阅读器 chrome 点击和竖向滚动之间的仲裁：外层未主动切章时，子层仍可能在 `ACTION_UP` 后按自己的规则处理横向翻页，导致误切章或进度跳到相邻章节末尾。

用户已手动简化当前项目，删除旧 `ChapterSwipeDetector`，引入 `TouchInterceptor`、`TouchInterceptorRegistry` 和 `ChapterSwipeInterceptor`。本设计以当前代码为准，不以过期交接文档中的旧状态为准。

readest 的价值不在于直接复制 foliate-js，而在于它把输入、桥接、优先级消费和具体消费者分开。EasyReader 应迁移这个仲裁模型，而不是继续在 `ReaderGestureLayout.dispatchTouchEvent()` 中叠加补丁。

## readest 架构要点

readest 的手势链路分为四层：

1. 低阶引擎：`packages/foliate-js/paginator.js` 处理分页拖动、snap、跨 section 导航和动画/非动画差异。
2. iframe bridge：iframe document 内的 `touch`、`wheel`、`click` 等事件通过 `postMessage` 回到宿主。
3. 宿主分发：`useIframeEvents` 将 touch 事件标准化为 `TouchDetail`，交给 `dispatchTouchInterceptors()`。
4. 消费者：阅读标尺、固定版面 swipe flip、hover/chrome、亮度手势等按优先级处理。

核心规则是：优先级高的消费者先看事件；第一个返回 consumed 的消费者拥有该手势；后续消费者不再处理。

亮度手势是特殊路径：它在 iframe document 上注册 capture 阶段、non-passive touch listener，并在激活后调用 `preventDefault()` 与 `stopImmediatePropagation()`，保证事件先于 paginator 被截获。

## readest 可借鉴阈值

本次迁移优先借鉴这些明确阈值：

- wheel page flip：累计归一化位移 `30px` 后触发一次；`200ms` 无事件后重置；触发后吞掉惯性尾迹；line mode 乘 `40px`，page mode 乘 `800px`。
- brightness：左边缘 `10%` 区域起手；纵向移动至少 `18px`，且 `abs(dy) > abs(dx)` 才激活。
- fixed-layout touch flip：`abs(dx) > abs(dy)`，`abs(dx) > 30px`，`abs(dx / dt) > 0.2px/ms`。
- hover/chrome 上滑：`deltaY < -10px`，纵向至少是横向 `2x`，横向漂移小于窗口宽度 `30%`。
- paginator snap：横向速度判断为 `abs(vx) * 2 > abs(vy)`；动画模式用末端速度，非动画/eink 更依赖平均速度。

EasyReader 第一阶段不实现 wheel、brightness 和 eink 分支，但这些数值用于指导后续扩展。

## 目标

第一阶段目标是重构现有行为，不新增用户可见功能：

- 章节切换只有 EasyReader 外层手势链可以触发。
- 一旦外层消费者认领横向章节手势，立即向子 WebView 发送 `ACTION_CANCEL`，并吞掉后续 MOVE/UP。
- 竖向滚动、pinch 缩放、tap/chrome、章节横滑使用同一套输入采样和优先级消费协议。
- `ReaderGestureLayout` 从复杂状态机收敛为 Android 输入入口、采样器、分发器和 child cancel 执行器。
- 保持现有阅读体验：竖向滚动优先保护，短点击仍交给正文优先消费，内容未消费时才切换 chrome。

## 非目标

第一阶段明确不做：

- 不迁移 foliate-js paginator。
- 不实现亮度滑动和 sqrt 感知曲线。
- 不实现 wheel page flip。
- 不实现 eink 动静分离。
- 不处理图片预览问题。
- 不重构 Readium session、进度存储、书架或导入模块。

## 目标架构

### 输入采样层

新增或强化一个手势采样对象，由 `ReaderGestureLayout` 独占维护：

- `downX/downY/downTime`
- `x/y/eventTime`
- `netDx/netDy`
- `maxAbsDx/maxAbsDy`
- `pathAbsDx/pathAbsDy`
- `velocityX/velocityY`
- `durationMs`
- `pointerCount`
- `startedFromSystemBackEdge`
- `isTapCandidate`
- `isPostVerticalScrollSuppressed`

采样层必须读取 `MotionEvent` 历史点，统一累计路径。这样折返识别、方向锁定和速度计算都基于同一份输入，避免每个 interceptor 自己重算。

### 分发层

`TouchInterceptorRegistry` 保留按 priority 降序调用的模型，但需要明确手势生命周期：

- `DOWN`：重置当前 owner 和 consumed 状态，所有 interceptor 可以初始化。
- `MOVE`：若已有 owner，只把事件交给 owner；否则按优先级询问所有 interceptor。
- `UP/CANCEL`：若已有 owner，先交给 owner 收尾；然后清理 owner。
- `PASS`：当前消费者不认领。
- `CONSUMED`：当前消费者认领并要求外层吞掉事件。
- `HANDLED_BUT_PASS_TO_CHILD`：消费者处理了副作用，但不阻止子层继续收到事件，仅用于低风险辅助逻辑。

核心注释应写在 registry 内：当前手势一旦被消费者认领，后续事件必须稳定交给同一消费者，不能在 MOVE 阶段被另一个消费者抢走。

### 消费者优先级

建议第一阶段优先级：

- `PinchScaleInterceptor`: 100
- `VerticalScrollInterceptor`: 80
- `ChapterSwipeInterceptor`: 60
- `TapChromeInterceptor`: 20

`ReaderGestureLayout` 可以暂时保留 pinch 的 `ScaleGestureDetector`，但生命周期结果应进入同一套消费协议。若实现成本过高，第一阶段允许 pinch 保持在入口层，但必须在 `TouchDetail` 中标记 `pointerCount > 1`，让其他消费者退出。

### 子层取消协议

`ReaderGestureLayout` 负责唯一的 child cancel 执行：

- interceptor 不直接调用 `super.dispatchTouchEvent(cancel)`。
- registry 返回 `CONSUMED` 且该消费者声明需要阻断子层时，`ReaderGestureLayout` 调用 `cancelChildOnce()`。
- 对章节横滑，取消必须发生在手势被 arm 的第一个 MOVE，而不是等到 UP。
- 对“大横向但未达到切章阈值”的手势，UP 阶段仍要取消子层，避免 Readium 自行翻页。

这条协议是迁移成功的关键。

## 章节横滑设计

章节横滑分为两个阶段：

1. Arm：路径累计横向足够强，外层开始拥有该手势并取消子层。
2. Commit：UP 时满足最终阈值才调用 `onNextChapter()` 或 `onPreviousChapter()`。

推荐第一阶段阈值：

- 系统返回边缘：左右 `32dp` 内不切章。
- arm 距离：`max(72dp, width * 0.18)`。
- 慢速方向比：`2.0`，提升自然横滑手感。
- 快速短滑距离：`max(48dp, width * 0.14)`。
- 快速方向比：`3.0`。
- 最小时长：`180ms`。
- fling 速度：`1800px/s`，只作为快速短滑的附加条件，不能单独触发。
- 切章冷却：`250ms`。

如果第一阶段要更保守，可以先使用当前简化代码中的 `72dp + 3.0`，但建议把 fast/slow 两套阈值保留在设计中，测试覆盖后再打开。

## 竖向滚动设计

竖向滚动消费者优先于章节横滑：

- 当 `pathAbsDy > pathAbsDx * 1.2` 且 `pathAbsDy > 12dp` 时锁定竖向滚动。
- 锁定后触发 `onVerticalScrollStarted()`。
- UP/CANCEL 时触发 `onVerticalScrollFinished()`，并开启 `450ms` post-scroll suppress 窗口。
- suppress 窗口内章节横滑不允许 commit。

竖向滚动消费者默认不取消子 WebView，因为正文滚动应由 Readium/WebView 处理。只有未来恢复折返保护时，才在明确存在横向尾迹风险的 UP 阶段取消子层。

## Tap/Chrome 设计

tap 消费者是最后兜底：

- tap slop：`8dp`。
- tap timeout：`250ms`。
- 顶部 chrome 控件区域：`96dp` 内，且 chrome 已显示时，直接 pass-through 给控件。
- tap 先交给 child；随后调用 `onReaderTapCandidate(x, y)`。
- 若内容通过 Readium listener 或 JS bridge 标记已消费，则不切换 chrome。
- 若 child 未处理，立即切换 chrome。
- 若 child 已处理但内容消费信号可能稍晚，等待 `180ms` 再决定是否切换 chrome。

这保留现有脚注、链接、图片点击与 chrome 点击之间的优先级。

## 错误处理和日志

重构后应保留低成本日志，默认只输出关键状态：

- gesture id、phase、owner、result。
- arm/commit/cancel child 的原因。
- chapter switch 方向、阈值命中信息。
- vertical lock start/finish。
- tap 被内容消费或触发 chrome。

日志标签继续使用 `EasyReaderGesture` 和 `EasyReaderTrace`，避免真机排查口径变化。

## 测试策略

第一阶段必须先补单元测试再改实现：

- registry：priority 顺序、owner 锁定、MOVE 后续吞掉、UP/CANCEL 清理。
- sampler：历史点累计路径、净位移、峰值位移、速度、tap 判定。
- chapter：慢速横滑、快速短横滑、竖向漂移拒绝、系统返回边缘拒绝、冷却拒绝。
- vertical：竖向锁定、竖向后 suppress 窗口阻止切章。
- layout：章节 arm 后发送一次 `ACTION_CANCEL`，后续 UP 不再传给 child。
- layout：tap 仍按内容优先，延迟消费窗口有效。

测试命令建议：

```bash
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest
```

完成实现后再跑：

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

## 迁移步骤

1. 扩展 `TouchDetail` 和采样层，补 sampler 单元测试。
2. 重写 `TouchInterceptorRegistry` 的 owner 生命周期，补 registry 测试。
3. 抽出 `VerticalScrollInterceptor`，保持现有竖向行为。
4. 重写 `ChapterSwipeInterceptor`，先保守迁移当前阈值，再用测试打开 fast/slow 阈值。
5. 抽出 tap/chrome 处理，保持内容优先和 `180ms` 延迟窗口。
6. 精简 `ReaderGestureLayout`，只保留输入入口、分发、child cancel、pass-through 和必要回调。
7. 跑单元测试、assemble，并在真机用日志验证章节切换和竖向滚动。

## 风险

- `ACTION_CANCEL` 时机过早会影响正文选择或滚动；因此只有章节 arm 或明确横向尾迹时取消子层。
- 过强的章节阈值会让横滑手感变钝；fast/slow 两档阈值必须通过测试和真机手感验证。
- tap 延迟窗口过短会漏掉 WebView/JS 稍晚的消费信号；过长会让 chrome 响应迟滞。第一阶段沿用 `180ms`。
- Readium 内部 WebView 结构可能变化；child cancel 仍应只依赖 Android 事件分发，不依赖具体 WebView 层级。

## 验收标准

- 现有 tap、竖向滚动、pinch 缩放、章节横滑行为保持可用。
- 外层未 commit 的横向尾迹不会触发 Readium 子层自行切章。
- 单元测试覆盖 registry、sampler、chapter、vertical、tap/layout 关键路径。
- `:app:testDebugUnitTest :app:assembleDebug` 成功。
- 真机日志能明确区分：外层章节切换、子层取消、竖向滚动、tap 内容消费。
