#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
用法:
  EasyReaderAPK 版本号
  EasyReaderAPK --check 版本号
  EasyReaderAPK -c 版本号
  EasyReaderAPK --remote 版本号
  EasyReaderAPK -r 版本号

示例:
  EasyReaderAPK 0.1.0
  EasyReaderAPK --check 0.1.0
  EasyReaderAPK --remote 0.1.0

说明:
  默认模式会修改版本号、运行本地预检、提交、打 tag，并 push main 和 tag。
  --remote/-r 跳过本地 Gradle 测试和构建，只通过 push tag 触发 GitHub Actions 发布。
  --check/-c 只检查并展示将要执行的动作，不修改文件、不提交、不打 tag、不 push。
USAGE
}

die() {
  echo "错误: $*" >&2
  exit 1
}

info() {
  echo "==> $*"
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令: $1"
}

run_with_timeout() {
  local seconds="$1"
  shift

  if command -v timeout >/dev/null 2>&1; then
    timeout "${seconds}s" "$@"
  else
    "$@"
  fi
}

check_mode=false
remote_mode=false
version=""

while (($# > 0)); do
  case "$1" in
    -c|--check)
      check_mode=true
      shift
      ;;
    -r|--remote)
      remote_mode=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      usage >&2
      die "未知参数: $1"
      ;;
    *)
      if [ -n "$version" ]; then
        usage >&2
        die "只能传入一个版本号"
      fi
      version="$1"
      shift
      ;;
  esac
done

if [ -z "$version" ] && (($# > 0)); then
  version="$1"
  shift
fi

[ -n "$version" ] || { usage >&2; die "缺少版本号"; }
(($# == 0)) || die "多余参数: $*"

if [[ ! "$version" =~ ^[0-9]+(\.[0-9]+){2,}$ ]]; then
  die "版本号必须形如 0.1.0，不要带 v 前缀"
fi

need_cmd git
need_cmd perl
need_cmd sort
need_cmd sha256sum

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "当前目录不在 git 仓库内"
cd "$repo_root"

gradle_file="app/build.gradle.kts"
workflow_file=".github/workflows/release-apk.yml"
release_doc="docs/releases/easyreader-${version}-release.md"
tag="v${version}"
release_branch="${EASYREADERAPK_RELEASE_BRANCH:-main}"
gradle_cmd="${EASYREADERAPK_GRADLE_CMD:-gradle}"

[ -f "$gradle_file" ] || die "找不到 $gradle_file"
workflow_exists=false
if [ -f "$workflow_file" ]; then
  workflow_exists=true
fi

branch="$(git symbolic-ref --short HEAD 2>/dev/null || true)"
branch_warning=""
if [ "$branch" != "$release_branch" ]; then
  branch_warning="当前分支是 ${branch:-detached HEAD}，真正发布前需要切到 $release_branch"
  if [ "$check_mode" = false ]; then
    die "请在 $release_branch 分支发布，当前分支是 ${branch:-detached HEAD}"
  fi
fi

current_version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$gradle_file" | head -n 1)"
current_version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$gradle_file" | head -n 1)"

[ -n "$current_version_code" ] || die "无法从 $gradle_file 读取 versionCode"
[ -n "$current_version_name" ] || die "无法从 $gradle_file 读取 versionName"

same_version=false
if [ "$current_version_name" = "$version" ]; then
  same_version=true
  next_version_code="$current_version_code"
else
  lowest_version="$(printf '%s\n%s\n' "$current_version_name" "$version" | sort -V | head -n 1)"
  if [ "$lowest_version" != "$current_version_name" ]; then
    die "目标版本 $version 不能低于当前 versionName $current_version_name"
  fi
  next_version_code="$((current_version_code + 1))"
fi

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  die "本地 tag 已存在: $tag"
fi

status_before="$(git status --short)"

if [ "$check_mode" = true ]; then
  echo "检查模式：不会修改文件、提交、打 tag 或 push。"
  echo "仓库: $repo_root"
  echo "发布分支: $release_branch"
  echo "当前分支: ${branch:-detached HEAD}"
  if [ -n "$branch_warning" ]; then
    echo "分支提示: $branch_warning"
  fi
  echo "tag: $tag"
  if [ "$remote_mode" = true ]; then
    echo "发布模式: 远端 GitHub Actions"
  else
    echo "发布模式: 本地 Gradle 预检 + 远端 GitHub Actions"
  fi
  if [ "$same_version" = true ]; then
    echo "versionCode: $current_version_code (保持不变)"
    echo "versionName: $current_version_name (保持不变)"
  else
    echo "versionCode: $current_version_code -> $next_version_code"
    echo "versionName: $current_version_name -> $version"
  fi
  echo "发布记录: $release_doc"
  if [ "$workflow_exists" = false ]; then
    echo "警告: 未找到 $workflow_file，默认模式会因此停止。"
  fi
  echo
  echo "当前工作区改动:"
  if [ -n "$status_before" ]; then
    echo "$status_before"
  else
    echo "(干净)"
  fi
  echo
  if [ "$remote_mode" = true ]; then
    echo "远端模式将执行: 跳过本地 Gradle 测试和构建 -> 更新发布文件 -> commit -> tag -> push $release_branch -> push $tag"
  else
    echo "默认模式将执行: 测试 -> release 构建 -> 更新发布文件 -> commit -> tag -> push $release_branch -> push $tag"
  fi
  exit 0
fi

[ "$workflow_exists" = true ] || die "找不到 $workflow_file，请先提交 GitHub Actions APK 发布 workflow"

if [ -n "$status_before" ]; then
  cat >&2 <<DIRTY
错误: 工作区不干净，发布前请先提交、暂存或清理这些改动：
$status_before
DIRTY
  exit 1
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  die "缺少 origin remote，无法 push 发布"
fi

remote_tag_status=0
git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1 || remote_tag_status=$?
case "$remote_tag_status" in
  0)
    die "远端 tag 已存在: $tag"
    ;;
  2)
    ;;
  *)
    die "无法检查远端 tag，请确认 SSH remote 和网络可用"
    ;;
esac

cat <<CONFIRM
⚠️ 危险操作检测！

操作类型：自动发布提交
影响范围：
  - 修改 $gradle_file（如果目标版本不同）
  - 创建或更新 $release_doc
  - 执行 git add $gradle_file $release_doc
  - 创建提交 chore: release EasyReader $version
  - 创建 tag $tag
  - push origin $release_branch
  - push origin $tag
风险评估：会把发布提交和 tag 推送到远端，tag 推送后会触发 GitHub Actions 发布 APK。

CONFIRM

if [ "$remote_mode" = true ]; then
  echo "发布模式：远端 GitHub Actions；将跳过本地 Gradle 测试和构建。"
else
  echo "发布模式：本地 Gradle 预检 + 远端 GitHub Actions。"
fi

if [ "${EASYREADERAPK_CONFIRM:-}" != "yes" ]; then
  printf '\n请输入 "确认" 继续: '
  read -r answer
  case "$answer" in
    确认|是|继续|yes|y|Y)
      ;;
    *)
      die "用户取消发布"
      ;;
  esac
