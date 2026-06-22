# EasyReader 书柜 UI 交接

## 完成内容

- 书柜首页改为 3 列网格。
- 系列以堆叠封面显示，并显示 `N 本` 数量角标。
- 系列内页显示单本书网格。
- 单本和系列进度条使用 `ReadingProgress.totalProgression`，系列进度为单本显示进度平均值。
- EPUB 导入提取封面和 Calibre / EPUB3 series 元数据。
- 支持内置归组规则启停和用户自定义正则规则。
- 支持手动加入和移出系列，手动系列优先于自动归组。

## 验证

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:compileDebugAndroidTestKotlin`
- `:app:connectedDebugAndroidTest`

## 调试记录

- connected test 曾发现封面导入测试失败，根因是测试夹具中手写 tiny PNG 字节无法被 Android `BitmapFactory` 解码。
- 已改为在 androidTest fixture 中用 Android `Bitmap` 生成 PNG，导入测试随后通过。

## 手动回归重点

- 导入 EPUB 后首页显示 3 列网格。
- 带封面的 EPUB 在书柜中显示封面。
- 标题匹配规则的书籍折叠为系列堆叠项。
- 点击系列进入系列内页，单本可正常打开阅读。
- 长按书籍进入选择模式，快捷方式入口仍可用。
- “加入系列”覆盖自动规则，“移出系列”后自动规则恢复生效。
- “归组规则”里内置规则可启停，自定义正则无效时显示错误提示。
