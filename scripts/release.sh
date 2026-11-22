#!/usr/bin/env bash
# 本地打 tag 并推送，触发 GitHub Actions 构建并发 Release（APK 作为 release asset）。
#
# 用法：
#   ./scripts/release.sh 0.2.0        # 等价于打 v0.2.0
#   ./scripts/release.sh v0.2.0       # 也接受带 v 的写法
#
# 前置：
#   - 工作区干净（先 commit）
#   - 本机有 JDK 17 / Android SDK（默认在 ~/android-tools 下，可用环境变量覆盖）
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "用法: $0 <版本号，例如 0.2.0>" >&2
  exit 1
fi

TAG="v${VERSION#v}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "工作区有未提交改动，请先提交：" >&2
  git status --short >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "标签 $TAG 已存在" >&2
  exit 1
fi

echo "==> 本地构建校验（$TAG）"
(
  cd android
  JAVA_HOME="${JAVA_HOME:-$HOME/android-tools/jdk-17}" \
  ANDROID_HOME="${ANDROID_HOME:-$HOME/android-tools/android-sdk}" \
    ./gradlew assembleDebug --no-daemon -q -PappVersionName="${TAG#v}"
)

echo "==> 打标签 $TAG 并推送"
git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG"

REMOTE_URL="$(git remote get-url origin)"
SLUG="$(printf '%s' "$REMOTE_URL" | sed -E 's#(git@|https://)github\.com[:/]##; s#\.git$##')"

echo
echo "已推送 $TAG。GitHub Actions 会自动构建并发布 Release："
echo "  https://github.com/${SLUG}/actions"
echo "发布页："
echo "  https://github.com/${SLUG}/releases"
