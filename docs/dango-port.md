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
| 生物踩点 Dance | `common/core/.../music/Dance.kt` | 按 BPM 网格抖动生物 |
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

### 4.2 1.7.5 这块已经做过的验证（剩下的都要进游戏点）

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
- 还没验证的：上面清单 6~9 那四条**只能在游戏里点**（面板交互、变速后的进度/歌词、按歌记住跨重启、
  真的接一个 Spotify 试播放控制）。

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
