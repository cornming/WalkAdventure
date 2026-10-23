#!/usr/bin/env bash
# 依「上一個版本標籤之後的提交訊息」計算下一個 versionName（語意化版本）。
#
# 規則（取最高的一項）：
#   - 提交類型帶 !（例如 feat!: 或 fix(scope)!:），或內文有 BREAKING CHANGE  → 主版號 +1，次版號與修訂號歸零
#   - 類型為 feat                                                            → 次版號 +1，修訂號歸零
#   - 其他                                                                    → 修訂號 +1
#
# 特例：
#   - repo 還沒有任何 vX.Y.Z 標籤時，第一版固定為 0.1.0
#   - 上一個標籤之後沒有新提交時，回傳該標籤的版本（不遞增）
#
# 用法：
#   next-version.sh            輸出下一個版本號（例如 0.2.0，不含 v）
#   next-version.sh --prev-tag 輸出上一個版本標籤（例如 v0.1.0；沒有則輸出空字串）
set -euo pipefail

TAG_RE='^v[0-9]+\.[0-9]+\.[0-9]+$'

prev_tag() {
  git tag --list 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | grep -E "$TAG_RE" | head -n1 || true
}

PREV="$(prev_tag)"

if [ "${1:-}" = "--prev-tag" ]; then
  printf '%s\n' "$PREV"
  exit 0
fi

if [ -z "$PREV" ]; then
  echo "0.1.0"
  exit 0
fi

IFS=. read -r MAJOR MINOR PATCH <<< "${PREV#v}"

COMMITS="$(git rev-list --no-merges "${PREV}..HEAD")"
if [ -z "$COMMITS" ]; then
  echo "${MAJOR}.${MINOR}.${PATCH}"
  exit 0
fi

LEVEL=patch
# 正規表示式放進變數，避免 [[ =~ ]] 對括號的解析問題
BANG_RE='^[A-Za-z]+(\([^)]*\))?!:[[:space:]]'
FEAT_RE='^feat(\([^)]*\))?:[[:space:]]'

for sha in $COMMITS; do
  subject="$(git log -1 --format=%s "$sha")"
  body="$(git log -1 --format=%b "$sha")"

  # 類型帶 ! → 破壞性變更
  if [[ "$subject" =~ $BANG_RE ]]; then
    LEVEL=major
    break
  fi
  # 內文有 BREAKING CHANGE（Conventional Commits 也接受 BREAKING-CHANGE）
  if printf '%s\n' "$body" | grep -qE '^BREAKING[ -]CHANGE'; then
    LEVEL=major
    break
  fi
  # feat → 次版號
  if [[ "$subject" =~ $FEAT_RE ]]; then
    LEVEL=minor
  fi
done

case "$LEVEL" in
  major) echo "$((MAJOR + 1)).0.0" ;;
  minor) echo "${MAJOR}.$((MINOR + 1)).0" ;;
  *)     echo "${MAJOR}.${MINOR}.$((PATCH + 1))" ;;
esac