fi

if [ "$same_version" = true ]; then
  info "当前 versionName 已是 $version，首发版本号保持不变"
else
  info "更新版本号"
  VERSION="$version" NEXT_VERSION_CODE="$next_version_code" perl -0pi -e '
    my $code = s/(versionCode\s*=\s*)\d+/${1}$ENV{NEXT_VERSION_CODE}/g;
    my $name = s/(versionName\s*=\s*")[^"]+(")/$1$ENV{VERSION}$2/g;
    die "versionCode/versionName replacement failed\n" unless $code == 1 && $name == 1;
  ' "$gradle_file"
fi

info "写入发布记录"
mkdir -p "$(dirname "$release_doc")"
cat > "$release_doc" <<DOC
# EasyReader $version 发布记录

## 发布信息

- 版本号：$version
- Tag：$tag
- versionCode：$next_version_code
- 发布方式：EasyReaderAPK 自动发布脚本
- 发布模式：$([ "$remote_mode" = true ] && echo "远端 GitHub Actions" || echo "本地 Gradle 预检 + 远端 GitHub Actions")

## 发布流程

1. 本地脚本确认版本号并创建发布提交。
2. 本地脚本推送 $release_branch 和 $tag。
3. GitHub Actions 通过 $workflow_file 构建、签名并上传 APK。

## 校验方式

GitHub Release 会生成：

- EasyReader-$tag-release.apk
- EasyReader-$tag-release.apk.sha256
DOC

# 远端模式用于本机 Gradle 环境或依赖下载不稳定时，把最终编译、签名和校验交给 GitHub Actions。
if [ "$remote_mode" = true ]; then
  info "远端模式：跳过本地 Gradle 测试和构建"
else
  need_cmd "$gradle_cmd"

  info "运行单元测试"
  run_with_timeout "${EASYREADERAPK_TEST_TIMEOUT_SECONDS:-180}" "$gradle_cmd" :app:testDebugUnitTest --no-daemon

  info "运行 release 构建"
  run_with_timeout "${EASYREADERAPK_BUILD_TIMEOUT_SECONDS:-600}" "$gradle_cmd" :app:assembleRelease --no-daemon
fi

info "暂存发布改动"
git add "$gradle_file" "$release_doc"

if git diff --cached --quiet; then
  die "没有可提交的发布改动"
fi

info "创建发布提交"
git commit -m "chore: release EasyReader $version"

info "创建 tag $tag"
git tag -a "$tag" -m "EasyReader $version"

info "push $release_branch"
git push origin "$release_branch"

info "push $tag"
git push origin "$tag"

cat <<DONE

发布触发完成。
GitHub Actions 会继续构建并上传 APK：
https://github.com/luoyuxiaoxiao/EasyReader/actions

Release 页面：
https://github.com/luoyuxiaoxiao/EasyReader/releases/tag/$tag
DONE
