# FMusic 项目知识库 (knowledge.md)

> 本文件记录 FMusic 模组 (Minecraft 1.7.10 Forge) 的代码架构、合并方式与关键机制,供后续开发参考。
> 项目来源: AllMusic (Coloryr) 的 forge_1_7_10 client+server,二合一后重打包为 com.Lilith.FMusic。

---

## 1. 项目概况

| 项目 | 值 |
|---|---|
| 游戏版本 | Minecraft 1.7.10 (Forge 10.13.4.1614) |
| 模组 | FMusic (modid = "FMusic"), 单 jar 同时含客户端+服务端逻辑 |
| 原始代码 | tmp/AllMusic-main (client/server/forge_1_7_10 平台代码 + client/src, server/src 共享核心 + codec) |
| 模板入口 | src/main/java/com/Lilith/FMusic/ (FMusic, CommonProxy, ClientProxy, Config, mixins/) |
| 内置音乐API | src/main/java/com/Lilith/FMusic/netapi/ (网易云音乐, 原 netapi-main 插件集成, 15 个类) |
| 构建 | GTNH convention (elytra-conventions + gtnhconvention), RFG 2.0.2, stable_12 映射 |
| 产物 | build/libs/FMusic-<version>.jar (shadowJar + reobf, 自动 relocate 依赖) |

## 2. 源码目录结构

```
src/main/java/com/Lilith/FMusic/
├── FMusic.java              # 主 @Mod, 生命周期事件转发给 proxy
├── CommonProxy.java         # 服务端逻辑接线 (持有 FMusicServer 实例)
├── ClientProxy.java         # 客户端逻辑接线 (持有 client.FMusic 实例)
├── Config.java              # 模板自带 Forge 配置(未使用)
├── mixins/                  # LateMixinPlugin(ILateMixinLoader) + Mixins 枚举 + TargetMod
│   └── early/               # 声音 Mixin: MixinSound, MixinLibSound (early 包,与配置 package 对应)
├── client/                  # 原 AllMusic 客户端 (平台 7 类 + core 315 类)
│   ├── FMusic.java          # 原 @Mod allmusic_client 入口 (已去 @Mod)
│   ├── CoreRenderTarget.java / PicRender.java / TexRender.java   # HUD 渲染桥实现
│   ├── IGetSound.java / IGetSoundHandler.java                    # 声音系统访问接口(mixin 实现)
│   └── core/                # FMusicCore, FMusicPlayer, FMusicHud, Point2f,
│                            #   objs/ (ConfigObj, PlayTaskObj),
│                            #   render/ (TextFrameBuffer, PictureFrameBuffer, TextureRender),
│                            #   player/decoder/ (mp3/flac/ogg/m4a 全套解码器, 共约 300 类)
├── server/                  # 原 AllMusicServer 服务端 (平台 8 类 + core 92 类)
│   ├── FMusicServer.java    # 原 @Mod allmusic_server 入口 (已去 @Mod)
│   ├── CommandForge.java    # /music 指令 (CommandBase)
│   ├── LogForge.java        # IAllMusicLogger 实现 (服务器聊天+控制台回退)
│   ├── SideForge.java       # BaseSide 实现 (Forge 侧操作/发包)
│   ├── TaskItem.java / Tasks.java  # 服务器主线程延迟任务队列
│   ├── event/ (MusicAddEvent, MusicPlayEvent)  # 可取消的 Forge 事件
│   └── core/                # FMusic(配置/启动/API), 指令系统, music/, saves/, side/, utils/, objs/
└── codec/                   # 通信协议 (12 类, 客户端服务端共用)
    ├── MusicPacketCodec.java # ByteBuf 编解码 (UTF-8 字符串/长整型)
    ├── MusicPack.java        # 包类型: LYRIC/LYRIC_KTV/INFO/PLAY/IMG/STOP/CLEAR/POS/HUD_DATA/TIME
    ├── CommandType.java      # 指令类型枚举 (与 MusicPack 对应)
    ├── KtvLyricObj.java      # KTV 逐字歌词
    └── HudPos*.java          # HUD 位置对象: HudPosObj(lyric/info/state/pic) + HudPosType(9 方向)
```

