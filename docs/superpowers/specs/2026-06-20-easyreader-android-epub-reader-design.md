# EasyReader Android EPUB Reader 第一版技术方案

## 背景

EasyReader 的第一版目标是做一个体积小、启动快、阅读足够丝滑的 Android 手机端开源小说阅读器。第一版只支持 EPUB，不支持 PDF、云同步、账号、AI、OPDS、复杂书库管理等扩展功能。

调研参考项目包括：

- Readest：跨平台 `Next.js + React + Tauri v2 + Rust` 架构，功能完整但对 EasyReader 第一版过重。
- foliate-js：浏览器内电子书渲染库，结构清晰，但 API 不稳定，且 Android 原生集成需要长期维护 JS bridge。
- Readium Kotlin Toolkit：Android/Kotlin EPUB 工具包，支持 EPUB 2/3、滚动、定位、偏好设置和成熟的 Navigator。
- KOReader：LuaJIT + 原生阅读引擎，功能强但不是现代 Android 原生项目的合适基座。

结论：第一版采用 Android 原生外壳，使用 Readium Kotlin Toolkit 负责 EPUB 解析和渲染。

## 第一版目标

第一版只实现最小可用阅读闭环：

- 批量导入 EPUB。
- 本地书架展示。
- 打开 EPUB 进行滚动阅读。
- 同一章内保持纵向滚动。
- 任意位置左右滑动切换上一章或下一章。
- 点击阅读区域显示或隐藏阅读 chrome。
- 滚动时隐藏阅读 chrome。
- 底部显示两个进度百分比：全书进度和本章进度。
- 支持批量把书籍添加为桌面快捷方式。
- 通过桌面快捷方式打开指定书籍并恢复上次阅读位置。

明确不做：

- PDF、MOBI、AZW3、TXT 等其他格式。
- 云同步、账号体系、在线书源、AI、翻译、TTS。
- 分页阅读模式。
- 复杂标注、笔记、全文搜索。
- 自研 EPUB 排版引擎。

## 技术选型

推荐栈：

- Kotlin
- Jetpack Compose
- Readium Kotlin Toolkit
- Room
- DataStore
- Kotlin Coroutines and Flow
- AndroidX ShortcutManagerCompat

选择原因：

- Compose 负责原生 Android UI，便于保持小体积、好维护和系统集成。
- Readium 负责 EPUB 标准、CSS、图片、目录、定位和 WebView 渲染，避免自研排版。
- Room 保存书籍、章节、进度和快捷方式映射。
- DataStore 保存全局阅读偏好。
- ShortcutManagerCompat 处理桌面 pinned shortcut 的兼容逻辑。

第一版不拆多个 Gradle module。先在单个 `app` module 内按 package 分层，降低工程启动成本。等功能稳定后，再按实际复杂度拆分模块。

## 高层架构

```text
app
├── ui.bookshelf
│   ├── 书架列表
│   ├── EPUB 批量导入入口
│   ├── 批量选择
│   └── 批量桌面快捷方式入口
├── ui.reader
│   ├── ReaderActivity
│   ├── 阅读 chrome
│   ├── 双进度显示
│   ├── 加载态
│   └── 错误态
├── reader.readium
│   ├── EPUB 打开流程
│   ├── Publication 生命周期
│   ├── EpubNavigatorFragment 创建
│   ├── 阅读偏好提交
│   └── 章节跳转
├── reader.gesture
│   └── 横滑切章手势状态机
├── domain.book
│   ├── Book
│   ├── Chapter
│   ├── ReadingProgress
│   └── ReadingPreferences
├── data.local
│   ├── Room Database
│   ├── Entity
│   ├── DAO
│   └── Repository
├── data.settings
│   └── DataStore
└── shortcut
    ├── pinned shortcut 创建
    ├── shortcut intent 生成
    └── deep link 解析
```

核心原则：

- UI 不直接操作 Readium 细节。
- 业务层只依赖领域模型和 repository。
- Readium 封装层只暴露 EasyReader 需要的打开、定位、切章、偏好提交能力。
- 章节切换是 Navigator 内部导航，不是重建页面。

## EPUB 打开与渲染

打开流程：

```text
bookId
-> 查询本地书籍记录
-> 读取 EPUB 文件 Uri 或 File
-> AssetRetriever
-> PublicationOpener
-> Publication
-> EpubNavigatorFactory
-> EpubNavigatorFragment
-> 使用已保存 Locator 恢复位置
```

阅读页使用混合视图结构：

```text
ReaderActivity
└── ReaderGestureLayout
    ├── FragmentContainerView  承载 EpubNavigatorFragment
    └── ComposeView            绘制阅读 chrome 和进度
```

这样做的原因：

- `EpubNavigatorFragment` 可以完整接管 EPUB WebView 渲染。
- 自定义 `ReaderGestureLayout` 可以在 WebView 外层统一识别横滑切章。
- Compose overlay 可以独立控制顶部栏、底部进度、显示隐藏动画。
- 切章时不销毁 Fragment，不重新解析 EPUB，不重建 WebView。

Readium Navigator 初始偏好：

