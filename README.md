# FMusic

Minecraft 1.7.10 (Forge) 音乐模组

在单模组内同时集成原 AllMusic 的 **客户端**（HUD 显示、OpenAL 流式播放、mp3/flac/ogg/m4a 解码）与
**服务端**（点歌/歌单/切歌投票/插歌/歌词/KTV/空闲歌单/音乐 API 插件系统）
常维护在1.7.10,如果有问题请发issues

## 功能

- **点歌系统**: `/music` 指令 (搜索/点歌/选歌/列表/取消/投票切歌/插歌)
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

- 服务端配置目录: `fmusic_server/` (config.json / message.json / ban.json / hud.json / music.json / players/) —— **单人游戏同样适用**: 客户端内嵌集成服务器,进入单机世界时也会读取该配置并运行完整音乐系统
- 客户端配置: `config/fmusic_client.json` (picSize 封面大小, queueSize 音频缓冲队列)
- 网络信道: `fmusic:channel` (仅服务端→客户端)
- 音乐 API: 内置 **netapi** (网易云音乐) / **qqmusic** (QQ音乐) / **kugou** (酷狗音乐),
  由 `fmusic_server/config.json` 的 `defaultApi` 决定使用哪个 (默认 netapi);
  自定义 API: 将实现 `IMusicApi` 的 jar 放入 `fmusic_server/api/` 后重载, 可覆盖内置
- 播放 VIP 歌曲: 浏览器登录 music.163.com 导出 cookie (JSON) 覆盖 `fmusic_server/cookie.json`, 执行 `/music reload`
- 各音源 cookie 独立: 网易云 `fmusic_server/cookie.json` / QQ音乐 `fmusic_server/QQMusic_cookie.json` /
  酷狗 `fmusic_server/Kugou_cookie.json` (后两者不存在时自动创建, `/music reload` 后生效);
  B站点歌 cookie 在 `fmusic_server/bili/cookie.json` (bili/config.json 的 cookie-file 指定)
- B站点歌: 编辑 `fmusic_server/bili/config.json` 填 `room-id` (直播间号) 后执行 `/music reload`;
  `/bilimusic status` 查看连接状态, `/bilimusic request <歌名>` 手动点歌 (弹幕格式: 点歌<歌名>)

>
> `FMusic.cfg` (config/ 下) 可配置pause_at_freeze(是否在单人游戏未开启局域网联机的情况下进入Esc菜单时暂停音乐,默认false)
### 模组依赖
- [UniMixin](https://github.com/LegacyModdingMC/UniMixins/releases)(尽量下最新版吧?)
- [GTNHLib](https://github.com/GTNewHorizons/GTNHLib/releases)任意版本(别是1.0.0版本之类的就行)

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
| QQ音乐 API | [AllMusic_QQMusic](https://github.com/haaaa/tmp) (ds.haaa) | GPL-3.0 (同 AllMusic) |
| 酷狗音乐 API | [AllMusic_Kugou](https://github.com/haaaa/tmp) (ds.haaa) | GPL-3.0 (同 AllMusic) |
| B站直播弹幕点歌 | [BiliMusicBridge](https://github.com/haaaa/tmp) | MIT |

按 AGPL-3.0 条款, 对本项目的使用、修改与分发需保持本许可证并保留上述版权声明;