资源 (src/main/resources):
```
assets/fmusic/
├── decoder/                 # mp3 解码表 .ser (au2lin, l3reorder, lin2au, sfd) — 从类路径迁移至此
└── textures/hud/            # bg1/2/3.png, pg1/2/3.png, offset.txt(-1), pic.txt(0.83)
mixins.FMusic.json           # EARLY mixin 配置 (声音 mixin 在 client 段)
mixins.FMusic.late.json      # LATE mixin 配置 (LateMixinPlugin 加载, 当前为空)
icon.png, pack.mcmeta, mcmod.info, LICENSE
```

## 3. 生命周期与二合一接线

原架构是两个 @Mod (allmusic_client, allmusic_server) 两个 jar;合并后由 FMusic 主类统一驱动:

```
FMusic.preInit  → proxy.preInit
  CommonProxy.preInit : Config.sync + fMusicServer.commonSetup(event)
      # 注册 Forge/FML 总线、fmusic:channel 信道、AllMusic.log/side、new FMusic().init(fmusic_server/)
  ClientProxy.preInit : super + fMusicClient.preload(event)
      # 注册渲染/声音/数据包事件 + 再次注册 fmusic:channel(客户端接收用)
FMusic.init / postInit → proxy (空实现)
FMusic.serverStarting → CommonProxy: fMusicServer.onServerStarting(event)
      # 记录 MinecraftServer 实例 + 注册 /music 指令
FMusic.serverStarted  → fMusicServer.onServerStarted  → FMusic.start()
      # 启动 歌曲处理线程/歌曲播放线程/搜索线程/数据库线程 + 加载音乐 API
FMusic.serverStopping → fMusicServer.onServerStopping  → FMusic.stop()
FMusic.loadComplete   → ClientProxy: fMusicClient.test(event)
      # 从混入后的 SoundSystem 取 ALSource → FMusicCore.init(config, bridge, source) + renderInit()
```

要点:
- **信道**: 服务端 FMusicServer.channel 用于 sendTo 发包;客户端 preload 再次 newEventDrivenChannel("fmusic:channel") 用于收包。FML 按名字分发表,后者覆盖前者,两端各取所需,无需改动。
- **proxy 持有实例** (非静态调用): 原 @Mod.EventHandler 是实例方法且要 register(this),故 CommonProxy/ClientProxy 各持有一个 final 实例字段。
- **服务端代码在客户端同样运行** (集成服务器),与原版行为一致。**单人游戏实测通过** (runClient):
  - 客户端 preInit 即执行 commonSetup → 在客户端运行目录创建/读取 `fmusic_server/` (config.json/message.json/ban.json/hud.json/music.json/cookie.json/players/api)
  - 进入单机世界 → FMLServerStarting/Started 事件触发 → FMusic.start() (线程+音乐API)
  - /music 指令在单人游戏中可用, 退出时 serverStopping → FMusic.stop()
  - 客户端运行目录: run/client (gradle.properties runClientWorkingDirectory)

## 4. 通信协议 (codec)

- 信道: **fmusic:channel** (原 allmusic:channel,已改名,与原版 AllMusic 不互通)
- 编码: 1 字节 type 序号 + 各类型负载 (字符串= int 长度 + UTF-8;TIME= 2×long;POS= int;LYRIC= 3×字符串;LYRIC_KTV= 复杂结构)
- 方向: 仅服务端→客户端 (客户端无上行包)
- 客户端分发: FMusicCore.packRead → packDo 按 CommandType 处理 (PLAY 前先 stopPlayMusic 停 MC 原声)

## 5. 客户端核心

