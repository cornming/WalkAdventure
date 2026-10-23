#!/usr/bin/env bash
# 把「上一個版本標籤之後的提交」依 feat／fix／其他分組，整理成 Release 說明（Markdown，輸出到 stdout）。
#
# 用法：release-notes.sh <版本號，不含 v> [上一個標籤]
#   上一個標籤省略時，會自動用 next-version.sh --prev-tag 取得；沒有標籤則列出全部提交。
set -euo pipefail

VERSION="${1:?用法：release-notes.sh <版本號> [上一個標籤]}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREV="${2-$("$SCRIPT_DIR/next-version.sh" --prev-tag)}"
APK="WalkAdventure-v${VERSION}.apk"

if [ -n "$PREV" ]; then
  RANGE="${PREV}..HEAD"
else
  RANGE="HEAD"
fi

# 正規表示式放進變數，避免 [[ =~ ]] 對括號的解析問題
HEADER_RE='^([A-Za-z]+)(\(([^)]*)\))?(!)?:[[:space:]]+(.*)$'

BREAKING=""
FEATS=""
FIXES=""
OTHERS=""

for sha in $(git rev-list --no-merges "$RANGE"); do
  subject="$(git log -1 --format=%s "$sha")"
  body="$(git log -1 --format=%b "$sha")"
  short="$(git rev-parse --short "$sha")"

  type=""
  desc="$subject"
  scope=""
  bang=""
  if [[ "$subject" =~ $HEADER_RE ]]; then
    type="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
    scope="${BASH_REMATCH[3]}"
    bang="${BASH_REMATCH[4]}"
    desc="${BASH_REMATCH[5]}"
  fi

  line="${desc}"
  [ -n "$scope" ] && line="**${scope}**：${desc}"
  line="${line} (\`${short}\`)"

  if [ -n "$bang" ] || printf '%s\n' "$body" | grep -qE '^BREAKING[ -]CHANGE'; then
    BREAKING+="- ${line}"$'\n'
  fi

  case "$type" in
    feat) FEATS+="- ${line}"$'\n' ;;
    fix)  FIXES+="- ${line}"$'\n' ;;
    *)    OTHERS+="- ${line}"$'\n' ;;
  esac
done

cat <<EOF
## 走走冒險 v${VERSION}

### 安裝方式
1. 在 Android 手機下載下方附件 \`${APK}\`。
2. 依系統提示允許瀏覽器（或檔案管理員）「安裝未知應用程式」。
3. 開啟 APK 完成安裝，第一次執行時允許「活動辨識」權限（計步需要）。

EOF

if [ -n "$PREV" ]; then
  cat <<EOF
**可直接覆蓋安裝**：本版與 ${PREV} 使用同一把固定的 debug 簽章金鑰，直接安裝即可覆蓋，不需解除安裝，本機存檔會保留。

EOF
else
  cat <<EOF
**首次發佈（固定簽章）**：從本版起，每一版都使用同一把固定的 debug 簽章金鑰，之後的新版可直接覆蓋安裝。
本版之前的舊版 APK 簽章不同，**第一次換成本版需先解除安裝舊版，本機存檔會消失**。

EOF
fi

if [ -n "$PREV" ]; then
  echo "### 更新內容（自 ${PREV} 起）"
else
  echo "### 更新內容"
fi
echo

if [ -n "$BREAKING" ]; then
  printf '#### 破壞性變更\n%s\n' "$BREAKING"
fi
if [ -n "$FEATS" ]; then
  printf '#### 新功能（feat）\n%s\n' "$FEATS"
fi
if [ -n "$FIXES" ]; then
  printf '#### 修正（fix）\n%s\n' "$FIXES"
fi
if [ -n "$OTHERS" ]; then
  printf '#### 其他\n%s\n' "$OTHERS"
fi
if [ -z "$BREAKING$FEATS$FIXES$OTHERS" ]; then
  echo "（沒有新的提交）"
fi