```text
scroll = true
publisherStyles = true
pageMargins = 适合手机阅读的默认值
fontSize = 默认 100%
theme = 跟随 EasyReader 阅读主题
```

第一版只暴露少量阅读设置：

- 字号。
- 字体族，优先使用系统字体。
- 文字颜色和背景色。
- 行距或段距可后续迭代；第一版如时间紧张可只做默认值。

## 不重建页面的章节切换

错误方式：

```text
横滑
-> 销毁 ReaderActivity 或 EpubNavigatorFragment
-> 重新打开 EPUB
-> 重新解析书籍
-> 创建新 WebView
-> 跳到目标章节
```

该方式会导致白屏、卡顿、内存抖动和进度错乱。

正确方式：

```text
打开书籍时创建一次 Publication 和 Navigator
-> 用户横滑
-> 根据当前 readingOrderIndex 计算目标章节
-> 调用 navigator.go(target)
-> 监听 currentLocator 更新进度
```

同一本书阅读期间，`Publication` 和 `EpubNavigatorFragment` 应保持存在。章节切换只是导航行为，不是页面生命周期重建。

## 章节模型

第一版的“章节”定义为 EPUB reading order/spine 项。原因是它和 Readium 的导航模型一致，适合快速稳定实现横滑切章。

后续如果遇到一本 EPUB 的单个 spine 文件包含多个目录小节，可以再引入 TOC 子章节映射。第一版不提前复杂化。

章节数据来源：

```text
Publication.readingOrder
Publication.tableOfContents
```

保存到本地的章节信息：

```text
bookId
index
href
title
linear
```

其中 `title` 优先来自 TOC 匹配，匹配不到时使用 `第 N 章` 作为显示名称。

## 阅读进度模型

进度以 Readium `Locator` 为准，不使用页码。

保存内容：

```text
bookId
locatorJson
readingOrderIndex
totalProgression
chapterProgression
updatedAt
```

全书进度：

```text
locator.locations.totalProgression
```

本章进度：

```text
locator.locations.progression
```

如果本章进度为空，第一版 fallback 为：

```text
当前 readingOrderIndex 内的滚动相对位置
```

如果无法稳定获得滚动相对位置，则显示 `0.00%` 或保留上一次有效值，避免显示跳变。

显示格式：

```text
左下：全书进度，如 0.4%
右下：本章进度，如 24.96%
```

刷新策略：

- `currentLocator` 更新时刷新内存状态。
- UI 可实时显示最新值。
- 数据库写入节流到 `500-800ms`。
- 切章、退出阅读页、应用进入后台时立即保存。

## 阅读 chrome

阅读 chrome 指 EasyReader 自己的阅读外壳控件，不是浏览器：

- 顶部标题栏。
- 返回、菜单、搜索按钮。
- 底部全书进度和本章进度。
- 后续可能加入目录、设置入口。

全屏模式：

- 隐藏 app 顶部标题栏。
- 保留 Android 系统状态栏和导航栏。
- 正文区域使用 system bar inset，避免内容被遮挡。

非全屏模式：

- 显示顶部标题栏。
- 标题栏包含菜单、书名、作者、搜索、更多入口。
- 正文区域位于标题栏下方。

显隐规则：

- 点击正文区域：切换 chrome 显隐。
- 纵向滚动开始：隐藏 chrome。
- 章节切换完成：短暂显示底部进度。
- 打开阅读页初始：短暂显示 chrome，然后自动隐藏。

第一版不在正文内显示电量、时间等信息，依赖系统状态栏即可。

## 手势状态机

用户要求任意位置左右滑动切换章节。为降低误触，使用状态机和阈值组合判断。

状态：

```text
Idle
-> Tracking
-> VerticalLocked
-> HorizontalCandidate
-> ChapterSwitching
-> Cooldown
```

状态含义：

- `Idle`：没有手势。
- `Tracking`：收到触摸开始，等待方向明确。
- `VerticalLocked`：判定为纵向滚动，本次手势交给 WebView。
- `HorizontalCandidate`：判定为可能横滑切章。
- `ChapterSwitching`：触发上一章或下一章。
- `Cooldown`：切章后的短暂冷却，防止连续误触。

推荐阈值：

```text
minHorizontalDistance = max(72dp, screenWidth * 0.24)
fastFlingVelocity = 700-900dp/s
directionRatio = abs(dx) / abs(dy) >= 1.8
cooldown = 250ms
```

触发条件：

```text
abs(dx) >= minHorizontalDistance
and abs(dx) >= abs(dy) * directionRatio
```

或：

```text
abs(dx) >= 48dp
and horizontalVelocity >= fastFlingVelocity
and abs(dx) >= abs(dy) * directionRatio
```

一旦纵向滚动先满足锁定条件，本次手势不再触发切章。

方向：

```text
向左滑：下一章
向右滑：上一章
```

需要适配 RTL EPUB 时，后续可按书籍 reading progression 反转方向。第一版先按中文小说 LTR 阅读习惯实现。

核心状态机必须写简体中文注释，说明每个状态为什么存在，避免后续改动破坏阅读手感。

## 导入与本地存储

