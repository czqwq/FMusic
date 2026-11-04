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

两个系统**同时只允许一个出声**，由 `mc1710/DangoAudioArbiter.kt` 仲裁（一方开始播放时另一方自动让路）。

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

- **删掉 3 个死着色器的注册**：`GlowDownsampleShader` / `GlowUpsampleShader` / `GlowCompositeShader`
  引用 `shaders/outline/base.vert`，而这个文件在上游 legacy 和 modern 两套里都不存在（上游自己的 bug），
  结果是每次启动刷 3 条加载失败日志。实测这三个从未被使用（真正的辉光是 `DeepGlow` 里的内联 GLSL），
  所以不再注册；文件保留在源码里，上游补上 `base.vert` 并真的用起来时把那三行加回来即可。
- **互斥仲裁完全收在移植层里**：`DangoAudioArbiter.tick()` 同时轮询两边的「是否正在出声」，
  靠状态跳变识别「刚开播」，因此**不需要改 FMusic 原有的 `FMusicPlayer` / `FMusicCore` 播放路径**。
  唯一对我们老代码的改动是给 `FMusicCore` 加了一个公开的 `hardStop()`（内部就是原来的私有
  `stopPlaying()`），避免用 STOP 指令时「结尾附近优雅收尾」导致两首歌短暂叠音。
- **1.7.10 缺的信息降级处理**：`fps` 改成按两次取值的间隔估算（1.7.10 没有 `Minecraft.getDebugFPS()`，
  而生产环境是 SRG 名，字符串反射拿不到），`pingMs` 恒 0，`isRainingAt` 用最接近的
  `world.canLightningStrikeAt(x,y,z)` 近似。这三处只影响显示/环境音效的细微差别，代码里都写了注释。
- **音频后端**：桌面 Java 自带 `jsound`，走 Java Sound（`SourceDataLine`）；`openAudioOutput`
  （游戏 OpenAL）在 1.7.10 平台层没有实现、返回 null，只在 Java Sound 打不开时才会被用到，
  这是上游的正常分支。1.7.10 桌面端不受影响。

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
