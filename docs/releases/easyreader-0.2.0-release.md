# EasyReader 0.2.0 发布记录

## 本次更新

- 书柜首页改为 3 列网格，支持系列堆叠封面和绿色阅读进度条。
- 系列内页显示单本书网格，系列进度使用系列内书籍阅读进度平均值。
- EPUB 导入增加封面提取和 Calibre / EPUB3 series 元数据读取。
- 支持手动加入 / 移出系列，手动整理优先于自动归组。
- 新增系列归组规则入口，内置规则可启停，自定义规则支持正则和预览。

## 验证

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:compileDebugAndroidTestKotlin`
- `:app:connectedDebugAndroidTest`
