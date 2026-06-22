# EasyReader 0.2.1 发布记录

## 本次更新

- 提高横向滑动切章阈值，降低纵向滚动时误触切章的概率。
- 书架增加“整理”二级菜单，支持按标题、作者、阅读进度、导入时间排序，并支持升序/降序切换。
- 单书和系列卡片阅读进度条加粗，书架排序改为自然排序，数字编号更符合阅读顺序。
- 自动归组增加简单规则：填写大系列名后，可按 `[S...]` 文件名前缀自动归为同一大系列。
- 自定义归组规则增加删除入口，导入图书增加删除入口和确认框。
- 系列内页返回行为修正为先回到上一级书架。

## 验证

- `git diff --check`
- `git diff main...HEAD --check`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:compileDebugAndroidTestKotlin`
- 模拟器 `AppDatabaseTest`
- 模拟器启动 smoke