### FMusicCore (com.Lilith.FMusic.client.core)
- 静态桥 FMusicCore.bridge (AllMusicBridge 接口: 屏幕/字体/渲染层工厂/资源读取/kick)
- 配置: config/fmusic_client.json (picSize=120, queueSize=100, 首次运行生成)
- 4 线程 ScheduledExecutorService (FMusicCore.service): 图片旋转/歌词/循环/播放计时
- init(Path, bridge, IntBuffer source): 建 HttpClient + FMusicPlayer;renderInit(): 建 FMusicHud

### FMusicPlayer (OpenAL 流式播放)

**已修复的播放问题 (诊断记录):**

| 症状 | 根因 | 修复 |
|---|---|---|
| 播放到一半中断 | 服务器断流 read 返回 -1 被当 EOF; 原代码不重连且 local += -1 | read 系列: -1 且 local < Content-Length → Range 断点重连 (限 5 次); temp<0 不加 local |
| 播放中断 | 单帧解码异常直接 break 整个播放 | 解码异常容错: 连续 3 次失败才放弃, 单次失败跳过继续 |
| 播放中断/卡顿 | 单线程 下载=解码=播放, 网络慢时队列耗尽 (underrun) | 设计限制; queueSize (默认100帧≈2.6s) 可调大 |
| 播放不全 | OGG 结尾: read 返回 -1 但代码只认 bytes==0; jorbis wrote(-1) 污染 fill | OggDecoder: bytes<0 → 0 且主循环 eos=1 |
| 播放不全 | FLAC 结尾返回 len=0 空帧 (应 null) | FlacDecoder: blockSamples==0 → null |
| 播放不全 | 自然播完后线程空转不退出 (isPlay 恒 true, 等 STOP 兜底) | 播放循环加 eof 标志: 解码完毕且 AL_STOPPED → 自然结束 |
| 播放失败 | MP3 只认 0xFFFB (128kbps), 其他码率误判 OGG | 0xFF 且 (b1&0xE0)==0xE0 → MP3 |
| 计时不准 | 服务端固定 10ms 步进, 与客户端实际播放(缓冲)脱节; 客户端开播延迟 ~0.5-2s | 设计限制: 服务端可调 fixSongTime; 无法感知客户端缓冲 |

**剩余已知限制 (未改):**
- HUD 时间来自服务端 TIME 包 (每 sendDelay=1000ms 一次), 进度条 1 秒一跳
- 服务端 musicLessTime 按 length+fixSongTime 倒计时, 网络慢时客户端滞后 → 服务端先 STOP 切尾 (调大 fixSongTime 缓解)
- MP3 seek 精度 = 帧对齐 (time/26*framesize)
- 从 Minecraft SoundSystem 的 streaming 通道借用一个 AL 音频源 (ALSource)
- HTTP Range 断点续传 (FMusicPlayer 继承 InputStream, decoder 从它读)
- 格式识别: M4A(0x0000001c) / MP3(ID3 或 0xFFFB) / 其余按 OGG
- 缓冲队列: 填满 config.queueSize 个 AL buffer, 边放边回收; 音量跟随游戏"唱片机"音量

### FMusicHud (HUD 渲染)
- 组件: 图片(picRender)/状态(stateRender 时间+进度条)/信息(infoRender)/歌词(lyricRender+译文+ktv)
- 图片线程 allmusic_pic 下载+圆形裁切 (Ellipse2D clip, 比例 pic.txt=0.83), 旋转模式每 pic.speed tick 转 1°
- KTV 歌词: 按字符时间轴计算 lyricState (0~1), drawWithState 用 scissor 裁剪实现逐字高亮
- 文本滚动: 超过 maxWidth 时按 lcm 循环滚动 (TextFrameBuffer.tick)
- 位置: HudPosType 9 方向 + x/y 偏移 (getPos), 由服务端 HUD_DATA 包下发 HudPosObj JSON

### TexRender 纹理绘制修复 (u1 平铺 bug)

- 现象: 游戏内唱片/底衬纹理"爆了" (平铺成无数个小格子)
- 根因: forge_1_7_10 的 `drawPic(x, y, width, height, dir, alpha)` (bg1/bg3 使用) 中
  `u1 = width` → UV 0..pic.size(默认70) → GL_REPEAT 下纹理平铺 ~70 次
