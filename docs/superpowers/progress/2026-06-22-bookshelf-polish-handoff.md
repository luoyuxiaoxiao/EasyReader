# EasyReader 书架优化交接

日期：2026-06-22

## 当前状态

- 工作树：`/home/luoyu/Projects/AndroidAPP/EasyReader/.worktrees/bookshelf-polish`
- 分支：`feature/bookshelf-polish`
- 基点：`87bd10c docs: plan bookshelf polish implementation`
- 当前提交：`243cefe feat: add bookshelf organize menu`

本分支完成了书架 UI 整理入口、排序、简单归组规则、删除入口、系列页返回、阅读进度条和横滑切章阈值调整。

## 已完成内容

### 书架与整理入口

- 顶部增加“整理”二级菜单。
- 排序入口放入二级菜单，支持：
  - 标题
  - 作者
  - 阅读进度
  - 导入时间
  - 升序/降序切换
- 自动归组规则入口也放入二级菜单，平常书架仍以手动整理为主。
- 书架单书与系列卡片下方阅读进度条高度改为 `8.dp`，绿色进度条保持。
- 系列页增加返回处理：从系列内滑动/系统返回时回到上一级书架，不直接退出应用。

### 自动归大系列规则

- 新增简单规则：用户只填写“大系列名”，应用按 `[S...]` 前缀自动归为同一大系列。
- 适配样例：
  - `[S1_01]某魔法的禁书目录 01X`
  - `[S2_01]新约 某魔法的禁书目录 01X`
  - `[S5_02_01]某科学的超电磁炮SS 学艺都市篇X`
  - `[S6_24.12.10]创约 某魔法的禁书目录SS 黄金黎明篇X`
- 简单规则会把这些书归入用户填写的大系列名，例如“魔法禁书目录”。
- 旧的正则规则仍保留，适合高级用户自定义。
- 自定义规则增加删除按钮和确认框。

### 排序与自然排序

- 新增 `BookshelfSettingsStore` 持久化书架排序设置。
- 新增 `NaturalSort`，避免 `10` 排在 `2` 前面。
- 系列内和书架列表都走统一排序入口。
- `[S...]` 前缀规则会尝试使用前缀里的编号辅助自然排序。

### 删除导入图书

- 选择模式增加“删除”按钮和确认框。
- 删除数据库书籍记录时，会删除应用私有导入目录中的 `book.epub` 副本和封面目录。
- 不删除用户下载目录或外部存储中的原始 EPUB。
- 数据库级联删除已通过 instrumented 测试覆盖。

### 横滑切章阈值

- `ChapterSwipeDetector` 横滑距离阈值提高到 `max(72dp, screenWidth * 0.18)`。
- 快速滑动距离阈值为 `72dp`，快速速度阈值为 `900dp/s`。
- 目的：减少用户本想滚动阅读时误触切章。

## 关键文件

- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGrouping.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfModels.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/NaturalSort.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/BookshelfSettingsStore.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt`
- `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`

## 验证记录

已在本分支执行：

```bash
git diff --check
git diff main...HEAD --check
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest :app:assembleDebug
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:compileDebugAndroidTestKotlin
timeout 180s /home/luoyu/Android/Sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -e class io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabaseTest io.github.luoyuxiaoxiao.easyreader.test/androidx.test.runner.AndroidJUnitRunner
```

结果：

- `git diff --check`：无输出。
- `git diff main...HEAD --check`：无输出。
- `:app:testDebugUnitTest :app:assembleDebug`：`BUILD SUCCESSFUL`。
- `:app:compileDebugAndroidTestKotlin`：`BUILD SUCCESSFUL`。
- 模拟器 `AppDatabaseTest`：`OK (3 tests)`。
- 模拟器启动 smoke：应用进程可启动，启动后未发现进程级 error logcat 输出。

## 设备与安装注意事项

真机 `H6TOMF49SGBU89GI` 当前仍保留 release `v0.2.0`。本轮为了避免再次触发 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，instrumented 测试没有让 Gradle 同时操作真机，而是手动定向到模拟器：

```bash
/home/luoyu/Android/Sdk/platform-tools/adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
/home/luoyu/Android/Sdk/platform-tools/adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

此前 v0.2.0 真机签名冲突原因是系统克隆用户 `User 10: system_clone` 中残留旧 debug 签名包。若后续真机再次遇到签名冲突，优先检查：

```bash
adb shell pm list users
adb shell pm list packages -u | rg 'easyreader|luoyuxiaoxiao'
adb shell dumpsys package io.github.luoyuxiaoxiao.easyreader
```

需要清理指定用户空间时，先确认用户 ID，再执行：

```bash
adb shell pm uninstall --user <USER_ID> io.github.luoyuxiaoxiao.easyreader
```

## 后续建议

- 合并前建议在真机上手动检查：
  - 书架“整理”菜单
  - 排序切换
  - 简单归组规则弹窗
  - 自定义规则删除确认
  - 选择书籍删除确认
  - 系列页返回行为
- 若要把本轮改动发布到用户真机，建议重新走 release 构建与安装流程，避免 debug 包覆盖 release 包。
