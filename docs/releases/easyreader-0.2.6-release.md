# EasyReader 0.2.6 发布记录

## 本次更新

- 重构阅读页手势链路：采样层、优先级拦截器和消费者分离，降低局部补丁互相打架的风险。
- 收紧 Readium/WebView 子层透传：折返路径、竖向滚动后横向尾迹、系统返回边缘横滑、顶部 chrome 横向拖动和多触点缩放都不再把危险 UP 交给子层结算。
- 恢复 debug 手势日志：`EasyReaderGesture` 只在 debug 版输出，并展开 `MotionEvent` historical points，方便继续定位真实轨迹。
- 更新应用图标：使用 EasyReader 图标资源并生成 Android launcher/adaptive icon。

## 验证

- `:app:testDebugUnitTest :app:assembleDebug`
- `tests/EasyReaderAPK_test.sh`
- `git diff --check`

