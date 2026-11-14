# FMusic

Minecraft 1.7.10 (Forge) 音乐模组

在单模组内同时集成原 AllMusic 的 **客户端**（HUD 显示、OpenAL 流式播放、mp3/flac/ogg/m4a 解码）与
**服务端**（点歌/歌单/切歌投票/插歌/歌词/KTV/空闲歌单/音乐 API 插件系统）
常维护在1.7.10,如果有问题请发issues

## 功能

- **点歌系统**: `/music` 指令 (搜索/点歌/选歌/列表/取消/投票切歌/插歌)
- **指令帮助**: `/music help [子指令...]` 多级帮助 (用法 + 说明 + 子指令), 每行都可点击把指令填入聊天框
- **输入即提示**: 打开聊天框输入 `/music ...` 时, 输入框上方实时显示当前子指令的用法与候选
- **消息 i18n**: 玩家可见文本全部在 `assets/fmusic/lang/{zh_CN,en_US}.lang`, 服务端只发 i18n key + 参数, 各客户端按自己的语言渲染 (不再有 message.json; 改文案可用资源包覆盖 lang)
- **HUD 显示**: 歌曲信息、滚动歌词、翻译歌词、KTV 逐字高亮、旋转唱片封面、播放进度条
- **音频播放**: 服务端推送播放地址, 客户端直接流式拉取播放 (HTTP Range 断点续传)
- **格式支持**: MP3 / FLAC / OGG / M4A (AAC)
- **服务端管理**: 空闲歌单、禁歌/禁人、静音、配置热重载、经济系统接口
- **直接播放**: `/music test <音频URL>` 可测试播放任意直链 (mp3/flac/ogg/m4a)
- **暂停跟随**: `/fmusic pause_at_freeze true` (单人游戏) 让按 Esc 暂停时音乐同步暂停
- **内置音乐 API**: 网易云 (netapi) + QQ音乐 (qqmusic) + 酷狗 (kugou), 配置 `defaultApi` 切换; 也支持 api/ 目录加载外部 jar
- **B站直播弹幕点歌**: 监听直播间弹幕 "点歌XXX" 自动搜索入队 (fmusic_server/bili/config.json 配置)
- **Forge 事件**: MusicPlayEvent / MusicAddEvent (可取消)
- **可点击消息按钮**: 聊天中 [点我选择]/[点我查看] 等按钮可点击执行命令 (1.7.10 兼容序列化)
- **内置客户端播放器 (移植自 DangoMusic)**: 按 `M` 打开液态玻璃播放器 (主菜单也能开), 直连网易云/QQ音乐/酷狗,
  游戏内扫码登录, 推荐/排行榜/我的歌单/搜索, 列表 ↔ 封面墙, 逐字 3D 世界歌词 (发光/抖动/文闪),
  Song card + 灵动岛, 环境音效与房间回声, 生物踩点, 云盘浏览, 可自定义快捷键
  - 播放栏上的**均衡器** (10 段, 11 个预设, 自动前级) 和**变速变调** (速度 0.5x~2x, 音调 ±12 半音,
    可选"音调跟着速度"), 都能按歌记住 (同一首歌下次自动沿用)
  - 设置 → **音乐与 HUD / 播放器 → 音质**可选标准（默认）、高音质（优先 320 kbps）和无损（FLAC），下首歌生效；
    网易云 / QQ音乐 / 酷狗按账号权限获取音频，不可用时回退并提示，播放栏显示实际音质。
    高音质和无损自动使用本地缓存（上限 1 GiB，读取或写入时清理 7 天未使用的条目，单曲最多 128 MiB）；
    设置 → **音乐与 HUD / 音频缓存**使用相对于游戏目录的路径，默认显示 `config/fmusic/dango/audio_cache`，点击输入框保留原路径；Ctrl+A 全选后可输入或粘贴替换，回车保存，留空或点击“恢复默认目录”恢复默认。
    原有绝对路径仍可使用，同盘路径自动转换为相对路径，跨盘路径保留绝对形式。
    同一面板显示当前目录的磁盘占用和缓存文件数，可点击“清空缓存”并再次确认删除；后台清理不会中断正在播放的歌曲。
    更换目录不会迁移或删除旧目录中的缓存，清空仅删除当前目录里可识别的 FMusic 缓存文件，不递归删除其它文件。
    缓存按音源、账号、歌曲和实际音质隔离，试听与替代版本不缓存，低音质回退结果不会冒充所选高音质。
    标准档不读写磁盘缓存；不影响独立的服务端点歌系统，也不会改变其它播放器的音质。
  - 中间左侧的**播放模式**三态按钮 (顺序 / 随机 / 单曲循环); 红心旁边的「＋」可以**把正在放的歌
    加到自己的歌单** (网易云 / QQ音乐 / 酷狗, 需登录); 红心按音源实现 (网易云、酷狗)
  - 设置里打开**显示其它播放器**后, FMusic 没在放歌时会显示 Windows 上别的 App (Spotify、Apple Music 等)
    正在放的歌和歌词, 播放栏的上一首/暂停/下一首/进度也跟着转给那个 App (仅 Windows 10+)
  - 歌曲列表里**右键任意一首歌** → 「发送到点歌系统」: 按「音源 + 歌曲 ID」把这一首直接加进**服务端的点歌队列**
    (和 `/music <音源> <歌曲ID>` 同一套权限 / 经济 / 禁歌 / 队列上限校验, 结果在播放栏提示);
    单机、局域网主机、装了本模组的服务器都能用, 右键其它地方仍然是返回上一页
  - 它和上面的服务端点歌系统是**两套完全独立**的播放系统 (各自的账号/歌单/播放链路), 同时只允许一个出声;
    上面那条右键点歌是两套系统之间唯一的桥 (只把歌**送过去**, 不接管服务端的播放)
  - HUD 三套并存 (老 HUD / Song card / 灵动岛), **默认只开老 HUD**, 新样式在播放器设置里自己开
  - 配置与登录凭证在 `config/fmusic/dango/` (均衡/变速的按歌记录在 `sound_tuning.json`); 详见 [docs/dango-port.md](docs/dango-port.md)