- 证据: 跨版本对比 — fabric_1_16_5+ / forge_1_20_1 / neoforge 全部是 `u1 = 1`;
  仅 forge_1_7_10 / 1_12_2 / 1_16_5 老版为 `u1 = width` (bug)
- 修复: 该重载 u1 改为 1 (完整纹理); 4 参进度条重载 `drawPic(x, y, width, alpha)`
  的 `u1 = width` 保留 (pg2 按比例显示部分纹理, 是正确行为)

### 解码器 (player/decoder)
- mp3: javalayer 移植 (SynthesisFilter 需 sfd.ser 等 4 个 .ser 解码表)
- ogg: jcraft jogg/jorbis 移植
- m4a: AAC (Jay 移植) + MP4 container 解析
- flac: Project Nayuki 移植
- 全部为内嵌代码,无外部依赖; .ser 资源现从 assets/fmusic/decoder 加载
  (JavaLayerUtils.getResourceAsStream: 先类路径, 再 Minecraft 资源包回退)

## 6. 服务端核心

### FMusic (com.Lilith.FMusic.server.core.FMusic) — 静态单例式
- 常量: SERVER_DIR="fmusic_server/", channel="fmusic:channel", channelBC="fmusic:channelbc", version="4.0.0", configVersion="401", messageVersion="400"
- 配置文件: config.json / message.json / cookie.json / ban.json / hud.json / music.json / players/*.json / api/(API jar 目录)
- init(File): 建目录→loadConfig→BanSave/HudSave/MusicListSave 加载→MusicApiLoader 扫 api/ 目录 jar
- start(): MusicHttpClient.init + PlayMusic/PlayRuntime/MusicSearch/SaveTask 线程启动 + MUSIC_APIS 注册
- stop(): PlayRuntime/SaveTask 停止 + side.sendStop() 广播停止包
- joinPlay(player): 玩家加入延迟 joinDelay 后补发当前歌曲/图片/进度 (isSkip 检查各种禁用)

### 指令系统 (core/command)
- CommandEX 静态注册表: commandList(普通 14 个: stop/help/list/vote/mute/search/searchapi/select/nextpage/lastpage/hud/push/join/cancel) + commandAdminList(管理员 12 个: reload/next/ban/unban/banplayer/unbanplayer/delete/addlist/clearlist/clearban/clearbanplayer/test)
- CommandForge (/music) 转发: CommandEX.execute(sender, name, args) + tab 补全
- **/music test 修复**: 原版对非整数 ID 静默无反应 (checkId 失败直接跳过);
  现支持 http/https 链接直接播放 (sendMusic(name, url) → PLAY 包), 非法 ID 给出明确反馈
- 权限: needPermission 配置开关 → side.checkPermission / adminList; 经济: IEconomy 接口 + cost 配置

### 播放逻辑 (core/music)
- PlayMusic: 播放列表(ArrayList)/点歌任务队列(ConcurrentLinkedQueue)/空闲歌单(deep 去重)/切歌投票(vote)/插歌投票(push)
- PlayRuntime: musicPlayTask 线程轮询列表→取歌→getPlayUrl→sendMusic 广播→musicLessTime 倒计时循环; time1 每 10ms 推进 musicNowTime + 歌词触发; time3 每秒处理投票/插歌/ping
- MusicSearch: 搜索线程, SearchPageObj 分页(每页 10), 结果按玩家名缓存
- MusicHttpClient: httpclient5, cookie 持久化到 cookie.json, 5s/7s 超时
- LyricSave: 时间戳→歌词行映射 (Map<Long, LyricItemObj>), KTV 逐字 (Map<Long, KtvLyricObj>)

