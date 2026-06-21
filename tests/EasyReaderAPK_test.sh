#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$repo_root/EasyReaderAPK.sh"
tmp_dirs=()

cleanup() {
  local dir
  for dir in "${tmp_dirs[@]}"; do
    rm -rf "$dir"
  done
  rm -f /tmp/easyreader-invalid-version.out /tmp/easyreader-dirty-release.out
}

trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "$haystack" != *"$needle"* ]]; then
    fail "expected output to contain: $needle"
  fi
}

make_fixture() {
  local dir="$1"
  local version_code="${2:-1}"
  local version_name="${3:-0.1.0}"

  mkdir -p "$dir/app" "$dir/docs/releases" "$dir/.github/workflows"
  cat > "$dir/app/build.gradle.kts" <<GRADLE
android {
    defaultConfig {
        versionCode = $version_code
        versionName = "$version_name"
    }
}
GRADLE

  cat > "$dir/.github/workflows/release-apk.yml" <<'WORKFLOW'
name: Release APK
on:
  push:
    tags:
      - "v*"
WORKFLOW

  git -C "$dir" init -q
  git -C "$dir" config user.email "test@example.com"
  git -C "$dir" config user.name "EasyReader Test"
  git -C "$dir" checkout -q -b main
  git -C "$dir" add app/build.gradle.kts .github/workflows/release-apk.yml
  git -C "$dir" commit -q -m "initial"
}

add_bare_origin() {
  local dir="$1"
  local origin="$2"

  git init -q --bare "$origin"
  git -C "$dir" remote add origin "$origin"
  git -C "$dir" push -q -u origin main
}

test_check_mode_allows_current_version_for_first_release() {
  local tmp
  tmp="$(mktemp -d)"
  tmp_dirs+=("$tmp")
  make_fixture "$tmp" 1 0.1.0

  local output
  output="$(cd "$tmp" && bash "$script" --check 0.1.0)"

  assert_contains "$output" "检查模式"
  assert_contains "$output" "tag: v0.1.0"
  assert_contains "$output" "versionCode: 1 (保持不变)"
  assert_contains "$output" "versionName: 0.1.0 (保持不变)"
}

test_future_version_increments_version_code() {
  local tmp
  tmp="$(mktemp -d)"
  tmp_dirs+=("$tmp")
  make_fixture "$tmp" 1 0.1.0

  local output
  output="$(cd "$tmp" && bash "$script" --check 0.1.1)"

  assert_contains "$output" "versionCode: 1 -> 2"
  assert_contains "$output" "versionName: 0.1.0 -> 0.1.1"
}

test_short_remote_flag_publishes_first_release_without_local_gradle() {
  local tmp origin
  tmp="$(mktemp -d)"
  origin="$(mktemp -d)"
  tmp_dirs+=("$tmp" "$origin")
  make_fixture "$tmp" 1 0.1.0
  add_bare_origin "$tmp" "$origin"

  local output
  output="$(cd "$tmp" && EASYREADERAPK_CONFIRM=yes bash "$script" -r 0.1.0)"

  assert_contains "$output" "远端模式：跳过本地 Gradle 测试和构建"
  assert_contains "$output" "发布触发完成"
  grep -q 'versionCode = 1' "$tmp/app/build.gradle.kts" || fail "versionCode should stay unchanged for first release"
  grep -q 'versionName = "0.1.0"' "$tmp/app/build.gradle.kts" || fail "versionName should stay unchanged for first release"
  grep -q '发布模式：远端 GitHub Actions' "$tmp/docs/releases/easyreader-0.1.0-release.md" || fail "release doc does not mention remote mode"
  git --git-dir="$origin" rev-parse -q --verify "refs/tags/v0.1.0" >/dev/null || fail "remote tag was not pushed"
}

test_dirty_worktree_is_rejected_for_real_release() {
  local tmp origin
  tmp="$(mktemp -d)"
  origin="$(mktemp -d)"
  tmp_dirs+=("$tmp" "$origin")
  make_fixture "$tmp" 1 0.1.0
  add_bare_origin "$tmp" "$origin"

  echo "draft" > "$tmp/feature-note.txt"

  if (cd "$tmp" && EASYREADERAPK_CONFIRM=yes bash "$script" -r 0.1.0) >/tmp/easyreader-dirty-release.out 2>&1; then
    fail "dirty worktree release was accepted"
  fi

  local output
  output="$(cat /tmp/easyreader-dirty-release.out)"
  assert_contains "$output" "工作区不干净"
  assert_contains "$output" "?? feature-note.txt"
}

test_invalid_version_is_rejected() {
  local tmp
  tmp="$(mktemp -d)"
  tmp_dirs+=("$tmp")
  make_fixture "$tmp" 1 0.1.0

  if (cd "$tmp" && bash "$script" --check v0.1.0) >/tmp/easyreader-invalid-version.out 2>&1; then
    fail "invalid version v0.1.0 was accepted"
  fi

  local output
  output="$(cat /tmp/easyreader-invalid-version.out)"
  assert_contains "$output" "版本号必须形如 0.1.0"
}

test_check_mode_allows_current_version_for_first_release
test_future_version_increments_version_code
test_invalid_version_is_rejected
test_dirty_worktree_is_rejected_for_real_release
test_short_remote_flag_publishes_first_release_without_local_gradle

echo "EasyReaderAPK tests passed"
