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
- **内置音乐 API**: 网易云音乐 (netapi) 默认启用, 无需额外插件; 也支持 api/ 目录加载外部 jar
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
- 音乐 API: 内置 **netapi** (网易云音乐, id="netapi", 默认使用);
  自定义 API: 将实现 `IMusicApi` 的 jar 放入 `fmusic_server/api/` 后重载, 可覆盖内置
- 播放 VIP 歌曲: 浏览器登录 music.163.com 导出 cookie (JSON) 覆盖 `fmusic_server/cookie.json`, 执行 `/music reload`

>
> `FMusic.cfg` (config/ 下) 仅含固定提示: server config is on ../fmusic_server

## 开发

- 架构说明与踩坑记录见 [docs/knowledge.md](docs/knowledge.md)
- 冒烟测试: `gradlew runServer25` (需 `run/server/eula.txt` 含 `eula=true`; **不会自动退出**, 需手动终止)
- Mixin: 早期 mixin 在 `com.Lilith.FMusic.mixins.early` (配置 `mixins.FMusic.json`),
  晚期 mixin 经 `LateMixinPlugin` 加载 (`mixins.FMusic.late.json`)

## 许可

本模组基于 [AllMusic](https://github.com/Coloryr/AllMusic) 移植, 遵循其开源许可;
内置解码器分别来自 javalayer / jcraft / Project Nayuki / Jay 等项目, 各自遵循 LGPL 等许可。