### 侧实现 (core/side)
- BaseSide 抽象: runTask/延迟任务/权限/玩家集合/发包(send* 全家桶)/MiniMessage 消息/事件钩子(onMusicPlay/onMusicAdd)
- SideForge 实现: Tasks 延迟队列, FMLProxyPacket 发包, MinecraftForge.EVENT_BUS 事件
- IFMusicLogger: 日志接口 (LogForge 实现: 服务器在线时 addChatMessage, 否则控制台)

## 7. Mixin 机制 (UniMixins)

- **运行时依赖**: unimixins 0.3.1 (dev 环境经 gtnhgradle 自动加入 classpath; 生产环境需装 UniMixins 模组)
- **EARLY 注册**: jar manifest 属性 `TweakClass: org.spongepowered.asm.launch.MixinTweaker` + `MixinConfigs: mixins.FMusic.json` (gtnhgradle ToolchainModule 自动写入) → MixinTweaker 在 Minecraft 构造前注册配置 → 类加载时应用
- **LATE 注册**: LateMixinPlugin implements com.gtnewhorizon.gtnhmixins.ILateMixinLoader, @LateMixin 注解 → GTNHMixinsCore 在 LoaderState.CONSTRUCTING 发现并加载 (mixins.FMusic.late.json)
- **声音 Mixin (mixins/early/):**
  - MixinSound → @Mixin(paulscode.sound.SoundSystem): @Inject <init> RETURN 时记录 FMusic.sound 实例; @Shadow soundLibrary
  - MixinLibSound → @Mixin(paulscode.sound.Library): @Shadow streamingChannels/normalChannels, 实现 IGetSound
  - 目标类 paulscode 未被混淆 (Mojang 排除表), 无需 refmap 条目
  - **必须加 @Mixin(value = X, remap = false)**: 否则 AP 会因 paulscode 不在 MCP 映射中
    产生 "Unable to locate obfuscation mapping for @Shadow/@Inject" 警告。
    源码依据 (unimixins-src/org/spongepowered/tools/obfuscation/):
      AnnotatedMixin.java:    remap = annotation.getBoolean("remap", true) && targets.size() > 0
      AnnotatedMixins.java:   shouldRemap = annotation.getBoolean("remap", mixinClass.remap())
      AnnotatedMixinElementHandlerShadow.java:  if (!elem.shouldRemap()) return;  ← 跳过映射查找
      AnnotatedMixinElementHandlerInjector.java: if (... && elem.shouldRemap())  ← 跳过映射查找
    类级 remap=false 会跳过整个 mixin 的混淆映射处理(除非成员显式 remap=true), 运行行为不变
  - **必须 early**: SoundSystem 在 Minecraft 构造器(模组加载前)就创建,晚注册会漏掉注入
- **类名解析坑**: 本 fork 的 Mixin 会把配置列表中的名字无条件拼接到 "package" 前缀后, **不支持全限定名**!
  曾用 "com.Lilith.FMusic.client.mixins.MixinLibSound" 导致
  ClassNotFoundException: 'com.Lilith.FMusic.mixins.early.com.Lilith.FMusic.client.mixins.MixinLibSound'。
  正确做法: mixin 类必须放在配置 package (com.Lilith.FMusic.mixins.early) 下, 配置里写简单类名。
- 配置: mixins.FMusic.json 的 client 段列出两个声音 mixin (简单名), package = com.Lilith.FMusic.mixins.early
- refmap: mixins.FMusic.refmap.json (MCP→SRG, 由 mixin AP 生成)

## 8. HUD 贴图构造 (assets/fmusic/textures/hud)

像素分析结论 (238×238/192×192/128×10):

