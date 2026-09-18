#!/bin/bash
# 发布 v1.0.1 到 GitHub Releases: 建仓库 release + 上传绿色版 zip (带重试)
set -u
cd "D:/desktop/MJ-DingTalk"
REPO="Lxithral/Tiktok-MJ-for-DingTalk"
PROXY="--socks5-hostname 127.0.0.1:10808"
ZIP="dist/MJDingTalk-green-v1.0.1.zip"
ASSET_NAME="MJDingTalk-green-v1.0.1.zip"

TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | GCM_INTERACTIVE=never git credential fill 2>/dev/null | grep "^password=" | cut -d= -f2-)
[ -n "$TOKEN" ] || { echo "FATAL: no token"; exit 1; }

cat > /tmp/rel_v101.json <<'EOF'
{
  "tag_name": "v1.0.1",
  "target_commitish": "main",
  "name": "钉钉 MJ 彩蛋 v1.0.1 (绿色版)",
  "body": "## 下载\n\n下载下方 `MJDingTalk-green-v1.0.1.zip`，解压后双击 `MJDingTalk.exe` 即用，无需安装 Python 或任何依赖。\n\n## v1.0.1 新增\n\n- **开机自启动**：托盘菜单「开机自启」开关，勾选后随系统启动（写入当前用户注册表，无需管理员权限）\n- **启动 splash**：打开程序时屏幕正中央渐显应用图标与版本号，约 1.5 秒淡出\n\n## 功能\n\n- 在钉钉 PC 客户端发送 `mj` / `mjmj` / `MJ` / `MjMj`（偶数长度且每两位一组均为 mj）时，全屏播放带透明通道的蜘蛛侠动画（坠落贴右上角 / 荡绳贴左上角，自动交替），原版音效同步播放\n- 动画窗口点击穿透、不抢焦点；只触发**自己发送**的消息；中文输入法上屏回车不会误触发\n- 托盘常驻：启用开关 / 播放测试 / 开机自启 / 关于（深浅色跟随系统）/ 退出\n\n## 使用要求\n\n- Windows 10/11 x64，装有钉钉 PC 客户端（DingTalk.exe）并登录\n\n## 注意\n\n- 首次启动需解压，等 1~3 秒；无窗口弹出时看右下角托盘\n- 未签名 exe 可能被 SmartScreen/杀软提示，点「仍要运行」或添加信任\n- `config.json` 与日志生成在 exe 旁边，可记事本调整音量/动画大小/冷却时间\n\n## 致谢\n\n动画素材与玩法源自 [qiu7c/Tiktok-MJ-for-Wechat](https://github.com/qiu7c/Tiktok-MJ-for-Wechat)，素材版权归原作者所有，仅供学习交流。",
  "draft": false,
  "prerelease": false
}
EOF

echo "== 1. 创建 release v1.0.1 =="
CREATE=$(curl -s --max-time 60 -x http://127.0.0.1:10808 -X POST \
  "https://api.github.com/repos/$REPO/releases" \
  -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json; charset=utf-8" --data-binary @/tmp/rel_v101.json)
REL_ID=$(echo "$CREATE" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('id',''))")
if [ -z "$REL_ID" ]; then
  # 可能 release 已存在, 尝试按 tag 找
  echo "创建失败/已存在, 尝试获取已有 release..."
  REL_ID=$(curl -s --max-time 30 -x http://127.0.0.1:10808 \
    "https://api.github.com/repos/$REPO/releases/tags/v1.0.1" \
    -H "Authorization: token $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin).get('id',''))")
fi
[ -n "$REL_ID" ] || { echo "FATAL: 拿不到 release id"; echo "$CREATE" | head -c 300; exit 1; }
echo "release id = $REL_ID"

echo "== 2. 上传 $ASSET_NAME =="
UPLOADED=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  echo "-- 尝试 $i $(date +%H:%M:%S) --"
  curl -s --max-time 900 --connect-timeout 30 $PROXY -X POST \
    "https://uploads.github.com/repos/$REPO/releases/$REL_ID/assets?name=$ASSET_NAME" \
    -H "Authorization: token $TOKEN" -H "Content-Type: application/zip" -H "Expect:" \
    --data-binary "@$ZIP" -o /tmp/up_v101.json -w "http=%{http_code} sent=%{size_upload}B speed=%{speed_upload}B/s\n"
  STATE=$(python -c "import json; print(json.load(open('/tmp/up_v101.json', encoding='utf-8')).get('state',''))" 2>/dev/null)
  if [ "$STATE" = "uploaded" ]; then UPLOADED=1; echo "UPLOAD_OK"; break; fi
  echo "state=$STATE, 20s 后重试"; sleep 20
done

echo "== 3. 验证 =="
curl -s --max-time 30 -x http://127.0.0.1:10808 \
  "https://api.github.com/repos/$REPO/releases/$REL_ID/assets" \
  -H "Authorization: token $TOKEN" | python -c "
import json, sys
for a in json.load(sys.stdin):
    print('asset:', a['name'], a['size'], 'bytes, state:', a['state'])
    print('download:', a['browser_download_url'])
"
[ "$UPLOADED" = "1" ] && echo "ALL_DONE" || echo "UPLOAD_FAILED_AFTER_RETRIES"