批量导入使用 Android Storage Access Framework：

```text
ACTION_OPEN_DOCUMENT
allowMultiple = true
MIME = application/epub+zip
```

导入流程：

```text
用户选择 EPUB
-> 后台复制到 app 私有目录
-> 计算 SHA-256
-> 去重
-> Readium 解析元数据和封面
-> 保存书籍记录
-> 书架刷新
```

文件布局：

```text
files/books/{bookId}/book.epub
files/books/{bookId}/cover.jpg
```

去重策略：

- 第一版用 SHA-256 去重。
- 如果 SHA-256 已存在，则不重复复制，只更新 `lastImportedAt` 或提示已存在。

导入错误处理：

- 文件为空：提示文件无效。
- 非 EPUB：提示格式不支持。
- 解析失败：保留错误信息，不加入书架。
- 复制失败：提示存储错误。

## 数据库设计

`books`：

```text
id: String
title: String
author: String?
filePath: String
sha256: String
coverPath: String?
createdAt: Long
updatedAt: Long
lastOpenedAt: Long?
```

`chapters`：

```text
id: String
bookId: String
index: Int
href: String
title: String
linear: Boolean
```

`reading_progress`：

```text
bookId: String
locatorJson: String
readingOrderIndex: Int
totalProgression: Double?
chapterProgression: Double?
updatedAt: Long
```

`shortcuts`：

```text
bookId: String
shortcutId: String
createdAt: Long
lastRequestedAt: Long
```

第一版不需要复杂迁移策略。数据库版本从 1 开始，并为后续迁移保留清晰实体边界。

## 桌面快捷方式

Android 不允许应用静默批量把快捷方式放到桌面。批量添加应实现为队列式请求：

```text
用户在书架多选书籍
-> 点击添加到桌面
-> 为每本书生成 ShortcutInfo
-> 逐个调用 requestPinShortcut
-> 记录已请求的 shortcutId
```

Shortcut intent：

```text
easyreader://book/{bookId}
```

打开流程：

```text
Launcher 点击快捷方式
-> MainActivity/ReaderActivity 接收 deep link
-> 解析 bookId
-> 查询书籍
-> 打开 ReaderActivity
-> 恢复 reading_progress.locatorJson
```

图标：

- 优先使用书籍封面生成 adaptive shortcut icon。
- 封面过大时压缩到合理尺寸。
- 无封面时使用 EasyReader 默认图标。

## 错误处理

阅读页错误分为：

- 书籍不存在。
- EPUB 文件缺失。
- EPUB 解析失败。
- Readium Navigator 创建失败。
- 目标章节不存在。

处理方式：

- 书籍不存在或文件缺失：返回书架并显示错误。
- 解析失败：显示错误页，提供返回书架。
- 章节不存在：忽略本次切章并短暂提示已经到达边界。
- 进度恢复失败：从正文开头打开，同时保留原进度记录用于诊断。

边界提示保持轻量，不做复杂弹窗。阅读场景中优先避免打断。

## 性能要求

第一版性能目标：

- 打开普通 EPUB 不出现长时间白屏。
- 横滑切章不重建 Activity、Fragment 或 WebView。
- 纵向滚动时不因数据库写入造成掉帧。
- 导入和封面解析在后台线程执行。
- 阅读页只保留必要 Compose overlay，避免复杂重组。

关键实践：

- 进度写入节流。
- 切章只调用 Navigator 导航。
- 书籍解析结果尽量复用当前 Reader 会话。
- Compose chrome 状态尽量小而稳定。
- 图片封面解码和压缩放到后台。

## 测试策略

单元测试：

- SHA-256 去重逻辑。
- `Locator` JSON 序列化和反序列化。
- 全书进度和本章进度格式化。
- 横滑切章状态机。
- shortcut deep link 生成和解析。

集成测试：

- 批量导入 EPUB。
- 导入重复 EPUB。
- 从书架打开书籍。
- 保存并恢复阅读进度。
- shortcut intent 打开指定书籍。

人工验收：

- 长章节纵向滚动是否顺滑。
- 任意位置左右滑切章是否稳定。
- 普通上下滚动是否容易误触切章。
- 全屏和非全屏 chrome 显示是否符合参考图。
- 退出后再次打开是否恢复位置。

## 后续迭代方向

第一版完成后再考虑：

- 更细粒度 TOC 子章节进度。
- 目录页。
- 阅读主题和字体高级设置。
- 书籍封面网格优化。
- 高亮和书签。
- 搜索。
- OPDS 或本地文件夹扫描。
- PDF 支持。
- 云同步。

这些功能不进入第一版，避免过早扩大架构。

## 已确认决策

- 使用 Android 原生外壳加 Readium EPUB 渲染。
- 第一版只支持 EPUB。
- 第一版只支持滚动阅读，不支持分页。
- 任意位置左右滑动切换章节。
- 同一章内保持纵向滚动。
- 使用双百分比显示全书进度和本章进度。
- 保留系统状态栏和导航栏，不在阅读器内重复显示电量和时间。
- 支持批量添加桌面快捷方式。
- 桌面快捷方式直达指定书籍并恢复位置。