## 构建

```
gradlew.bat build
```

产物: `build/libs/FMusic-<version>.jar` (已包含 shadow 依赖并自动 relocate 到 `com.Lilith.FMusic.shadow`)。

环境要求: JDK 25 (项目 toolchain), Gradle 9.x (wrapper 自带)。

## 安装

1. 将 `FMusic-<version>.jar` 放入 `mods/` 目录
3. 服务端 (或单机) 首次启动自动生成 `fmusic_server/` 配置目录

## 运行时

- 服务端配置目录: `fmusic_server/` (config.json / ban.json / hud.json / music.json / players/) —— **单人游戏同样适用**: 客户端内嵌集成服务器,进入单机世界时也会读取该配置并运行完整音乐系统
- 客户端配置: `config/fmusic_client.json` (picSize 封面大小, queueSize 音频缓冲队列)
- 网络信道: `fmusic:channel` (仅服务端→客户端)
- 音乐 API: 内置 **netapi** (网易云音乐) / **qqmusic** (QQ音乐) / **kugou** (酷狗音乐),
  由 `fmusic_server/config.json` 的 `defaultApi` 决定使用哪个 (默认 netapi);
  自定义 API: 将实现 `IMusicApi` 的 jar 放入 `fmusic_server/api/` 后重载, 可覆盖内置
- 播放 VIP 歌曲: 浏览器登录 music.163.com 导出 cookie (JSON) 覆盖 `fmusic_server/cookie.json`, 执行 `/music reload`
- 各音源 cookie 独立: 网易云 `fmusic_server/cookie.json` / QQ音乐 `fmusic_server/QQMusic_cookie.json` /
  酷狗 `fmusic_server/Kugou_cookie.json` (后两者不存在时自动创建, `/music reload` 后生效);
  B站点歌 cookie 在 `fmusic_server/bili/cookie.json` (bili/config.json 的 cookie-file 指定)
- 酷狗点歌支持 32 位 hash、分享链接 (`https://www.kugou.com/share/xxx.html`) 与单曲页 (`/mixsong/xxx.html`);
  普通歌曲通过免签名移动端接口即可播放, VIP/付费歌曲仍需 `Kugou_cookie.json` 中的账号 Cookie