| 文件 | 尺寸 | 内容 | 用途 |
|---|---|---|---|
| bg1.png | 238×238 | 棕色唱片框(全幅) | 旋转模式下图片底衬 (drawPic size×size) |
| bg2.png | 192×192 | 深色圆形黑胶(内容 x16..175 圆形) | 封面圆形裁切后叠加的黑胶纹理 (pic.txt=0.83 → 裁切椭圆) |
| bg3.png | 238×238 | 顶部右侧小块灰白高光 (x175..208, y2..120) | 图片前景高光/反光 |
| pg1.png | 128×10 | 灰色进度条底 (R184) | 进度条背景 |
| pg2.png | 128×10 | 白色进度条填充 (R236) | 进度条已播放部分 (drawPic width=now/all) |
| pg3.png | 4×10 | 青色滑块 (R175 G244 B244) | 进度条当前位置滑块 |
| offset.txt | — | "-1" | 进度条 Y 偏移 (pgOffset) |
| pic.txt | — | "0.83" | 封面圆形裁切比例 (border = size×(1-0.83)) |

绘制顺序 (FMusicHud.update, rotate 模式): bg1 底衬 → picRender.draw(旋转 ang) → bg3 前景。
进度条布局: [总时长文本][pg1+pg2+pg3][当前时间文本], x = state.x + gap + 时间文本宽, y = state.y + pgOffset。

## 9. 构建与依赖

```kotlin
dependencies {
    shadowImplementation("org.apache.httpcomponents.client5:httpclient5:5.6.1")
    shadowImplementation("org.apache.httpcomponents.core5:httpcore5:5.4.2")
    shadowImplementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.2")
    shadowImplementation("net.kyori:adventure-text-minimessage:4.26.1")
    shadowImplementation("net.kyori:adventure-api:4.26.1")
    shadowImplementation("net.kyori:adventure-text-serializer-gson:4.8.1")
    shadowImplementation("net.kyori:adventure-text-serializer-legacy:4.8.1")
    shadowImplementation("net.kyori:adventure-text-serializer-plain:4.8.1")
    shadowImplementation("net.kyori:adventure-key:4.8.1")
}
```
- gradle.properties: usesShadowedDependencies=true, disableSpotless=true, disableCheckstyle=true (合并第三方代码需要)
- shadowJar 自动 relocate 到 com.Lilith.FMusic.shadow.* (minimize 默认开启)
- gson/netty/paulscode/lwjgl 由 MC 提供, 无需依赖
- Java 8 兼容注意: ScheduledExecutorService 无 close() (原代码 service.close() 已改为 shutdown())

## 10. 运行时行为与测试

- 配置目录: `fmusic_server/` (config.json, message.json, cookie.json, ban.json, hud.json, music.json, players/, api/)
- 首次启动自动生成默认配置 (check() 校验失败会覆盖)
- 线程: fmusic_task(歌曲处理) / fMusic_play(播放) / fmusic_search(搜索) / 数据库线程(SaveTask) / allmusic_pic(图片) / fmusic_run(OpenAL 播放) / fmusic_ogg
- 冒烟测试: `gradlew runServer` **不会自动退出**,需手动 kill (job_kill); 需 run/server/eula.txt 含 eula=true; 端口在 run/server/server.properties (本机 25565 被系统占用,测试用 25599)
- runClient 未实测 (声音 Mixin/HUD 渲染需进游戏验证)

## 11. 已修复的原版缺陷

1. **LogForge NPE**: 原版在 preInit (FMLServerStartingEvent 之前) 调用 AllMusic.init 打日志时 AllMusicServer.server 为 null → 空指针。修复: server 为空时回退 LOGGER.info(getUnformattedText())
2. **getFormattedText NoSuchMethodError**: IChatComponent.getFormattedText() 是 @SideOnly(CLIENT), 服务端不存在 → 改用 getUnformattedText()
3. **service.close()**: Java 8 API 无此方法 → shutdown()

## 12. 聊天消息序列化 (按钮修复)

- **问题**: adventure 4.17+ 的 GsonComponentSerializer 输出**新版 JSON 格式**
  (`"click_event":{"action":...,"command":...}`), MC 1.7.10 只识别**旧格式**
  (`"clickEvent":{"action":...,"value":...}`) → 消息中的可点击按钮([点我查看]等)
  文字正常显示但点击无效
- **根因**: minimessage 4.26.1 的传递依赖把 adventure 全家升到 4.26.1
  (声明 4.8.1 无效, gradle 解析到 4.26.1)
