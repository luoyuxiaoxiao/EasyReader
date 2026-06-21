# GitHub Actions APK 发布流程

本文记录 EasyReader 如何自动构建、签名并发布 APK 到 GitHub Release。

## 核心结论

Release 发布不需要个人 GitHub token。GitHub Actions 会为每次运行自动注入 `${{ secrets.GITHUB_TOKEN }}`，本仓库 workflow 通过 `permissions: contents: write` 创建 Release 并上传资产。

需要额外配置的是 Android release 签名材料。签名材料只放在本机和 GitHub Repository Secrets，不能提交到仓库。

## 首次配置 Secrets

进入仓库页面：

`Settings` -> `Secrets and variables` -> `Actions` -> `New repository secret`

需要配置 4 个 secret：

| Secret 名称 | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | release keystore 文件的 base64 内容 |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | release key alias |
| `RELEASE_KEY_PASSWORD` | release key 密码 |

Codex 生成密钥后，会把这些值写在本机的 `easyreader-release-secrets.env` 文件中。不要把该文件提交到仓库，也不要把内容贴到公开位置。

## 自动发布方式

workflow 文件：

`.github/workflows/release-apk.yml`

推荐使用仓库内脚本触发发布：

```bash
./EasyReaderAPK --check 0.1.0
```

确认无误并且已经位于 `main` 分支后执行：

```bash
./EasyReaderAPK 0.1.0
```

如果本地 Gradle 环境或依赖下载不稳定，使用远端模式：

```bash
./EasyReaderAPK --remote 0.1.0
```

短参数等价：

```bash
./EasyReaderAPK -c 0.1.0
./EasyReaderAPK -r 0.1.0
```

脚本会在真正提交和 push 前再次提示确认。输入 `确认` 后继续。

## 脚本行为

`EasyReaderAPK.sh` 默认会：

1. 检查真正发布时当前分支是 `main`。
2. 检查工作区干净，避免把无关改动放进 release 提交。
3. 检查目标 tag 本地和远端不存在。
4. 如果目标版本不同，自动递增 `versionCode` 并更新 `versionName`。
5. 如果目标版本等于当前 `versionName`，允许用于首次发布并保持 `versionCode` 不变。
6. 生成 `docs/releases/easyreader-<版本号>-release.md`。
7. 默认模式运行 `gradle :app:testDebugUnitTest --no-daemon`。
8. 默认模式运行 `gradle :app:assembleRelease --no-daemon`。
9. 只暂存 `app/build.gradle.kts` 和发布记录。
10. 提交 `chore: release EasyReader <版本号>`。
11. 创建 `v<版本号>` tag。
12. push `main` 和 tag。

使用 `--remote` 或 `-r` 时，脚本会跳过本地 Gradle 测试和构建，直接提交、打 tag、push，由 GitHub Actions 完成最终编译、签名、校验和 Release 上传。

本地 Gradle 命令默认使用全局 `gradle`。如果需要改用 wrapper：

```bash
EASYREADERAPK_GRADLE_CMD=./gradlew ./EasyReaderAPK 0.1.1
```

## GitHub Actions 行为

推送 `v*` tag 后，GitHub Actions 会自动：

1. checkout 对应 tag。
2. 安装 JDK 21 和 Android SDK。
3. 安装 Android platform 36 和 build-tools 36.0.0。
4. 执行 `./gradlew :app:assembleRelease --stacktrace --no-daemon`。
5. 从 GitHub Secrets 解码 keystore。
6. 对 `app-release-unsigned.apk` 执行 zipalign 和 apksigner。
7. 校验 APK 签名和 zipalign。
8. 生成 SHA-256。
9. 创建或更新 GitHub Release。
10. 上传签名 APK 和 `.sha256` 文件。

Release 资产命名格式：

```text
EasyReader-v0.1.0-release.apk
EasyReader-v0.1.0-release.apk.sha256
```

## 手动重跑发布

如果 tag 已存在但需要重新发布：

1. 打开 GitHub 仓库的 `Actions` 页面。
2. 选择 `Release APK` workflow。
3. 点击 `Run workflow`。
4. 输入 tag，例如 `v0.1.0`。
5. 运行完成后检查对应 Release 资产。

workflow 设置了 `overwrite_files: true`，同名 APK 和 SHA-256 资产会被覆盖。

## 常见失败

### signing secret is required

说明某个 Repository Secret 没有配置，或名称写错。按“首次配置 Secrets”补齐。

### versionName mismatch

说明 tag 和 `app/build.gradle.kts` 的 `versionName` 不一致。修正版本号并重新提交，再创建新的 tag。

### Unsigned APK not found

说明 Gradle release 构建没有产出 `app-release-unsigned.apk`。先检查 `./gradlew :app:assembleRelease` 的失败日志。

### DOES NOT VERIFY

说明签名材料、密码或 alias 不匹配。重新确认 keystore、alias、密码是否和本地发布时一致。

## 安全注意

- 不要把 keystore、密码、个人 GitHub token 提交到仓库。
- workflow 不需要个人 GitHub token。
- 更换 keystore 后，历史 APK 与新 APK 的签名证书会不同，安装升级可能失败。