- B站点歌: 编辑 `fmusic_server/bili/config.json` 填 `room-id` (直播间号) 后执行 `/music reload`;
  `/bilimusic status` 查看连接状态, `/bilimusic request <歌名>` 手动点歌 (弹幕格式: 点歌<歌名>)

>
> `FMusic.cfg` (config/ 下) 可配置pause_at_freeze(是否在单人游戏未开启局域网联机的情况下进入Esc菜单时暂停音乐,默认false)
### 模组依赖
- [UniMixin](https://github.com/LegacyModdingMC/UniMixins/releases)(尽量下最新版吧?)
- [GTNHLib](https://github.com/GTNewHorizons/GTNHLib/releases)任意版本(别是1.0.0版本之类的就行)

## 对外 API

其他模组可以通过 `com.Lilith.FMusic.api.FMusicApi` 调用 FMusic:

- **点歌**: `addMusic(apiId, musicId, requester, callback)` (支持音源 ID / songmid / 分享链接, 异步回调)
- **搜索**: `search(apiId, keyword, callback)` / `searchSync(...)`
- **投票**: `startSwitchVote` / `startPushVote` / `agreeVote` / `cancelVote` / `getCurrentVote`
- **播放控制**: `skip` / `stopAll` / `clearQueue` / `getQueueList` / `removeFromQueue` / `resyncPlayer`
- **状态与信息**: `isEnabled` / `getApiIds` / `isPlaying` / `getNowPlaying`
- **直链即时播放**: `playUrl(player, url)` / `playUrlAll(url)`
- **消息与 HUD**: `sendMessage` / `broadcast` / `getHud` / `setHud`

完整文档(调用步骤/流程/示例/返回码): [docs/api.md](docs/api.md)

## 开发

- 架构说明与踩坑记录见 [docs/knowledge.md](docs/knowledge.md)
- 冒烟测试: `gradlew runServer25` (需 `run/server/eula.txt` 含 `eula=true`; **不会自动退出**, 需手动终止)
- Mixin: 早期 mixin 在 `com.Lilith.FMusic.mixins.early` (配置 `mixins.FMusic.json`),
  晚期 mixin 经 `LateMixinPlugin` 加载 (`mixins.FMusic.late.json`)

## 许可

本模组以 **GNU Affero General Public License v3.0 (AGPL-3.0)** 发布, 详见 [LICENSE](LICENSE)。

### 代码来源与致谢

| 部分 | 来源 | 许可证 |
|---|---|---|
| 主体移植 | [AllMusic](https://github.com/Coloryr/AllMusic) (Coloryr) | GPL-3.0 (与本项目兼容) |
| 内置音乐 API | [netapi](https://github.com/Coloryr/netapi) (Coloryr) | AGPL-3.0 (许可全文见 [LICENSE.netapi](src/main/resources/LICENSE.netapi)) |
| MP3 解码器 | javalayer (JavaZOOM) | LGPL |
| OGG 解码器 | jcraft jogg/jorbis | LGPL |
| FLAC 解码器 | Project Nayuki | LGPL |
| M4A/AAC 解码器 | Jay / FAAD2 移植 | LGPL 等 |
| QQ音乐 API | [AllMusic_QQMusic](https://github.com/hutuyee/AllMusic_QQMusic) (ds.haaa) | GPL-3.0 (同 AllMusic) |
| 酷狗音乐 API | [AllMusic_Kugou](https://github.com/hutuyee/AllMusic_Kugou) (ds.haaa) | GPL-3.0 (同 AllMusic) |
| B站直播弹幕点歌 | [BiliMusicBridge](https://github.com/hutuyee/BiliMusicBridge) | MIT |
| 内置客户端播放器 (液态玻璃播放器/3D 歌词/Song card/灵动岛/扫码登录/环境音效等) | [DangoMusic](https://github.com/Sorihiko/DangoMusic) (Sorihiko) | GPL-3.0 (与本项目兼容) |

按 AGPL-3.0 条款, 对本项目的使用、修改与分发需保持本许可证并保留上述版权声明;