- **修复**: 自写 ChatComponentSerializer (com.Lilith.FMusic.server) 输出 1.7.10 原生格式:
  - text/extra/color(小写命名色, 1.7.10 大小写不敏感)/bold/italic/underlined/strikethrough/obfuscated
  - clickEvent: run_command/open_url/change_page; suggest_command → 转 run_command (1.7.10 无此动作)
  - hex 颜色不支持 (1.7.10 无), 忽略
- **配色方案**: 前缀 <gold>[FMusic], 普通提示 <white>, 信息 <yellow>, 错误 <red>, 按钮 <aqua>
  (原: light_purple 前缀 + dark_green 正文; 36 个文件批量替换)
- 注意: 运行时 message.json 是生成物, 老配色需删除重载或手动替换

## 13. netapi 内置音乐 API (网易云音乐)

- 来源: tmp/netapi-main (AllMusic 音乐 API 示范插件), 集成于 com.Lilith.FMusic.netapi
- NetiApiMain implements IMusicApi, getId() = "netapi" (与 ConfigObj 默认 defaultApi 一致 → 默认使用)
- 注册点: 服务端核心 FMusic.start() → MUSIC_APIS.put, 先于 api/ 目录加载 (外部 jar 同 id 可覆盖)
- 功能: 搜索/点歌/播放链接/歌词(含KTV逐字)/电台/歌单导入 (music.163.com)
- 加密: WEAPI (AES+rsaEncrypt) / EAPI (md5 签名) / API 三种网易云接口加密方式 (CryptoUtil)
- cookie: 复用 MusicHttpClient 的 cookie.json 持久化, 配置浏览器 cookie 后可播放 VIP 歌曲 (见 README)
- 注意: 原插件 import com.coloryr.allmusic.libs.org.apache.hc... (原版 relocate 包名) → 已改为 org.apache.hc... (编译期原包, 运行时由本项目 shadow 自动 relocate)

## 14. FMusic.cfg 与 pause_at_freeze

- **FMusic.cfg** (config/ 下) 现为正式 Forge Configuration, 存放单人游戏暂停行为配置
- **指令**: `/fmusic pause_at_freeze <true/false>` (客户端指令, ClientCommandHandler)
  - 仅在单人游戏且未开放局域网时可执行 (mc.isSingleplayer() && !integratedServer.getPublic())
  - 无参数时查询当前值; 设置后立即 Config.save() 写入 config/FMusic.cfg
  - 默认 false (保持原行为: 暂停时音乐继续播放)
- **行为**: true 时按 Esc 暂停游戏 → FMusicPlayer.frozen → alSourcePause 暂停音乐;
  恢复游戏 → 播放循环自动 alSourcePlay 恢复 (自然结束判断排除 frozen, 避免暂停时误结束)
- **/music reload**: 服务端 CommandReload 额外调用 Config.reload() 刷新 FMusic.cfg
- **踩坑1 (配置不保存)**: Config.save() 若复用 synchronizeConfiguration 会重新 load 并用文件旧值覆盖内存,
  且从未把新值写回 Property → 文件不变、内存丢失。save() 必须独立实现: load → get().set(内存值) → save
- **踩坑2 (恢复无声)**: 自然结束判断曾用 `state != AL_PLAYING`, 会把 PAUSED(暂停中)误判为结束
  → eof=true 时恢复瞬间终止播放并 resetSource 清空缓冲。必须用 `state == AL_STOPPED` 才算真正播完;
  且恢复游戏时应主动 alSourcePlay, 不依赖播放循环 5ms 检查
- **计时冻结 (防歌词不同步)**: FMusic.frozen 静态标志 (server core) — 客户端 FMusicPlayer.tick 在
  暂停时置 true, 恢复时置 false; PlayRuntime.time1 与 FMusicHud.lyricTick 开头检查并跳过
  → 音乐/服务端 musicNowTime/musicLessTime/歌词触发/客户端KTV计时 全部同步暂停与恢复
  (musicLessTime 冻结意味着暂停时间不计入歌曲时长, 不会提前 STOP)
