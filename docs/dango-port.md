# DangoMusic 客户端播放器移植说明

本模组（FMusic）内置的「液态玻璃播放器」及其配套的 3D 歌词、Song card、灵动岛、
联网扫码登录、环境音效等，全部移植自 [DangoMusic](https://github.com/Sorihiko/DangoMusic)（作者 Sorihiko，GPL-3.0）。

本文记录移植范围、路径映射、为 1.7.10 做的必要改动，以及尚未实机验证的风险点。

## 一、移植范围

### 已移植

| 功能 | 上游位置 | 说明 |
|---|---|---|
| 液态玻璃播放器界面 | `common/ui/.../music/MusicScreen.kt` + `musicui/**` | 推荐 / 排行榜 / 我的歌单 / 搜索、列表 ↔ 封面墙、设置页、扫码登录弹窗 |
| 三个音源（网易云 / QQ音乐 / 酷狗） | `common/core/.../music/**` | 客户端直连，含扫码登录、cookie、歌单、推荐、排行榜、云盘、逐字歌词 |
| 3D 世界歌词 | `common/ui/.../module/impls/music/LyricsModule.kt` | 逐字、发光、抖动、文闪，`legacy/.../lyrics/DeepGlow.kt` 提供辉光 |
| Song card / 灵动岛 | `module/impls/music/SongCardModule.kt`、`DynamicIslandModule.kt` | 与 FMusic 原有 HUD 并存，各自开关 |
| 液态玻璃渲染管线 | `legacy/.../shader/**`、`ui/GlassRenderContext.kt`、`ui/glass/GlassBackdrop.kt` | 自建 GLSL（形状 / 图像 / 字体 / 玻璃 / 模糊 / 辉光三级降采样） |
| Inter 字体 + CJK 字体管理 | `legacy/.../font/**` | Inter 打包在图集里；CJK 用系统字体按需生成图集，不打包字体文件 |
| 环境音效 + 房间回声 | `common/core/.../music/EnvironmentAudio.kt`、`RoomAcoustics.kt` | 发射线测量空间混响、水下闷音、雨声压音 |
| 生物踩点 Dance | `common/core/.../music/Dance.kt` + `mc1710/DanceRender.kt` | 按 BPM 网格抖动生物；1.7.10 侧挂在 `RenderManager` 实体分发外层并限定世界渲染范围，YSMU / 其它替换渲染器同样生效（见 §3.13） |
| 音频分析 / 音量归一化 | `common/core/.../music/AudioAnalysis.kt` | 让不同歌曲响度一致 |
| 云盘 | `CloudDrives.kt`、`KugouCloud.kt`、`QqCloud.kt` | 只读浏览，无上传/删除 |
| 更新检查 | `common/core/.../update/UpdateChecker.kt` | 改为查 FMusic 自己的 release |
| 快捷键绑定 | `platform/PlayerKeyBinding.kt` + `legacy/.../LegacyKeyBindings.kt` | 注册进原版按键设置，默认 `M` 打开播放器 |
| 歌手详情页 | `musicui/ArtistPage.kt`、`common/core/.../music/SourceArtists.kt` | 头像 + 主题色提取、别名、两行简介、歌曲/专辑/热门数、热门歌曲 / 专辑 / 简介三标签（弹簧切换）、专辑按封面墙打开、播放与随机播放；三家音源都有（网易云走 `NeteaseApi`，QQ/酷狗走 `SourceArtists.kt`） |
| 歌手名可点 + 多层返回 | `ArtistPage.drawArtistLinks()`、`Library.PageSnapshot` + `ScreenState.trail` | 歌曲行/播放栏/专辑里的歌手名可点进歌手页；进歌手页、从歌手页进专辑可逐层返回（最多 20 层） |
| 播放器窗口拖动 / 缩放 | `MusicScreen.kt` 的 `Grab` / `winRect` / `grabAt` / `dragWindow` / `fitWin` 等 | 拖边拖角改大小（最小 880×480，不出屏）、拖顶部空白移动、双击顶部空白还原；位置大小按屏幕比例存进 `Window X/Y/width/height`，换分辨率自动换算 |
| 进度条拖动 seek | `PlayerBar.kt` 的 `seeking` / `seekFraction` / `fractionAt` / `mouseUp` | 拖动中只移动显示与时间标签，**松手才真正 seek**，避免反复重解码 |
| 音频输出选择 | `AudioOutput.kt`、`MusicPlayer.outputMode`、`platform.openAudioOutput` | 自动 / Java Sound / 游戏声音（OpenAL）三档；运行中切换会从当前位置在新输出上重开 |
| 音频输出掉线自愈 | `AudioOutput.lost`、`JavaSoundOutput.writeAll/checkStall/drain`、`MusicPlayer.reopenOutput` | 换默认设备（插蓝牙耳机）、设备被独占、`F3+T` 重载声音资源后自动在新设备上续播，不再永久卡死解码线程 |
| 原版 BGM 让路 | `Platform.holdVanillaMusic` + `VanillaMusicHold` + `ForgeBridge.onPlaySound` | 放歌时压制原版背景音乐，只拦 `SoundCategory.MUSIC`（不误杀唱片机/音乐盒） |
| 分层玻璃播放栏 | `Gfx.recaptureBackdrop` + `Ui.occluder` / `Ui.bottomInset` + `MusicScreen.drawBar` | 播放栏浮在内容之上、玻璃实时采到栏后内容；列表/封面墙可滚到栏后，悬停不再穿透 |
| **均衡器（1.7.5）** | `common/core/.../music/Equalizer.kt` + `musicui/EqPanel.kt` | 10 段峰值滤波器（RBJ 公式）串起来，11 个预设，前级增益按整条频响最高处自动算，最后过一道 tanh 软限幅（26000 以上才压）；播放栏上的按钮弹出浮层面板 |
| **变速变调（1.7.5）** | `common/core/.../music/SoundStretcher.kt`、`SoundTuning.kt` + `musicui/TunePanel.kt` | 速度 0.5x～2x（WSOLA，音高不变）、音调 ±12 半音（拉伸 + 三次插值重采样，时长不变），可勾「音调跟着速度」；都是 1 时完全 bypass（不加延迟、不烧 CPU） |
| **按歌记住均衡 / 速度 / 音调（1.7.5）** | `SoundTuning.kt`（`sound_tuning.json`） | 一份全局设置 + 最多 2000 首的按歌记录（`Magic:track.key`）；关掉「按歌记住」这首歌就回到全局设置；拖滑条时攒 500ms 落盘 |
| **显示其它播放器（1.7.5）** | `common/core/.../music/SystemMedia.kt` + `assets/fmusicdango/system-media.ps1` | 仅 Windows 10+：用系统自带 PowerShell 常驻读 Windows 媒体控制（SMTC），把别的 App 的歌名歌手拿去当前音源搜一次，配上了就有封面和歌词；暂停/切歌/拖进度从 stdin 发回去。默认关，在设置「其它播放器」里打开 |
| **加到歌单（o.o）** | `musicui/AddPanel.kt` + `Library.loadAddTargets/addToPlaylist` + `MusicSourceApi.canAddToPlaylist/editablePlaylists/addToPlaylist` | 播放栏红心旁边的「＋」弹出当前音源的自建歌单列表（最多 8 行 + 滚轮），点一个就把正在放的歌加进去；三家音源都支持（网易云 weapi、QQ 的 `PlaylistDetailWrite/AddSonglist`、酷狗 `cloudlist/v6/add_song`）。放别的播放器的歌时不显示 |
| **红心抽象 + 酷狗红心（o.o）** | `MusicSourceApi.canLike/likedState/setLiked` + `Library.likedState` + `KugouCatalog.setLiked` | 红心从「只有网易云」变成按音源实现：网易云沿用原来的 `loadLiked/isLiked/setLiked`，**酷狗**用「我喜欢」歌单（`cloudlist/v4/get_list_all_file` 读、`delete_songs` 删、`add_song` 加，10 分钟缓存 + 刚点亮的内存态）；QQ 明确不做（写 dirId 201 会被拒 80105，上游同） |
| **播放模式按钮（o.o / 1.7.6）** | `NeteaseMusicModule.cyclePlayMode()` + `PlayerBar.drawModeButton` | 播放栏左侧一个三态按钮：顺序（→）→ 单曲循环（↻+1）→ 随机（⇄），非顺序时变红，点了在播放栏提示当前模式；原来右侧分开的随机 / 单曲循环两个按钮撤掉，右侧让给均衡器 / 变速变调。1.7.6 把循环顺序改成和网易云 App 一致（原来是 顺序→随机→单曲循环） |
| **逐字歌词漂移修复（o.o）** | `lyrics/LyricsProvider.repairYrc` + `NeteaseLyrics` | 网易云有些歌的逐字歌词后半截整体往后跑偏（一个字能占十几秒，歌词卡在半句）。现在拿普通 LRC 对照，连续 3 句差 10 秒以上就从切换点起改用 LRC 的时间；制作人员名单行不参与判断 |
| **「算出 NaN 就没声」防御（o.o）** | `EnvironmentAudio` / `RoomAcoustics` / `Equalizer` | 空间混响、环境音效、均衡器里只要混进一个 NaN/无穷，滤波器状态就永远是 NaN、写回声卡全是 0——表现是「播放着突然彻底没声」。现在参数和采样都做有限性检查，坏了就清状态/回退到「不加效果」，混响测量也丢弃非有限结果 |
| **环境音效湿声修复（1.7.6）** | `EnvironmentAudio` / `RoomAcoustics` | 回声湿声不再用固定系数直接叠加，改成湿声单独跟峰值走（起音 1ms / 释放 250ms），峰值超过满幅约 30% 就压下来，原声再按 wet 让出一点余量；房间早期反射延迟改用「第一下碰壁距离的中位数」（原来用平均自由程，从门窗穿出去、碰到远处地形的几根射线会把平均拉得很大，石头房里延迟一直顶到 80ms）。修的是「低音重、响度大的歌在室内发闷、发颤，甚至中途没声」（上游 #65） |
| **播放栏图标重画（1.7.6）** | `musicui/Ui.kt` 的 `snap` / `snapLength` / `crispLine` + `PlayerBar` / `EqPanel` / `TunePanel` / `AddPanel` | 细线粗细取整到屏幕像素、中心对齐像素：界面缩放不是整数（全屏）时平行细线不再有的实有的虚；播放模式按钮改成线条画的图标（顺序 = 两条右箭头、单曲循环 = 带缺口的圆箭头 + 1、随机 = 两条交叉箭头），上一首 / 下一首 / 队列图标改按按钮中心对中 |
| **播放模式循环顺序（1.7.6）** | `NeteaseMusicModule.cyclePlayMode()` | 顺序 → 单曲循环 → 随机 → 顺序，和网易云 App 的循环顺序一致（原来是 顺序 → 随机 → 单曲循环） |

### 明确不移植

- **一起听歌（Listen Together / Firebase）**：`common/core/.../party/**`、`musicui/PartyPage.kt` 整体没有搬入。
  随之去掉的还有：`MusicPlayer` 里的房主/成员转发分支、`Sidebar` 的入口、`Library.Page.PARTY`、
  `PlayerBar` 的成员失败提示、`UpdateChecker` 里「一起听歌中转」的 `/update` 兜底路径。

## 二、路径映射

| 项 | 上游 | 本项目 |
|---|---|---|
| 根包名 | `me.sorihiko.dango` | `com.Lilith.FMusic.dango` |
| 1.8.9 平台层包 | `me.sorihiko.dango.legacy` | `com.Lilith.FMusic.dango.mc1710` |
| 资源域 | `assets/dangomusic` | `assets/fmusicdango` |
| 客户端配置 / 登录凭证 / 缓存 | `.minecraft/config/dangomusic/` | `.minecraft/config/fmusic/dango/` |
| 按键 lang key | `key.categories.dangomusic` / `key.dangomusic.*` | `key.categories.fmusic_dango` / `key.fmusic_dango.*` |

上游各子包（`config` / `module` / `music` / `musicui` / `ui` / `gfx` / `font` / `shader` / `lyrics` / `platform` / `utils`
/ `events` / `service` / `update`）结构保持不变，方便以后和上游 diff 同步。因此 `common/core` 与 `common/ui`
两份源码在同一包空间里合并（上游打包时本来也是这样）。

## 三、为 1.7.10 做的必要改动

### 3.1 平台层的 API 替换

`legacy/**` 是写给 1.8.9 的，逐个换成 1.7.10 等价 API：

- 没有 `GlStateManager`（1.8+ 才有），全部直接用 LWJGL2 的 `GL11/GL12/GL13/GL20/GL30/EXTFramebufferObject`。
- 没有 `net.minecraft.util.BlockPos`：`world.getBlockState(pos).block` → `world.getBlock(x, y, z)`。
- `World` 的 `isRainingAt` / `isThundering` / `skylightSubtracted`、`Block.soundType*` 静态字段、
  `Material.leaves`、`ChatStyle.setTextChatClickEvent` 等按 1.7.10 实际签名改写。
- 拿不到的运行时信息（例如 1.7.10 没有 `NetHandlerPlayClient.getPlayerInfo(...).responseTime`）降级处理，
  返回安全默认值并保留注释说明。

### 3.2 MP3 解码：额外 shade 一份 jlayer

DangoMusic 的播放链路是「`byte[]` → 分块解码」，用的是原生 `javazoom:jlayer`。
FMusic 自带的那份 jlayer 是**魔改移植**（`client/core/player/decoder/mp3`）：构造函数绑定 `FMusicPlayer` 网络流、
`Decoder` 被合并进 `Mp3Decoder`、`LayerXDecoder` 包私有，没有 `byte[]` 入口，加一个入口需要复制内部驱动逻辑。

为了**完全不动**这条正在线上跑的播放链路，这里改为额外 shade 一份未修改的 `javazoom:jlayer:1.0.1`
（会被 relocate 到 `com.Lilith.FMusic.shadow.javazoom`，与自带那份零冲突）。上游自己也是 shade jlayer 的，
所以这反而是最「原封不动」的选择。

### 3.3 Ogg Vorbis：`DangoVorbis` 放进我们自己的 jorbis 包

`DangoVorbis` 需要访问 `VorbisFile` 里 package-private 的 `vd` / `decode_ready`，
所以它被放进 `com.Lilith.FMusic.client.core.player.decoder.ogg.jcraft.jorbis`（与我们那份 jorbis 移植同包），
而不是单独一个包。逻辑一字未改。

FLAC 用上游自带的 `FlacReader`（自包含，无外部依赖）。

### 3.4 两套播放系统并存

按需求，DangoMusic 播放器与 FMusic 原有的服务端点歌系统**完全独立、双轨运行**：

- DangoMusic 侧：客户端直连音源、自己的账号与歌单、Java Sound 出声。
- FMusic 侧：服务端点歌 / 投票 / B 站弹幕，客户端 OpenAL 流播服务端推送的地址。

两个系统**同时只允许一个出声**，由 `mc1710/DangoAudioArbiter.kt` 仲裁。

**两边"让路"的方式必须不同，这是本移植里最容易做错的一处：**

| | 让路方式 | 为什么 |
|---|---|---|
| 点歌（服务端） | **只静音，不暂停不停止** | 点歌和服务端同步（服务端按歌曲时长推进，歌词/KTV 由服务端推送）。本地一旦暂停/关掉流，位置就和服务器上其他人错开，恢复时歌词和进度对不上；而且 `closePlayer()` 会把播放任务直接销毁，服务端也不会重推，等于"这首歌没了" |
| DangoMusic（纯客户端） | **暂停** | 它不需要和任何人同步，暂停保留进度，恢复时从原处接着放 |

具体做法：`FMusicPlayer#setMuted()` 只把 OpenAL 源的增益压到 0（并绕过原来 5% 的音量下限），
流照常按实时推进；`FMusicCore#setMuted()` 是给仲裁器用的公开入口。
这样 DangoMusic 一停下来，取消静音听到的就是**此刻的正确位置**，歌词和服务器上其他人始终一致。

极性：**DangoMusic 绝对优先，点歌永不反抢。**

1. DangoMusic 在出声 → 点歌静音（继续流式，保持与服务器同步）；
2. DangoMusic 不出声（暂停 / 停止 / 放完）→ 点歌取消静音，听到的是此刻的正确位置；
3. 服务端推来新歌**不会**暂停 DangoMusic（原来那条「对称让位」已按要求去掉）；
   而且 DangoMusic 出声期间点歌一进来就是静音的，不会先漏出一两声。

判断是纯电平的（只看 `MusicPlayer.state`），没有边沿逻辑，所以不存在两个方向互相触发的可能。
`mutedByUs` 只用来区分「静音是仲裁器做的」还是「用户自己调的音量」，避免还原时覆盖用户设置。

副作用：DangoMusic 播放期间点歌仍在下载/流式（只是听不见），这是「保持同步」的必然代价。

### 3.5 HUD 并存

FMusic 原有 HUD（歌词 / KTV / 封面 / 进度条）与新移植的 Song card、灵动岛、3D 歌词是三套独立模块，
各自有开关。**默认只开老的 HUD**，升级后画面零变化；想用新样式的玩家自己在播放器设置里打开。
具体默认值：

| 模块 | 默认 | 说明 |
|---|---|---|
| `Lyrics` 模块自身 | 开 | 上游强制打开（`NeteaseMusicModule` 每 tick 补开一次），保留 |
| └ 3D lyrics（世界里的歌词） | **关** | 改成默认关，因为老 HUD 已经在显示歌词 |
| `SongCard` | 关 | 上游本来就是关 |
| `DynamicIsland` | **关** | 改成默认关 |
| `NeteaseMusic`（播放器本体） | 开 | `M` 键随时能打开 |

### 3.6 构建集成

- 项目主体是 Java + GTNH convention 构建，这里仅为移植代码引入 Kotlin：`kotlin("jvm")`
  **不写版本号**（GTNH/Elytra 的插件链上已有 Kotlin Gradle 插件，显式指定版本会被 Gradle 拒绝），
  `jvmTarget = 1.8`（MC 1.7.10 跑在 Java 8 上）。
- Kotlin 标准库、jlayer、ZXing 都走 `shadowImplementation`，被 relocate 到 `com.Lilith.FMusic.shadow.*`；
  已确认成品 jar 里没有未 relocate 的 `kotlin/`、`javazoom/`、`com/google/zxing/` 残留。
- 移植过来的 `.kt` / `.java` 被排除在 spotless 之外，保持上游排版，方便以后和上游 diff。

### 3.7 其他为跑通而做的判断

- **3 个 glow 着色器不注册**：`GlowDownsampleShader` / `GlowUpsampleShader` / `GlowCompositeShader`
  （以及 `assets/fmusicdango/shaders/glow/*.frag`）引用了 `shaders/outline/base.vert`，而这个文件在本项目里不存在，
  结果是每次启动刷 3 条加载失败日志。**已核实这三个类上游从无此物**：上游 legacy / common / modern 全树
  grep `GlowDownsample|GlowUpsample|GlowComposite` 零命中，shader 资源树也只有 `quad` + `ui/{font,glass,glass_blur,image,shape}`，
  没有 `glow/*.frag`、没有 `outline/base.vert`。也就是说这三个是**本模组单方面新增、且从未接线的遗留文件**
  （真正的辉光是 `lyrics/DeepGlow.kt` 里的内联 GLSL + 自建 FBO），所以不注册。
  源码与 `.frag` 暂时保留不删；将来若要启用，需先补上 `outline/base.vert` 并真正接线。
- **互斥仲裁完全收在移植层里**：`DangoAudioArbiter.tick()` 同时轮询两边的「是否正在出声」，
  靠状态跳变识别「刚开播」，因此**不需要改 FMusic 原有的 `FMusicPlayer` / `FMusicCore` 播放路径**。
  唯一对我们老代码的改动是给 `FMusicCore` 加了一个公开的 `hardStop()`（内部就是原来的私有
  `stopPlaying()`），避免用 STOP 指令时「结尾附近优雅收尾」导致两首歌短暂叠音。
- **1.7.10 缺的信息降级处理**：`fps` 改成按两次取值的间隔估算（1.7.10 没有 `Minecraft.getDebugFPS()`，
  而生产环境是 SRG 名，字符串反射拿不到），`pingMs` 恒 0，`isRainingAt` 用最接近的
  `world.canLightningStrikeAt(x,y,z)` 近似。这三处只影响显示/环境音效的细微差别，代码里都写了注释。
- **音频后端**：桌面 Java 自带 `jsound`，走 Java Sound（`SourceDataLine`）；`openAudioOutput`
  （游戏 OpenAL）在 1.7.10 平台层**已实现**：`LegacyPlatform.LegacyOpenAlOutput` 用 LWJGL2 的
  `AL10/AL11/ALC10/AL` 做 50ms/块、最多 5 块的流式输出（对齐上游 modern 的 `GameAudioOutput`），
  每个线程首次调 AL 前 `alcMakeContextCurrent(gameContext)`，绑的是游戏自己的同一个环境。
  设置里的「音频输出」三档（自动 / Java Sound / 游戏声音 OpenAL）因此可用（`hasGameAudio = true`）。
  任一步失败（`alGetError()` 非 0、建不出源、`!AL.isCreated()`、精简 LWJGL 无 `isCreated/getContext`）
  都返回 null，`openOutput` 必定回落 Java Sound，选错也不会静音。

### 3.8 聊天「点击复制」：用 mixin 做到了和上游一致

歌词/点歌消息里我们自己生成的按钮是 `SUGGEST_COMMAND` 点击事件（1.7.10 没有 `COPY_TO_CLIPBOARD` 动作），
所以「是复制还是填输入框」完全取决于谁拦这次点击。

- **上游 1.8.9**：挂在 `GuiScreenEvent.MouseInputEvent.Pre`（`EventPriority.HIGHEST`）上，
  命中我们自己的链接就 `GuiScreen.setClipboardString(value)` + `e.isCanceled = true`。
- **1.7.10**：Forge 没有 `MouseInputEvent`（1.8 才加）。所以改成在
  `GuiChat#mouseClicked` 的 **HEAD** 注入并 `ci.cancel()`，判定条件与上游逐条对齐：
  左键 + 光标下那一段文字的 `chatClickEvent` **按对象身份**是我们创建的（`IdentityHashMap`，
  服务器发来的同名 `SUGGEST_COMMAND` 不会被接管）→ 复制 + 取消原版处理。

这样点「点击复制」**只进剪贴板，不会再顺手把同一段文字填进聊天输入框**，与上游行为一致。
改动位置：

- `mixins/early/MixinGuiChat.java`（在已有的「输入即提示」mixin 里新增一个 `mouseClicked` 注入，
  mixin 配置无需改动，`MixinGuiChat` 已在 `mixins.FMusic.json` 的 `client` 列表里）
- `mc1710/LegacyChatCopy.kt` 新增 `@JvmStatic fun handleClick(mouseButton: Int): Boolean`
  （保留 `internal` → 改 `public object` 是为了让 Java mixin 直接调用，见 `javap` 输出的
  `public static final boolean handleClick(int)`）

已确认构建期 refmap 把注入点正确映射成 SRG：
`"mouseClicked": "Lnet/minecraft/client/gui/GuiChat;func_73864_a(III)V"`，
所以生产环境（混淆名）下注入同样生效。

> 之前用「每帧采样左键边沿」在 `DrawScreenEvent.Pre` 里补的做法已经删掉：
> mixin 是等价替换且能取消原版处理，不需要再采样。

### 3.9 上游 1.7.5 三个新功能：移植时的取舍

1.7.5 新增「均衡器 / 变速变调 / 按歌记住 / 显示其它播放器」四块（另外还有一条 1.21.10 / 1.21.11
的歌词发光修复，改的是 `modern/.../McCompat.kt` 里的 `vanillaDraw` 帧缓冲重定向：原版每画完一批就把
帧缓冲绑回 0，换字形贴图页后只有第一批字进了发光画布，所以改成用 `keepRedirect()` 重新借道。
这套重定向只存在于 1.21 平台层，本项目 1.7.10 侧的自建 FBO 发光没有这条路径——`grep vanillaDraw`
零命中，所以不移植）。
逐文件核对过：除了下面这几处必要的适配，`Equalizer.kt` / `SoundStretcher.kt` / `SystemMedia.kt` /
`EqPanel.kt` / `TunePanel.kt` 与上游**逐字节相同**，`SoundTuning.kt` 只差 `speedLocked` 一处。
以后同步上游时按这个清单复读即可。

| 适配点 | 上游 | 本项目 | 为什么 |
|---|---|---|---|
| 一起听时锁 1x | `SoundTuning.speedLocked` 读 `ListenParty.role` | 恒 `false`，注释说明 | 一起听整体没移植（见「明确不移植」）。连带效果：变速面板里「一起听时固定 1x」那句文案永远不会出现 |
| 播放进度从哪来 | `MusicPlayer.positionMs` 读 `line?.positionMicros` | 读解码线程采样的 `positionMicrosAt` + 渲染线程插值 | 本模组早先为了不让渲染线程和阻塞的 `write()` 抢锁，已经把「问声卡」从渲染线程挪走了（见 §6 第 5 条）。`anchor()` / `songAt()` 的写法与上游一字不差，只是喂给它的声卡时间来自插值 |
| ps1 资源路径 | `/assets/dangomusic/system-media.ps1` | `/assets/fmusicdango/system-media.ps1` | 资源域映射 |
| ps1 换行 | 仓库里是 CRLF | `.gitattributes` 有 `*.[pP][sS]1 text eol=crlf`，所以也存成 CRLF | 纯仓库约定；脚本内容经过 UTF-16LE Base64 传给 `-EncodedCommand`，换行对运行毫无影响 |
| 显示别的播放器时的 HUD / 播放栏 | 上新了 `MusicPlayer.shownTrack / shownState / shownPositionMs / shownDurationMs / shownActive / external`，HUD 与播放栏改用它们 | 同上游 | 这套访问器是纯读取层，`state` / `current` 仍是「自己是否真的在出声」，所以 `DangoAudioArbiter`（与点歌系统互斥）和 `Dance` 用的还是老访问器，**互斥逻辑不受影响** |

几个容易踩的点，写在这里省得以后重新推：

- **均衡器关着的时候解码线程根本不创建 `Equalizer`**（`SoundTuning.effective()` 的 `eq` 为 `null`），
  所以默认设置下这条链路是纯直通、零开销。`Equalizer.process()` 末尾那道 `tanh` 软限幅是无条件的，
  但只有真的开了非平直均衡器才会走到它。
- **变速变调同样是「参数都是 1 就整个跳过」**（`tune.bypass`），不建 `SoundStretcher`、
  不加 WSOLA 延迟。从 1 调开 / 调回 1 时会新建 / 丢弃 stretcher，歌曲时间直接对回解码位置。
- 变速以后**进度条 / 歌词 / 3D 歌词全靠 `anchors`**（声卡时间 ↔ 歌曲时间，按 speed 外推）：
  写声卡的第 N 块记一笔 `(outUs, songMs, speed)`，渲染线程拿声卡当前位置查最近的一笔。
  所以千万别把 `songOutMs` 的推进（`outMs * tune.speed`）改掉，否则变速时进度会漂。
- **按歌记住**用 `MusicPlayer.current.key`（= `音源:歌曲id`）当键，`sound_tuning.json` 和
  `config/fmusic/dango/` 下其它文件同级；上限 2000 首，超了从最早的开始扔。
- **显示其它播放器**匹配不上的歌会造一个 `nativeId` 以 `smtc:` 开头的占位 `NeteaseTrack`
  （只有歌名 / 歌手 / 时长，没有封面），歌词模块看到这个前缀就**不去查歌词**，避免拿空 ID 打接口。
- `SystemMedia` 起的是 `powershell.exe -EncodedCommand <Base64>`（`-NoProfile -NonInteractive
  -ExecutionPolicy Bypass`），不落临时文件、不受执行策略影响；stderr 直接丢 `NUL`（Java 8 没有
  `Redirect.DISCARD`）。系统没有 SMTC 时脚本 `exit 2`，连续失败 3 次就不再重启，不会天天刷日志。
  开关关掉、模块关掉、游戏退出（shutdown hook）都会 `destroy()` 掉这个进程。

### 3.10 上游 `o.o` 提交（1.7.5 之后）：移植时的取舍

上游在 1.7.5 之后又推了一个 `o.o`（commit `7d1609d`，19 个文件、+706/−64），内容是
**「加到歌单」+「红心抽象（顺带补上酷狗红心）」+「播放模式三态按钮」** 三个功能，
外加 **逐字歌词漂移** 和 **「算出 NaN 就没声」** 两个修复。逐文件核对过：本项目这 16 个文件
在 o.o 之前与上游**逐字节相同**，所以 o.o 的补丁基本原样落地；落地后把包名归一化再 diff，
除下面列的本地差异外**逐字节一致**。

需要注意的几处：

| 适配点 | 说明 |
|---|---|
| 一起听相关 | o.o 的 `PlayerBar` 补丁里夹着上游自己的一起听提示块（`blockedNotice` / `failedMembers` / `ListenParty.members`）。本项目没有一起听，这几块**没有搬**（和之前的移植保持一致）；`Library` 的 `Page.PARTY` 同理。 |
| `RoomAcoustics` | 本项目之前加过一个本地优化：「玩家站着不动就不重发射线」。o.o 的有限性检查插在测量之后，两者不冲突；副作用是被丢弃的坏测量不会立刻重测（站在同一个方块里会继续沿用上一次的好值，`room == null` 时仍会继续重测）。 |
| `NeteaseApi` | 只差注释里的配置路径（`config/fmusic/dango/netease_cookie.txt`），保留我们的写法。 |
| QQ 红心 | 上游 o.o 明确不做（往 dirId 201 写会被拒 `80105`，只有真 App 的设备身份才认），所以 QQ 只有「加到歌单」，红心判据 `canLike` 走默认 `false`。 |
| 死代码 | 上游 o.o 里 `Library.likedCheckedAt` 变成了没人用的字段、`PlayerBar` 的 `toggleButton`/`shuffleA`/`repeatA` 和 `MusicSource`/`NeteaseApi` import 也不再被引用——**为了和上游保持一致都原样留着**（只会产生 unused 警告，构建没有 `allWarningsAsErrors`）。 |

写歌单这条链路的关键点（以后调 API 时看这里）：

- **网易云**：`editablePlaylists()` 用 `/api/user/playlist/` 自己建的那些（`creator.userId == uid` 且
  `specialType != 5`，那个 5 就是「我喜欢的音乐」，红心走另一条路）；加歌走 weapi
  `playlist/manipulate/tracks`，`code 200` = 加上了、**`502` = 本来就在里面**（不是错误）。
- **QQ 音乐**：自建歌单的 `dirId` 就是 `1..199`（`QqCatalog.Ref.write`），加歌走
  `PlaylistDetailWrite/AddSonglist`；回包 `result.dirId != 0` 才算真的改了，`0` = 什么都没变。
  写歌单要数字 `songId`，所以 `NeteaseTrack` 多了一个 `sourceSongId`：解析时顺手记下，
  没有就按 mid 调一次 `get_song_detail_yqq` 再回写。
- **酷狗**：「我喜欢」= `is_def == 2` 的那个歌单，自建可写的只有 `type == 0`。
  加歌 `cloudlist/v6/add_song`（整个曲目对象塞进 `data` 数组），删歌要 `fileid`，
  所以取消红心时会先读一遍歌单列表（`v4/get_list_all_file`，每页 50，10 分钟缓存），
  刚点亮还没拿到 `fileid` 的先记在 `likedPending` 里，界面照样显示红心。
- 红心 / 加歌单之后都会 `Library.invalidateTracks(...)`：**自己歌单的缓存作废**，
  正在开着的那个在后台重拉，下次打开一定能看到新加的歌。

### 3.11 输入法（IME）：搜索框等输入框能打中文了

上游 1.8.9 那边也是"打不了中文就复制粘贴"，所以 `Sidebar` / `TracksPage` 里留着一条粘贴兜底
（`Ctrl+V`）。1.7.10 这边补齐了平台的文字输入，中文 / 日文 / 韩文可以直接用输入法输入：

- **`MusicScreen.textInputActive` 这次真的接上了**（上游的 modern 平台层就是每帧 `setImeFocus(screen.textInputActive)`
  去打开系统输入法；1.8.9 平台层没做，所以移植时这个属性只剩声明、没人用）。平台层每帧把它同步给
  `mc1710/LegacyTextInput.kt`。上游 modern 的做法是"让一个看不见的原版 `EditBox` 拿到焦点"，
  1.7.10 这边等价的做法是调 lwjgl3ify 给自绘输入框准备的 `InputEvents.beginTextInput()/endTextInput()`。
- **lwjgl3ify（GTNH 现在跑的输入层）**：SDL 只在 `SDL_StartTextInput` 打开时才把输入法的组字结果
  交给游戏，而 lwjgl3ify 只在原版 `GuiTextField` 获得焦点时才代它打开 —— 我们是自绘输入框，
  所以组字结果一条都进不来（表现就是：输入法打出来的字母进了搜索框，选完字的汉字没了）。
  这里按 lwjgl3ify 的公开 API `InputEvents.beginTextInput()/endTextInput()` 自己开关，
  用 `InputEvents.KeyboardListener#onTextEvent` 收文本；SDL 文本输入开着时可打印字符
  **只认 TextEvent**，按键事件里带的那个字符丢掉（和 ModularUI2 输入框一致，否则英文输两遍）。
- **纯 LWJGL 2（没装 lwjgl3ify）**：组字结果是一条 `keyCode = 0` + `keyState = false` 的纯字符事件，
  1.7.10 的 `GuiScreen#handleKeyboardInput` 只看 `keyState`，于是整条丢掉 —— 这是 InputFix 修的根因，
  这里用同一套判据补回来；装了 InputFix 时自动让位，避免一个字进两遍。
- lwjgl3ify 的 API 全部反射访问（不是编译期依赖，同一份 jar 也要能跑在纯 LWJGL 2 上），
  拿不到就只退化到"纯 LWJGL 2 那套"，不会因为缺类崩。详细根因和验证见
  [knowledge.md §40](knowledge.md#40-输入法-ime-在-dangomusic-输入框里打不了中文-2026-11)。

### 3.12 上游 1.7.6：移植时的取舍

上游 1.7.5 到 1.7.6（tag `v1.7.6`，HEAD `149933a`）之间一共三个提交：`o.o`（`7d1609d`，见 §3.10，
已全部落地）、`1.7.6`（`d7d8908`）、`fix env audio wet level`（`149933a`）。
`1.7.6` 的更新日志和三个提交逐项对过，处理如下：

| 1.7.6 内容 | 处理 | 原因 |
|---|---|---|
| 音质选择：设置里选默认音质（标准 / 极高 / 无损）+ 播放栏给当前这首临时换 + 三家音源按档位取流并逐档回退；`AudioQuality.kt` / `PlaybackQuality`、`MusicPlayer.playingQuality`、`MusicPlayer.reloadWithQuality`、新版 `QualityPanel.kt`、`KugouLite` / `KugouPlayback` / `NeteasePlayback` / `QqPlayback` 的取流改动 | **不移植**（本项目先前的音质 PR 已有等价且更完整的实现） | 本项目在 1.7.6 之前就独立实现了音质切换（`AudioQuality` + `AudioCache` / `MusicCache` + 播放栏 `QualityPanel` + `MusicPlayer.currentQuality` / `switchQuality`）：比上游多了本地缓存（按音源 / 账号 / 歌 / 实际音质隔离，复用低档缓存不冒充所选档位）、实际音质显示与回退提示、加载中切换、设置页与播放栏共用一条路径。上游的 `PlaybackQuality` 全局对象和 `reloadWithQuality` 只是同一件事的另一种写法，搬过来只会重复。逐文件也核对过：上游 1.7.6 对 QQ（`F000/M800/M500` 列表）和酷狗的按档位取流我们都有，且我们的 `QqPlayback` / `KugouPlayback` / `KugouLite` 还多了结果解析、质量判定和取消支持。唯一写法差异是网易云无损档上游把 `encodeType` 显式写成 `flac`——本项目的 `ec1e539` 提交说明里记录了「用会员 cookie 端到端验证 lossless → flac」，说明现有 `encodeType=mp3` 写法在 `level=lossless` 下拿到的就是 FLAC，故不改 |
| 播放栏图标重画（`Ui.snap` / `snapLength` / `crispLine`，`PlayerBar` / `EqPanel` / `TunePanel` / `AddPanel` 图标线条化、按屏幕像素对齐） | **移植** | 纯观感改进：全屏（界面缩放不是整数）时细线才一样粗、不虚。我们多出来的悬浮提示层、音质徽章与本改动无关，原样保留 |
| 播放模式循环顺序改成 顺序 → 单曲循环 → 随机（和网易云 App 一致） | **移植** | 一行改动，播放栏按钮与网易云的循环顺序对齐 |
| 环境音效湿声修复（`EnvironmentAudio` 湿声峰值包络压缩 + 原声让位、`RoomAcoustics` 早期反射延迟改用第一次碰壁距离的中位数） | **移植** | 真实 bug：低音重、响度大的歌在石头房里发闷发颤、甚至中途没声（上游 #65） |
| 「打开自己的歌单自动刷新」「QQ 音乐总放 128k」「网易云歌词卡半句」 | 无需额外移植 | 前两条分别属于 `o.o`（§3.10）和本项目的音质实现；歌词漂移已在 `o.o` 里修掉 |

移植后的核对方式：把上游 `v1.7.6` 源码做包名归一化（`me.sorihiko.dango` → `com.Lilith.FMusic.dango`）
再逐文件 diff——`EnvironmentAudio.kt` 与上游**逐字节一致**；`RoomAcoustics.kt` 只多我们本地「站着不动不重测」
的优化；`Ui.kt` / `EqPanel.kt` / `TunePanel.kt` / `AddPanel.kt` 的对应片段与上游一致，`PlayerBar.kt`
只差我们自己的悬浮提示层和音质徽章排版（上游没有这两块）。`d7d8908` 里的 `NeteaseMusicModule` 音质设置项、
`SettingsPage` 音质选项、`zh_cn.tsv` 音质文案同样因为「已有实现」不重复添加。

### 3.13 生物踩点抖动的 YSMU / 替换渲染器兼容

上游的「生物跟着节奏跳」只算姿势（`Dance.pose()`），套姿势是各平台层的事；1.7.10 这边原先照搬
1.8.9 的 `RenderLivingEvent.Pre/Post`（在 `mc1710/ForgeBridge.kt` 里）。这层挂点只要有替换渲染器
模组就不成立：

- **YSMU 玩家**：YSMU 在 `RenderPlayerEvent.Pre` 就 `setCanceled(true)` 并自己画 GeckoLib 模型，
  原版 `RenderPlayer#doRender` 直接 return，`super.doRender` 里的 `RenderLivingEvent.Pre` 根本不会发；
- **YSMU NPC / 载具**：NPC 在 `RenderLivingEvent.Pre` 里取消并当场上屏（我们是 LOWEST，最后收到，
  只 push 不 pop，矩阵栈失衡）；载具在 `RenderManager` 里被 redirect 掉整个 `render.doRender`，事件也不发；
- 其它 GeckoLib 模组的替换渲染器同理。

改成和姐妹项目 [FriendSwine](https://github.com/jiaotan798/friendswine)（同一个 1.7.10 移植环境，
自带该兼容）同形的做法：

| 部位 | 做法 | 为什么 |
|---|---|---|
| 挂点 | mixin `RenderManager.func_147939_a`（HEAD / `doRenderShadowAndFire` 调用前 / RETURN） | 在实体分发外层：原版渲染器、YSMU 的 GeckoLib 渲染器、其它替换渲染器都吃同一套变换 |
| 压扁 / 拉长 | GL 矩阵缩放，轴心 `y - entity.yOffset` | `RenderPlayer` 和 YSMU 都是先减 `yOffset` 再画模型；用 `y` 当轴心时压扁会把脚抬起来（FriendSwine 踩过的坑） |
| 转圈 | 改 `renderYawOffset` / `rotationYawHead`（含 prev），不直接转矩阵 | 名字牌是 billboard，转矩阵文字会跟着歪；YSMU 的模型朝向读的也是这两个字段（`GeoReplacedEntityRenderer#resolveBodyYaw` 插值 `prevRenderYawOffset→renderYawOffset`），所以一样生效 |
| 阴影 / 火焰 | 画之前 `pop + push` 换掉栈帧、还原朝向 | 阴影不能被压扁、火焰位置不能歪 |
| 生效范围 | mixin `RenderGlobal.renderEntities` 记深度，只在世界实体渲染里套 | 物品栏纸娃娃 / GUI 实体预览 / YSMU 预览不跟着抖；世界实体（开着界面时背景里也在渲染）照常抖 |

实现文件：`mc1710/DanceRender.kt`（姿势 / 矩阵 / 朝向 / 嵌套栈，全部带注释）、
`mixins/early/MixinRenderManagerDance.java`、`mixins/early/MixinRenderGlobalDance.java`
（已加进 `mixins.FMusic.json` 的 client 列表；refmap 已核对：`doRenderShadowAndFire` → `func_76979_b`、
`renderEntities` → `func_147589_a`）。原先 `ForgeBridge` 里的两个 `RenderLivingEvent` 监听已删除，
避免双重变换。

行为差异：**物品栏纸娃娃不再跟着抖**（之前会）。世界里的实体（第三人称的自己、别的玩家、
被 YSMU 替换模型的玩家 / NPC / 载具）都照常抖，名字牌不转、阴影 / 火焰不歪；没装 YSMU 时
与原实现的画面一致（矩阵 / 朝向的合成方式相同）。另：验证本改动时顺带修了
`GlStateSnapshot` 里 `glGetInteger(GL_SCISSOR_BOX, IntBuffer)` 只给 4 个元素的缓冲区导致的崩溃
（LWJGL2 对 `IntBuffer` 版本统一要求 ≥16 个元素，见 3.13 下方的 `utils/render/GlStateSnapshot.kt` 注释）。

## 四、Angelica / 光影兼容

GTNH 环境下 `runClient` 会加载 [Angelica](https://github.com/GTNewHorizons/Angelica)（1.7.10 的渲染层：
GL 3.3 core profile + Java 端固定管线模拟 + Iris 光影 + 自带字体系统）。移植时针对性核对过：

- **立即模式（`glBegin`/`glVertex2f`）**：Angelica 用 RFB 字节码重定向把所有 mod 的
  `org.lwjgl.opengl.GL11` 调用改写到 `com.gtnewhorizons.angelica.glsm.*`，由它记录后走模拟绘制，能用。
  没有 Angelica 时 LWJGL2 原生支持；只装 lwjgl3ify 时由 `lwjgl2-compat` 提供。
- **自建 GLSL**：Angelica 的 `CompatShaderTransformer` 会把兼容 profile 的内建
  （`gl_Vertex`、`gl_Color`、`gl_MultiTexCoord0`、`gl_TexCoord[]`、`gl_ModelView*Matrix`、`gl_FragColor`、`texture2D`）
  改写成 core profile 版本，**所以着色器源码保持上游原样，不要改写**。
  `ShaderManager.preDraw` 检测到绑定的不是 Iris 核心着色器时会跳过 FFP 模拟、保留我们的 program。
- **字体**：上游 UI 的拉丁字符走自建 Inter 图集 + 自建 shader，中日韩走自建 CJK 图集，
  两者都**不经过 `FontRenderer`**，所以和 Angelica 的字体改造（`FontStrategist` / `BatchingFontRenderer` /
  MCPatcher HD font）互不干扰。上游把 CJK 段延后到玻璃 UI 画完再统一 flush 的时序必须保留，否则会和
  原版字体批处理的出现层次错乱。
- **状态保存**：`utils/render/GlStateSnapshot` / `GlCaps` 是兼容关键，绘制前后必须完整保存恢复
  program、纹理单元、blend、矩阵模式、scissor 等状态，避免和 Angelica 的状态缓存打架。

### 待实机验证清单（当前仅代码级核对，未跑过实机）

1. 无 Angelica / 有 Angelica / 有 Angelica + Iris 光影，三种环境下分别确认：
   播放器界面能打开、玻璃模糊正常（不是亮粉色自采样累积）、字体无缺字、3D 歌词可见。
2. 光影开启时 3D 歌词的绘制时机（上游在 1.8.9 也只是把入口放在 `RenderWorldLastEvent`，
   并注明「尚需 OptiFine 实机验证」）。
3. 玻璃背景抓取（`GlassBackdrop`）在主菜单打开播放器时是否正常（没有世界可采样时的退化路径）。
4. 切换维度、重新加载光影后歌词/玻璃是否恢复。
5. 两套播放系统的互斥是否生效（服务端点歌与本地播放器交替触发）。
6. **均衡器**：面板里 10 个滑条、预设、自动前级显示都对；开着「低音增强」这类预设听感正常且不破音；
   面板打开时鼠标在面板上不会同时悬停到下面的列表行（`Ui.popup` 那段）。
7. **变速变调**：0.5x / 2x 下**进度条和歌词是否跟着走**（这一条最容易翻车，靠 `anchors` 映射）；
   变速后拖进度条、切歌、插蓝牙耳机（输出重开）之后进度是否还对；「音调跟着速度」打开时音色是否合理。
8. **按歌记住**：A 歌调成 1.5x + 低音增强 → 打开「按歌记住」→ 切到 B 歌（应回到全局设置）→
   切回 A 歌（应沿用 A 的设置）；重启游戏后 `config/fmusic/dango/sound_tuning.json` 读回来仍然生效。
9. **显示其它播放器**（Windows 10+）：设置里打开后，用 Spotify / Apple Music / 网易云桌面版放歌，
   FMusic 没在放歌时播放栏和 HUD 是否显示它、歌词偏移（默认 0，可 -3000~3000 ms）是否够用、
   播放栏的暂停/上一首/下一首/拖进度是否真的转给了那个 App；关掉开关后 PowerShell 进程有没有退出
   （任务管理器里看 `powershell.exe`）；FMusic 自己开始放歌时应立刻切回自己这首歌。
10. **加到歌单**（o.o）：播放栏红心旁边的「＋」能弹出当前音源的自建歌单（**要登录**）、滚轮能翻、
    点一个之后提示「已添加到 xxx」；再点同一个歌单应提示「已经在 xxx 里了」（网易云是 `502` 那条路）；
    加完打开那个歌单能看到新加的歌。三家音源各试一次；歌单超过 8 个时右边有细滚动条。
11. **播放模式按钮**（o.o / 1.7.6）：中间左侧那个三态按钮点一下切「顺序 → 单曲循环 → 随机」
    （1.7.6 起和网易云 App 一致，原来是 顺序 → 随机 → 单曲循环），
    每次都应在播放栏弹出模式名、图标颜色跟着变；放完一首确认行为对得上（随机真的乱序、单曲循环真的重放）；
    窗口拖窄时右边的音量条应该先缩短、再整条消失，按钮不重叠。图标现在是线条画的，
    顺便确认三种模式图标（两条箭头 / 圆箭头 + 1 / 交叉箭头）在游戏里画得都对。
12. **酷狗红心**（o.o）：酷狗放歌时红心能不能点亮/取消，切歌回来状态是否记住；
    「我喜欢」歌单里能看到刚点亮的那首。
13. **本来就难触发、但值得留意**：o.o 修的「算出 NaN 就没声」——如果以后再遇到「播放中突然完全没声、
    进度还在走」，先看日志里有没有 `Environment audio: non-finite sample, reverb state cleared`；
    有就说明防御生效了（声音应该自己恢复），没有则是别的原因。
14. **环境音效湿声修复**（1.7.6，上游 #65）：开着环境音效在石头/砖头这类硬墙小房间里放低音重、
    响度大的歌（电子 / 摇滚 / 鼓多的），确认不再发闷、发颤、中途没声；换到门窗多的房间，
    早期回声延迟应该跟着变短（不会所有房间都顶在 80ms 上限）。
15. **播放栏图标像素对齐**（1.7.6）：GUI 缩放不是整数比例 / 全屏时，播放模式、均衡器、变速变调、
    加歌单几个按钮的细线应该一样粗、不发虚，上一首 / 下一首 / 队列图标视觉居中（不再偏向一边）。
16. **抖动 + YSMU 兼容**（§3.13）：装 YSMU（模型包加载正常、玩家/NPC 有替换模型）后进世界，
    打开「生物踩点」放歌：
    - 第三人称看自己 / 看别的玩家 / 看被 YSMU 替换模型的 NPC：压扁-拉长和转圈都要生效
      （之前 YSMU 玩家完全不抖）；
    - 压扁时**脚不能离地**（轴心用的是 `y - yOffset`，参考 FriendSwine 的抬脚教训）；
    - 名字牌文字不应跟着转；阴影和火焰不应被压扁 / 错位；
    - 物品栏纸娃娃 / 模型预览界面不应抖（预期不抖，这是 §3.13 的行为差异）；
    - 把 YSMU 卸掉再放一次，世界内行为应与原来一致。

### 4.1 GL 调用审计（务必保持，否则会 JVM 级崩溃）

Angelica 的 `GLSMRedirector` 会把 mod 里的 `org.lwjgl.opengl.GLxx` 调用改写到它自己的
`glsm.*` 实现上。**它表里没有的调用会直接落到真实 GL**，而在 GL 3.3 core profile 里
兼容 profile 专用的函数根本不存在，LWJGL 会在原生层直接 abort 整个 JVM
（`No context is current or a function that is not available in the current context was called`，
这个错误**无法用 try/catch 捕获**）。

实机已踩到两处，都已修正：

| 调用 | 问题 | 改法 |
|---|---|---|
| `GL13.glMultiTexCoord4f`（`GlStateSnapshot.restore`） | 重定向表只映射了 `glMultiTexCoord2f/2d/2s`（`gl13` 表），没有 4f 变体 → JVM abort | 改成 `glMultiTexCoord2f(unit, s, t)`。原版自己设光照贴图坐标也是 `glMultiTexCoord2f(lightmapTexUnit, u, v)`，语义一致 |
| `EXTFramebufferObject.glGenerateMipmapEXT`（`CjkFont`） | 能力位判据 `GLContext.getCapabilities().OpenGL30` 在 lwjgl3ify 兼容层下不可靠（报 false），于是走进 EXT 分支，Angelica 不路由 | 去掉能力位分支，统一用 core 的 `GL30.glGenerateMipmap`（`UiFont` 本来就这么写） |

**新增 GL 调用时请先跑一遍这个审计**（脚本思路）：
从 `GLSMRedirector.java` 抽出所有 `.add("名字")` / `.add("别名","目标")` 得到一个「已映射符号集」，
再把 `src/main/kotlin/com/Lilith/FMusic/dango/**` 里所有 `GLxx.<名字>`、`OpenGlHelper.<名字>` 的调用
逐条比对；出现在「未映射」里的就换成 core 等价物或者改用已映射的变体。
当前状态：**72 个调用点全部命中映射表**（`OpenGlHelper.defaultTexUnit` / `lightmapTexUnit` 是静态 int
字段不是调用，属正常例外）。

另外 `LegacyGfx.guarded` 现在对「状态快照 / 绘制 / 还原」三段都兜了异常并只记一次日志：
渲染层是纯装饰性的，任何失败都不该把游戏带崩（注意这只挡得住异常，挡不住上面那种原生 abort）。

### 4.2 已经做过的验证（剩下的都要进游戏点）

**1.7.5 那一轮：**

- `gradlew build` 全绿（含 checkstyle / reobf / shadowJar），成品 jar 里有新类，
  `assets/fmusicdango/system-media.ps1` 也在包里。
- 把移植过来的 DSP 直接拉出来跑（不进游戏）：`Equalizer` 的低频预设确实抬了低频、自动前级
  = -peakDb、软限幅以下完全直通、连跑 200 块不发散；`SoundStretcher` 在 1.25x / 0.5x 下
  输出时长误差 < 1%、只变调时时长不变、两个参数都是 1 时真 bypass；`SoundTuning` 的钳位 / 预设匹配 /
  `speedLocked=false` 都对；`SystemMedia.supported` 在这台 Windows 10 上为 true、没打开时
  `track` 为 null。
- `system-media.ps1` 按 `SystemMedia` 完全相同的路径跑通了一次（从资源取出 → UTF-16LE Base64 →
  `-EncodedCommand`），能正常吐出 `{}`（当时没有别的播放器在放）。
- 与上游 `v1.7.5` 的逐文件比对：把本项目文件的包名归一化回 `me.sorihiko.dango` 后，
  `Equalizer` / `SoundStretcher` / `EqPanel` / `TunePanel` / `Ui` / `SongCardModule` 与上游**完全一致**；
  其余文件的差异只剩「一起听没移植」「FMusic 自己的默认值与缓冲」「配置路径」这些本移植层老早就有、
  写在本文件里的改动。

**`o.o` 那一轮：**

- `gradlew build` 全绿（checkstyle / reobf / shadowJar 都过了），jar 里能看到 `AddPanel` 等新类。
- 与上游 `7d1609d` 逐文件比对（包名归一化后）：`Equalizer` / `EnvironmentAudio` / `LyricsProvider` /
  `NeteaseLyrics` / `MusicSources` / `NeteaseTrack` / `QqCatalog` / `QqMusicSource` / `KugouCatalog` /
  `KugouMusicSource` / `AddPanel` **逐字节一致**；`PlayerBar` 只少上游那 15 行一起听提示，
  `Library` 只差 `Page.PARTY` 一行，`RoomAcoustics` 只多本地那段「站着不动不重算」,
  `NeteaseApi` 只差注释里的配置路径，`MusicScreen` / `NeteaseMusicModule` 只差本移植层原有的差异。
- `LyricsProvider.repairYrc` 用合成歌词直接跑了 5 组用例（不进游戏）：整体 +60s 跑偏 → 前 3 句留逐字、
  之后走 LRC 且时间单调；时间本来就对 → 原样返回同一个列表；只连续跑偏 2 句 → 不动；
  没有 LRC → 不动；夹一行 `Piano : Bob` → 名单行不参与判断但仍保留。全部通过。
- `EnvironmentAudio` / `Equalizer` 的 NaN 防御是「本来就难触发」的兜底，只做了代码级核对：
  参数非有限 → `restoreParams()`；采样非有限 → 该帧状态清零 + 10 秒一次的日志；
  均衡器段状态清零后下一块继续有声音（`DspSmoke` 里的 24 项回归仍然全过）。
- 还没验证的：上面清单 6~13 那些**只能在游戏里点**（面板交互、变速后的进度/歌词、按歌记住跨重启、
  真的接一个 Spotify 试播放控制、三家音源各加一次歌单、酷狗红心、播放模式按钮的窄窗口表现）。

## 六、性能：为什么本地播放器比服务端点歌"更吃资源"

两套播放系统的音频架构完全不同，这也是"DangoMusic 播放时卡、点歌时不卡"的根本原因：

| | 服务端点歌（FMusic 原有） | 内置本地播放器（移植自 DangoMusic） |
|---|---|---|
| 数据来源 | 服务端推 URL，客户端边下边播 | 整首下载到内存 `byte[]` |
| 解码 | 解码后入队到 OpenAL | 独立线程解码成 PCM |
| 出声 | 游戏自己的 OpenAL（C 层混音） | Java Sound `SourceDataLine` |
| 播放位置 | `alGetSourcef`，无锁 | `getMicrosecondPosition()`，和 `write()` 抢锁 |
| 附加 DSP | 无 | 音量归一化、房间混响、鼓点检测 |

单机下客户端和内置服务器**共用一个 JVM**，所以上面任何一项抢 CPU / 抢锁都会同时拖慢客户端
和服务器 tick（日志表现为 `Can't keep up! ... skipping N tick(s)` 和 `Clientside chunk ticking took N ms`）。

已经处理掉的问题：

1. **采样环境信息的白工**（bug）：`NeteaseMusicModule.onTick` 里每 tick 都会求一次
   `platform.environment`（84–168 次 `world.getBlock` + 天空/下雨查询），但结果在环境音效关着时
   会被 `EnvironmentAudio.process` 直接丢弃。现在关掉开关就完全不采样。
2. **环境音效默认关**：它是唯一会持续烧 CPU 的模块（游戏线程 5 次/秒发射线读方块 + 解码线程
   逐采样滤波混响）。功能保留，想要的人在播放器设置里打开。
3. **站着不动不再重算房间混响**：`RoomAcoustics` 记住上次测量的方块坐标，没挪窝就跳过整轮射线。
4. **声卡缓冲 0.25 秒 → 1 秒**：原来太紧，解码线程被 GC 或游戏线程挤一下就 underrun，
   听感是"播放卡一下 / 爆音"。音乐不需要低延迟。
5. **播放位置改由解码线程采样 + 渲染线程插值**：渲染线程每帧要读好几次位置（3D 歌词、Song card、
   灵动岛、进度条），原来每次都直接问声卡，和解码线程阻塞中的 `write()` 抢同一把锁，
   一旦被挡住整个渲染线程就卡住。现在只有解码线程碰声卡，渲染线程只读两个 `@Volatile` 字段
   并按经过时间插值（间隔上限 200ms，不会飘）。

如果仍然卡，先确认是哪种卡：

- **画面 / tick 卡（FPS 掉、服务器 Can't keep up）** → 在播放器设置里把 `Environment audio`、
  `Volume normalization`（会整段解码前 15 秒）、`Dance` 依次关掉，看是哪个造成的。
- **声音本身卡（断音、爆音、忽快忽慢）** → 属于 Java Sound 侧的 underrun 或设备问题，
  把 `-Ddangomusic.openal=true` 加到 JVM 参数里可以强制走游戏 OpenAL 输出做对照。
- **只在切换歌曲时卡** → 是整首下载 + 解密/分析的开销，和播放无关。

## 七、右键「发送到点歌系统」：两套播放系统之间唯一的桥

**这不是上游移植，是本模组自己加的功能**：把移植过来的客户端播放器和 FMusic 原有的服务端点歌系统接起来。
上游 DangoMusic 没有服务端点歌系统，所以这块和上游无关，以后同步上游时不会冲突。

### 7.1 行为

- 在播放器的**歌曲列表行**上右键（歌单 / 搜索结果 / 队列 / 歌手页热门歌曲，都走 `TracksPage.drawTrackList`）
  → 弹出液态玻璃右键菜单（`musicui/ContextMenu.kt`，通用组件，以后可以往里加项），目前只有一项
  「发送到点歌系统」。
- 点了之后按「**音源 + 歌曲 ID**」把这一首直接加进服务端点歌队列，等价于 `/music <音源> <歌曲ID>`：
  权限、经济、禁歌、禁人、重复、队列上限、玩家点歌上限、`MusicAddEvent` 全都照走。
- 右键**其它地方**保持原来的「返回上一页」（播放栏区域也排除，免得弹出它底下那首歌的菜单）。
- 结果在**播放栏临时状态条**提示（成功「已点歌：歌名 - 歌手」，失败显示具体原因，共 19 条文案）。

### 7.2 音源与 ID 的对应

DangoMusic 的歌曲模型和点歌系统的 `IMusicApi` 对得上，不需要按歌名重新搜：

| 播放器音源 | 点歌系统 apiId | 传过去的 ID | 校验 |
|---|---|---|---|
| `MusicSource.NETEASE` | `netapi` | `nativeId`（数字歌曲 ID） | `checkId` = 纯数字 |
| `MusicSource.QQ` | `qqmusic` | `nativeId`（songmid） | 10~32 位字母数字 |
| `MusicSource.KUGOU` | `kugou` | `nativeId`（32 位 hash） | 32 位十六进制 |

映射在 `music/PointSongBridge.kt` 的 `apiId()` 里；对不上的（比如「显示其它播放器」的 `smtc:` 占位歌）
菜单项直接置灰。

### 7.3 网络：独立通道 `fmusic:req`

不能走老的 `fmusic:channel`：那是服务端→客户端的 `CommandType` 序号协议，混进新消息老版本会解析出乱码。
所以新开一条双向通道，自带协议版本号（见 `java/com/Lilith/FMusic/network/PointSongChannel.java`）：

| 消息 | 方向 | 载荷 |
|---|---|---|
| `TYPE_FEATURE` | 服务端 → 客户端 | 支不支持（进服后 1 秒发一次） |
| `TYPE_REQUEST` | 客户端 → 服务端 | apiId + 歌曲 ID |
| `TYPE_RESULT` | 服务端 → 客户端 | 状态码 + 歌名 + 作者 |

要点（改这块之前先看这几条）：

- **通道是静态单例 + `init(handler)` 幂等**：`FMusicServer.commonSetup` 在客户端和专用服务端都会跑，
  `client/FMusic.preload` 再往同一个通道对象上补注册客户端 handler。1.7.10 的
  `newEventDrivenChannel` 同名重复调用会抛异常，所以不能各建各的。
- **服务端 handler 是独立对象**（`server/network/PointSongServerHandler`），客户端也是
  （`client/network/ClientPointSongHandler`）。不能把 `@SubscribeEvent` 方法挂在旧的 `client/FMusic` 对象上：
  FML 的每个通道是独立 EventBus，但同一个对象注册进去后，旧通道的收包方法也会收到新通道的包、按旧协议解析。
- **老版本互相兼容**：老客户端/老服务端根本不注册 `fmusic:req`，FML 收到未知通道的包会直接交给原版
  handler 忽略，不会踢人。新客户端连老服务端时收不到能力包 → `supported = false` → 点了只提示
  「服务器不支持（FMusic 版本过旧）」；断线时会清掉能力位，避免先连新服再连老服时误判。
- **线程分层**（服务端）：网络线程只解析包 + 定位玩家 → `FMusic.side.runTask` 在 tick 线程做权限/经济
  check → `SaveTask.task` 在后台线程跑 `FMusicApi.addMusicSync`（要发 HTTP）→ 再回 tick 线程发包。
  结果包会按名字重新取当前在线的玩家（解析要几秒，期间可能重生）。
- **经济顺序**：先 `check`，入队成功后才 `cost`；扣款失败只记 warn、不回滚（歌已经在队列里了）。
  和现有 `/music` 命令的扣费时机略有不同，但不会出现「扣了钱没点成歌」。

### 7.4 验证

- `gradlew build` 全绿（spotless / checkstyle / reobf / shadowJar）；`gradlew compileKotlin -PskipPlatform`
  也能过（桥放在 `dango/music`，没有从核心 UI 反向引用 `mc1710` 平台层）。
- 协议层不进游戏单独跑了 17 项（`tmp/smoke/PointSongProtocolSmoke.java`）：三种消息正常解析、
  中文歌名/作者、坏版本、未知类型、空包/截断/负长度/超大长度、FML 出站 `payload.array()` 尾部补 0 的兼容，
  全部通过。**这里抓到过一个真 bug**：结果包缺字段时 `read()` 会返回带 null 字段的消息，
  Kotlin 侧非空参数会抛异常把结果包丢掉——已改成三个字段缺一即当坏包。
- 待实机验证：右键菜单的弹出位置/悬停/关闭、列表行命中（含页面切换动画期间）、真实点歌成功/失败、
  权限与经济扣费、专用服务器 + 两个客户端、老版本混连。

## 五、许可与致谢

DangoMusic 以 **GPL-3.0** 发布，本项目以 **AGPL-3.0** 发布，两者兼容（AGPL-3.0 更严格）。
移植代码保留原始版权与注释；第三方资源按其原许可随附在 `src/main/resources/META-INF/licenses/`：

| 资源 / 依赖 | 来源 | 许可 |
|---|---|---|
| Inter 字体 | [rsms/inter](https://github.com/rsms/inter) | OFL-1.1 |
| JLayer | javazoom | LGPL-2.1 |
| JOrbis / Jogg | jcraft | LGPL-2.1 |
| ZXing（二维码） | zxing | Apache-2.0 |
| DangoMusic | [Sorihiko/DangoMusic](https://github.com/Sorihiko/DangoMusic) | GPL-3.0 |