- **唱片旋转动画**: picRotateTick **独立**检测 Minecraft.isGamePaused() (不依赖 pause_at_freeze,
  视觉动画总是跟随画面暂停), 游戏暂停即停转, 恢复后继续

## 15. FMusic.cfg 历史记录 (模板 Forge 配置)

- 早期版本: Config.java 不注册配置项, 文件为空时写入固定提示内容; 现已被正式 Forge Configuration 取代
  (pause_at_freeze + hud_* 位置, 见第 14/16 节)
- 文件为空/不存在时写入固定提示内容:
  - server config is on ../fmusic_server
  - client config is on ./fmusic_client.json
- 双端 preInit 都会调用 Config.synchronizeConfiguration (run/server 与 run/client 各一份)

## 16. HUD 可视化配置界面 (照 PowerGoggles 复现)

- **指令**: /fmusic hudconfig (客户端指令) → DelayedGuiDisplayTicker 延迟 1 tick 打开 GUI
- **FMusicHudConfigGui** (com.Lilith.FMusic.client.gui):
  - doesGuiPauseGame=false; GUI 打开时 RenderGameOverlayEvent 不触发 (runTick 的
    currentScreen==null 分支), 故 drawScreen 里手动 FMusicCore.hudUpdate() 实时渲染 HUD
  - 每个 enable 的模块 (info/lyric/state/pic) 绘制一个红色拖拽手柄+白十字 (照
    PowerGogglesGuiHudConfig 的 10x10 手柄), 锚点 = FMusicHud.getPos(0,0,x,y,dir)
  - mouseClickMove: 鼠标坐标按 HudPosType 反推新 x/y (inversePos) → hud.setPos 实时生效
  - mouseMovedOrUp: Config.saveHudPos → 写入 config/FMusic.cfg (hud category)
- **保存**: Forge Configuration hud category 8 个键 (hud_info_x/y, hud_lyric_x/y,
  hud_state_x/y, hud_pic_x/y, 默认 -1); hasHudPos() 判定是否保存过
- **本地优先**: FMusicCore.packDo 收到服务端 HUD_DATA 时 Config.loadHudPos(obj) 用本地位置
  覆盖 x/y (删除 config 中 hud_* 键可恢复服务端配置)
- **FMusicCore.getHud() / FMusicHud.getHudPos()** 为配置界面暴露访问

## 17. 开发踩坑记录

### replace 静默失败 (hudconfig 分支丢失)

- **教训**: 用 edit/replace 修改文件后必须验证 before/after 差异, 不要盲报成功
- **案例**: 给 CommandFMusic 加 hudconfig 分支时, 匹配串写了 JS 转义 `\u00a7` 而文件里是
  实际 `§` 字符 → replace 无匹配静默跳过 → 指令永远走 else 分支显示用法提示
- **规避**: 涉及转义/Unicode 的修改优先用全量 write 重写; replace 后立即 grep 验证目标内容存在

### 构建 SSL 证书问题

- 现象: 偶发 `SSLHandshakeException: certificate_unknown` (gradle 换 JVM 后信任库不同)
- 规避: 依赖已缓存时用 `gradlew --offline` 构建绕过网络验证

## 18. 注意事项 / 待办

- 信道/配置/资源域名已全面改为 fmusic 命名 → **与原版 AllMusic 客户端/服务端不互通** (如需互通: 改回 fmusic:channel → allmusic:channel, fmusic_server/ → allmusic_server/)
- mixins.allmusic_client.json 原配置未复制 (两个声音 mixin 已并入 mixins.FMusic.json, 重复配置会导致重复应用)
- 运行时配置文件名 fmusic_client.json (客户端核心) / 目录 fmusic_server/ (服务端核心)
- 模板自带的 Config.java (Forge Configuration) 未使用, 可清理
- mcmod.info 描述仍是模板示例文案, 可更新
