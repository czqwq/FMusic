# DangoMusic 版本对比：GTNH 移植版 / FMusic 内置移植 / 上游

本文整理 2026-11 对三个 DangoMusic 代码快照的只读对比。**对比过程没有修改这三份代码**，结论来自文件清单、git 历史、关键字检索与三方文档交叉核对，**没有进游戏实测**。

- 我们自己的移植细节见 [dango-port.md](dango-port.md)（移植范围、路径映射、1.7.10 适配、待实测清单）。
- 本文引用的 `tmp/` 目录是本地参考源码，**未纳入版本库**（`.gitignore` 里有 `/tmp/`），其中的链接只在本地有效。

## 一、对比对象与版本快照

| 代号 | 路径 | 当前快照 | 上游基线 | 许可 |
|---|---|---|---|---|
| **GTNH 版** | [tmp/DangoMusic-GTNH](../tmp/DangoMusic-GTNH) | 分支 `port/gtnh-1.7.10` @ `d201b93`（2026-11-17），`build.gradle.kts` 版本 **0.5.1-dev.1**（README 正式发行仍是 0.1.0 / dev.5.38 功能），2026-11-05 起共 124 个提交 | **v1.7.3**（`1e82730`，2026-11-04） | GPL-3.0 |
| **我们（FMusic）** | `src/main/kotlin/com/Lilith/FMusic/dango` @ `2a57772` "Dango 1.7.6 port"（2026-11-17），FMusic 版本 2.8 | 包 `com.Lilith.FMusic.dango`，资源域 `fmusicdango`，配置 `config/fmusic/dango/` | **v1.7.6**（`149933a`，2026-11-15）+ `o.o`（`7d1609d`） | AGPL-3.0 |
| **上游** | [tmp/DangoMusic-main](../tmp/DangoMusic-main) | `main` @ `149933a` = tag **v1.7.6**（2026-11-15） | — | GPL-3.0 |

三者关系：GTNH 版和我们都是「DangoMusic 播放器 → Minecraft 1.7.10」的**平行移植**。GTNH 版从 v1.7.3 起家、在自己仓库里长出了一整套新功能，形态是**独立 mod**（`dangomusicgtnh`）；我们是从 v1.7.6 移植、并把上游 1.7.4 / 1.7.5 / 1.7.6 / o.o 全部落地，形态是 **FMusic 的内置子模块**。

代码规模（`src` + `core`/`dango` 主源码，未计文档与工具脚本）：

| 快照 | 文件数 | 行数 | 说明 |
|---|---|---|---|
| GTNH 版 | 168（155 `.kt` + 13 `.java`） | 27,681 | 含平台层、自研功能、`core` 子工程；另有 `tools/` 下 15 个验证用 Java 文件约 900 行 |
| 我们的移植 | 137（`.kt`） | 20,661 | 仅 dango 包，不含 FMusic 原有代码 |
| 上游 1.8.9 目标 | 150（`.kt` + `.java`） | 21,670 | `common/core` + `common/ui` + `legacy`；不含 modern/modern26 |

## 二、一句话结论

1. **不是同一条路线**：GTNH 版 = 独立、功能爆量的「音乐社交 mod」（Apple Music + 附近共享 + 听歌房 + 评论 + 大歌词页 + KTV + 驾驶舱歌词 + 续播/预取）；我们 = 「上游 v1.7.6 功能完整同步 + 深度融入 FMusic 点歌生态」（双播放系统仲裁、右键点歌、音质缓存、IME、悬浮提示、窗口操作）。
2. **版本错位是最大差异来源**：我们的基线 v1.7.6 比 GTNH 版的 v1.7.3 新，所以 1.7.5 / 1.7.6 的均衡器、变速变调、按歌记住、显示其它播放器、加到歌单、全音源音质选择、逐字歌词漂移修复这些**我们有、GTNH 版没有**。
3. 反过来，GTNH 版自研的**多人（附近/听歌房）、评论、大歌词页、KTV、音译、驾驶舱歌词、Apple Music、续播/预取**我们完全没有。

## 三、功能归属总览

✅ 有；❌ 没有；△ 部分 / 条件性；— 不适用或另有实现。

| 功能 | 上游 v1.7.6 | GTNH 版 | FMusic 内置 |
|---|---|---|---|
| 液态玻璃播放器、列表/封面墙、歌手页、云盘、3D 歌词、Song card、灵动岛 | ✅ | ✅ | ✅ |
| 网易云 / QQ / 酷狗 三音源 | ✅ | ❌ 酷狗被移除，改为网易云 / QQ / Apple | ✅ |
| 一起听（Firebase + 局域网） | ✅ | ❌ 替换为自家服务端听歌房 | ❌ 明确不移植 |
| Apple Music（实验性） | ❌ | ✅ | ❌ |
| 附近共享 / 听歌房 | ❌ | ✅ | ❌ |
| 网易云歌曲评论（只读） | ❌ | ✅ | ❌ |
| 大歌词页 / KTV / 音译 / 驾驶舱歌词 | ❌ | ✅ | ❌ |
| 多行 3D 歌词（句数、避障、保持屏内） | △ 单句为主 | ✅ 1–8 句 | ❌ 单句 |
| 均衡器 / 变速变调 / 按歌记住 / 显示其它播放器 | ✅ 1.7.5 | ❌ | ✅ |
| 加到歌单 / 酷狗红心 / 三态播放模式按钮 | ✅ 1.7.6 + o.o | ❌ | ✅ |
| 音质选择 | ✅ 三音源 3 档 | ✅ 仅网易云 6 档（自研） | ✅ 三音源 3 档 + 本地缓存 |
| 原版 BGM 让路（放歌时暂停原版音乐） | ✅ 1.7.4 | ❌ | ✅ |
| 窗口拖动 / 缩放 | ✅ | ❌ 固定布局 | ✅ |
| 续播队列 / 可调音频缓冲 / 下一首预取 | ❌ | ✅ | ❌ |
| 播放器窗口内输入法（中文） | △ 1.8.9 只能复制粘贴 | ✅ SDL 文本输入 | ✅ lwjgl3ify + 纯 LWJGL2 回退 + 兼容 InputFix |
| 点歌系统桥（右键「发送到点歌系统」） | ❌ | ❌ | ✅ |
| 服务端组件 | ❌ | ✅ 可选同 JAR 服务端 | — FMusic 本体已有服务端点歌 |

## 四、表一：GTNH 版 vs 我们模组的 DangoMusic

| 对比项 | DangoMusic-GTNH（独立 mod） | FMusic 内置 Dango |
|---|---|---|
| 形态 / 定位 | 只做播放器的独立 mod，可装进任意 GTNH 整合包 | FMusic 内嵌播放器，与 FMusic 原有服务端点歌系统**双轨并存** |
| 播放系统关系 | 单一播放器 | 两套系统同时只许一个出声：`DangoAudioArbiter` 仲裁，**Dango 绝对优先**；点歌侧只静音不暂停（保持与服务端同步），Dango 停了自动恢复 |
| 上游基线 | v1.7.3 + 大量自研功能 | v1.7.6 + o.o，上游 1.7.4 / 1.7.5 / 1.7.6 功能全量落地 |
| 音源 | 网易云 / QQ / **Apple Music**（酷狗已移除；Apple 占 config id 3 兼容旧配置） | 网易云 / QQ / **酷狗**（无 Apple） |
| 多人 | **附近共享 + 听歌房/音乐群组**：可选服务端组件、同一 JAR、Hello 握手、`/dangomusic group ...` 命令、`server.cfg`、`groups.json` 持久化 | 无多人功能；与点歌系统的互斥是唯一"协同" |
| 上游「一起听」(Firebase) | 移除，改成自家服务端房间协议 | 明确不移植（Party/Firebase 代码整体未搬） |
| Apple Music | **有**：JAR 内置 Windows WebView2/.NET8 后台播放组件、官方登录、游戏内搜索/媒体库/队列/电台 | 无 |
| 评论 / 大歌词页 / KTV / 音译 / 驾驶舱歌词 | **全有**（详见第五节） | 全无 |
| 3D 歌词 | 多行（1–8 句）、上滚淡出、避障、保持屏内、极端视角对齐、翻译/音译、KTV | 单句 3D：逐字、发光、抖动、文闪、翻译、拆句；另有上游 o.o 的逐字歌词漂移修复 |
| 播放器界面 | 经典 / 连续圆角**两种风格**、矢量图标、512px 高清封面、玻璃音量/模式弹窗、**窗口固定布局** | 上游 UI + **窗口拖动/缩放**（最小 880×480、按屏幕比例存位置）、进度条拖动松手才 seek、悬浮提示、分层玻璃播放栏、音质徽章 |
| DSP / 音效 | 音量均衡、环境音/房间回声、软限幅、浮点 DSP、原音模式、FLAC 抖动 | 上游全有，另外还有：**10 段均衡器**、**变速变调**（WSOLA / ±12 半音 / 音调跟速度）、**按歌记住**、**显示其它播放器**（Windows SMTC）、1.7.6 湿声修复、NaN 防御 |
| 音质与缓存 | 网易云 **6 档**（标准/较高/极高/无损/Hi-Res/超清母带，默认无损）+ 网易云磁盘缓存（`%LOCALAPPDATA%`、2 GiB、按会话+音质、LRU） | **三音源 3 档**（标准/高音质/无损）+ 本地音频缓存面板（1 GiB / 7 天 / 单曲 128 MiB，可换目录、可清空；按源+账号+歌+实际音质隔离，低档回退不冒充所选档） |
| 播放体验 | 续播队列（`resume-queue.json` / `resume-state.json`）、可调音频缓冲 400ms、下一首预取、淡入淡出 | 输出三档（自动 / Java Sound / 游戏 OpenAL）+ 掉线自愈（换设备、F3+T 后自动续播）+ 原版 BGM 让路；**无续播 / 预取 / 可调缓冲** |
| 歌单 / 收藏 | 只有网易云红心 | **加到歌单**（三家）+ 网易云/酷狗红心 + 播放模式三态按钮（顺序→单曲→随机） |
| 与其它系统集成 | 独立 mod，基本无外部集成（自带 Custom Main Menu 判断） | **右键「发送到点歌系统」**（新增 `fmusic:req` 通道，走权限/经济/禁歌/队列校验）、Custom Main Menu 适配、更新检查改查 FMusic release、聊天「点击复制」mixin |
| 配置 / 账号 | `config/dangomusicgtnh/`；账号放实例外 `%LOCALAPPDATA%/DangoMusicGTNH/accounts/<实例>`，Windows 用 DPAPI 加密 | `config/fmusic/dango/`（另含 `sound_tuning.json`、`audio_cache`），登录凭证同目录 |
| 服务端职责 | 可选服务端组件：附近/房间同步、群组持久化、L3 中转（默认关）、未装 mod 的客户端也能进服 | 无 dango 服务端；点歌系统的服务端逻辑由 FMusic 本体提供 |
| 输入法 | lwjgl3ify SDL 文本输入 + 设置页「单按数字键录两次」修复 | lwjgl3ify 反射调用 + 纯 LWJGL2 回退 + 与 InputFix 共存（三种环境都覆盖，见 [dango-port.md](dango-port.md) §3.11） |
| 渲染健壮性 | `GlStateManager` shim + `graphicsReady` 门控 + **玻璃渲染失败时降级到普通 `PlayerScreen`** | GL 调用审计（对照 Angelica 重定向表，72 个调用点全命中）+ `LegacyGfx.guarded` 异常兜底；无降级界面 |
| 构建 / 许可 | GTNHGradle 2.0.33 + `dotnet publish` 把 Apple 组件打进 JAR + shadow relocate；GPL-3.0 | Elytra conventions + Kotlin（jvmTarget 1.8）+ shadow relocate（Kotlin/JLayer/ZXing）；AGPL-3.0 |
| 更新 / 发行 | 手动 Releases（0.1.0；开发版 0.5.1-dev.1） | UpdateChecker 查 FMusic release；当前 FMusic 2.8 |

补充（我们的移植层，GTNH 版没有等价设计）：

- 双播放系统仲裁的极性：**Dango 优先、点歌永不反抢**；点歌"让路"只静音不暂停（它的播放位置要和服务器同步），Dango "让路"才暂停（纯客户端，保留进度）。
- HUD 三套并存：老 HUD / Song card / 灵动岛，默认只开老 HUD，升级后画面零变化。
- 「发送到点歌系统」是两套播放系统之间唯一的桥：只把歌送进服务端队列，不接管服务端播放。

## 五、表二：GTNH 版 vs 上游 DangoMusic

### 5.1 基础差异

| 项 | 上游 DangoMusic v1.7.6 | DangoMusic-GTNH |
|---|---|---|
| 支持平台 | MC 1.8.9（Forge）+ 1.20.1 / 1.21.1 / 1.21.10 / 1.21.11 / 26.1.2 / 26.2 / 26.3（Fabric / Forge / NeoForge，多套 JAR） | 仅 MC **1.7.10 Forge**（GTNH 2.9.0） |
| 构建 / 运行基线 | JDK 8 / 21 / 25，`build-all.bat`，composite build | GTNHGradle 2.0.33，JDK 25 跑 Gradle、JDK 8 编译；玩家侧基线 Java 25 + lwjgl3ify 3.0.42 |
| 多人 | 一起听（Firebase + 局域网经房主游戏同步） | 可选服务端组件 + 自家网络协议，**无 Firebase** |
| 音频输出 | 自动 / Java Sound / 游戏声音 三档 | **只有 Java Sound**（`Platform.openAudioOutput` 返回 null、`hasGameAudio = false`） |
| 播放器窗口 | 可拖边缘改大小、拖顶部移动 | **固定布局**（最新提交即 "keep the music player window anchored"） |
| 自动更新检查 | 有 | **移除** |
| 代码来源 | 多版本一体仓库 | 基于上游 v1.7.3 的独立派生（README 明确"非官方 GTNH 移植"） |

### 5.2 GTNH 特有 / 明显加强的功能（上游没有）

| 功能 | 内容 | 状态（据仓库文档） |
|---|---|---|
| **Apple Music（实验性）** | JAR 内置 Windows x64 WebView2/.NET8 后台播放组件（首次自动解包启动）；官方登录/会话恢复；游戏内搜索、个人媒体库/歌单、游戏队列、Apple 排行榜/新发现/广播/主页编辑刷新；Apple 大歌词页（双栏、封面取色、收藏、进度、音量、View Credits/Copy Link）；歌词用本地 LRC/YRC/TTML 或严格匹配的网易云歌词；不导出 DRM 音频/URL | 真实账号已实测出声、跳转 90 秒、重启恢复；**未**全曲/全地区/长期验收 |
| **附近共享** | 同维度默认 32 格（服务端可配 4–128），距离越远音量越低，最多 4 个 host/听众；可共享歌词投影（字体/颜色/发光/行数/翻译/音译/摆动同步） | 实验性；服务端与参与客户端都要装 |
| **听歌房 / 音乐群组** | 服务端保存房间：多人多房间、邀请/公开房、房主离线不解散、房主转让、自动回房、`/dangomusic group ...` 命令、`server.cfg` 策略；任意成员都能成为当前共享者（DJ） | 已实现，待多客户端完整回归 |
| **共享回退 L1/L2/L3** | L1 听众自己账号解析 → L2 主机短期 URL → L3 服务端中转（默认关，可限速/限大小） | L3 只实现 L3a，默认关闭 |
| **播放优先级** | 个人播放 > 听歌房 > 附近；本地一开声远端自动暂停（不卸载），本地停止后恢复；同一时刻只有一路远端 | 已实现 |
| **网易云歌曲评论** | 只读评论（总数、分页、头像/昵称/日期/换行保留、加载/空/失败/重试），大歌词页滚轮进入，设置里可关 | 已实现 |
| **大歌词页** | 独立页面：整首滚动、跟随播放/手动翻阅、逐句高亮、字号/翻译/音译/歌词对齐 ±0.5s、回到正在唱 | 已实现 |
| **KTV 逐字** | 逐字覆盖色推进、前奏/间奏/尾声标签（`[前奏 ♫~]`）、预显最多 1.5s、翻译/音译跟随覆盖、独立配色 | 已实现 |
| **音译** | 歌词页 / 3D 歌词 / 驾驶舱歌词 / 共享歌词都支持 | 已实现 |
| **驾驶舱歌词**（SideLyrics） | 屏幕边缘自动上滚的歌词列表：像素/平滑/自定义字体（含系统字体、Angelica 字体目录、`font-folders.txt`）、5 种配色、玻璃字（透出并折射游戏画面）、引导线、惯性、Alt+滚轮预习、点左键跳句、自动反色 | 700+ 行专属实现 |
| **多行 3D 歌词增强** | 1–8 句手动句数、换句上滚淡出、避让方块（中心+4 角探测，最多缩到 35%）、保持屏内、极端俯仰角对齐 | 已实现 |
| **界面双风格** | 经典 / 连续圆角两种外观；矢量图标、玻璃弹窗展开动画、横向音量胶囊+百分比、播放模式弹窗、侧栏重做、512px 高清封面、灵动岛动画开关 | 已实现 |
| **续播 / 缓冲 / 预取** | 记住队列与进度（下次启动暂停在原处，主动停止不恢复）、可调音频缓冲 400ms、下一首后台预取+响度分析、淡入淡出输出 | 已实现 |
| **网易云音质+缓存** | 6 档音质（默认无损）；完整音源磁盘缓存（`%LOCALAPPDATA%` 下、2 GiB、按会话/音质、校验后复用）；卡住下载自动续传；显示 PCM 位深/采样率/输出后端；原音模式（绕过滤波）、FLAC 抖动 | 已实现 |
| **账号隔离 / 安全** | 账号资料放实例外 `%LOCALAPPDATA%/DangoMusicGTNH/accounts/<实例>`，Windows 下用 DPAPI 加密，实例之间互不串号 | 已实现（升级需重新登录） |
| **输入与快捷键** | lwjgl3ify SDL 文本输入、中文可输入、设置页重复字符修复；独立按键分类，支持 Ctrl/Shift/Alt 组合键（经 Controlling 保存）、停止键、驾驶舱歌词预习键 | 已实现 |
| **玩家列表避让 / 降级界面** | HUD 自动避开 Tab 玩家列表；玻璃渲染器初始化失败时降级为普通 GUI 播放器 | 已实现 |
| **生物踩点改进** | 由"每拍压一下"改为连续周期弹 + 转圈速度 0.25×–4× | 已实现 |

### 5.3 GTNH 版**没有**跟进的上游功能（v1.7.4–v1.7.6 / o.o）

| 上游功能 | 上游版本 | GTNH 现状 |
|---|---|---|
| 放歌时自动暂停原版背景音乐 | 1.7.4 | **无**（全仓库无 `holdVanillaMusic` / MusicManager 相关代码） |
| 10 段均衡器（11 预设） | 1.7.5 | **无** |
| 变速变调 + 按歌记住（速度/音调/EQ） | 1.7.5 | **无** |
| 显示其它播放器（Windows SMTC，Spotify / Apple Music 等） | 1.7.5 | **无** |
| 加到歌单（网易云/QQ/酷狗） | 1.7.6 + o.o | **无**（无 `addToPlaylist` 相关实现） |
| 酷狗红心 / 红心抽象（按音源实现） | 1.7.6 + o.o | 酷狗已移除，只剩网易云红心 |
| 全音源音质选择（默认档 + 播放栏临时换 + 逐档回退） | 1.7.6 | 只有**网易云** 6 档（自研），QQ/Apple 无档位切换 |
| QQ 音乐"总是 128k"修复 | 1.7.6 | **未合入**：`QqPlayback` 仍只请求 `M500/M800`，没有 `F000` / flac 档 |
| 网易云逐字歌词漂移修复（`repairYrc`） | o.o | **未合入**（无 repair / drift 逻辑） |
| "算出 NaN 就没声"有限性防御 | o.o | **未合入**那套检查（只有零散 `isFinite`；音频侧另有自己的软限幅） |
| 环境音效湿声修复（`149933a`） | 1.7.6 | 未合入同一套改动；GTNH 用自己的"浮点 + 软限幅"修硬削波 |
| 打开自己的歌单自动刷新 | 1.7.6 | 有等价机制：`Library.stale`（5 分钟）触发 `softRefresh()` |
| 播放模式合并成单按钮 / 播放栏图标重画像素对齐 | 1.7.6 | 概念上覆盖：自己有单按钮+弹窗、全套矢量图标（实现不同） |

### 5.4 GTNH 版删掉 / 替换掉的上游东西

| 上游内容 | GTNH 的处理 |
|---|---|
| 酷狗音源（登录/歌单/云盘/歌词/播放） | **整源移除**（0.1.1-dev.12），Apple 顶到 config id 3 保持旧配置兼容 |
| Listen Together（Firebase 跨服 + 局域网经房主） | 移除，替换为**服务端听歌房/音乐群组**（不宣称支持上游 Firebase 房间） |
| "一起听"相关 UI（PartyPage、侧栏入口等） | 移除，换成 GroupPage / 多人设置标签 |
| 播放器窗口拖动/缩放 | 移除，固定默认布局 |
| 音频输出"游戏声音"档 | 未实现（仅 Java Sound） |
| 更新检查（指向上游/Firebase） | 移除，避免把上游 JAR 当 GTNH 更新 |
| SongCard 独立模块 | 合并进灵动岛（完整/简易两种布局） |
| 多平台入口与 modern/modern26/Fabric/NeoForge 代码 | 只保留 1.7.10 一套 |

## 六、结论与可借鉴文件

1. **补短**：若要把 GTNH 版独有的能力搬到我们的移植里，优先看这些文件（路径相对 `tmp/DangoMusic-GTNH/`）：
   - Apple Music：[core/.../music/AppleWebPlayer.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/music/AppleWebPlayer.kt)、[AppleMusicSource.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/music/AppleMusicSource.kt)、[BundledApplePlayer.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/music/BundledApplePlayer.kt)、[tools/apple-native-player/Program.cs](../tmp/DangoMusic-GTNH/tools/apple-native-player/Program.cs)；安装/限制见 [docs/INSTALL.md](../tmp/DangoMusic-GTNH/docs/INSTALL.md)
   - 多人：[network/NearbyServer.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/network/NearbyServer.kt)、[network/MusicGroups.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/network/MusicGroups.kt)、[core/.../multiplayer/NearbyProtocol.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/multiplayer/NearbyProtocol.kt)；设计见 [docs/NEARBY_SHARING.md](../tmp/DangoMusic-GTNH/docs/NEARBY_SHARING.md)
   - 歌词：[musicui/LyricsPage.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/musicui/LyricsPage.kt)、[module/impls/music/SideLyrics.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/module/impls/music/SideLyrics.kt)、[musicui/SongComments.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/musicui/SongComments.kt)
   - 界面/动画：[ui/UiStyle.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/ui/UiStyle.kt)、[ui/VectorIcon.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/ui/VectorIcon.kt)、[musicui/PopupLook.kt](../tmp/DangoMusic-GTNH/src/main/kotlin/me/sorihiko/dangomusicgtnh/musicui/PopupLook.kt)
   - 续播/缓存/音频：[music/ResumeStore.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/music/ResumeStore.kt)、[music/FadingAudioOutput.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/music/FadingAudioOutput.kt)、[music/NeteaseDiskCache.kt](../tmp/DangoMusic-GTNH/core/src/main/kotlin/me/sorihiko/dangomusicgtnh/music/NeteaseDiskCache.kt)
2. **反向同步**：GTNH 版目前落后上游 v1.7.4–v1.7.6 一整段。要跟进的话，直接按上游 tag 对照 [上游 1.7.4](../tmp/DangoMusic-main/docs/CHANGELOG-1.7.4.md) / [1.7.5](../tmp/DangoMusic-main/docs/CHANGELOG-1.7.5.md) / [1.7.6](../tmp/DangoMusic-main/docs/CHANGELOG-1.7.6.md) 的条目逐项落地即可；我们的移植（[dango-port.md](dango-port.md)）已经把这块做过一遍，可作参考实现。
3. **不要指望直接合并**：GTNH 版是 1.7.3 基线 + 8k 行自研改动，我们是 1.7.6 全量 + 自研集成，两边同名文件（如 `MusicPlayer.kt`、`PlayerBar.kt`、`Library.kt`）改动面都很大，跨仓库搬运应按**功能**拆，而不是按文件合并。

## 七、核对方法与证据

本次对比用到的只读手段：

1. **文件清单**：`Get-ChildItem` 递归统计三份源码的文件名、包路径、行数；对 `git ls-tree -r --name-only v1.7.3 / v1.7.6` 做上游两版本的文件名集合差（得出 1.7.4–1.7.6 新增/删除的上游文件）。
2. **功能关键字检索**：对 GTNH 与我们的源码分别 grep 关键 API/字段（`holdVanillaMusic`、`Equalizer`、`SoundStretcher`、`SystemMedia`、`addToPlaylist`、`likedState`、`repairYrc`、`isFinite`、`M500/M800/F000`、`MusicSource` 枚举、`openAudioOutput`、`dragWindow/winRect` 等），确认"有/无"。
3. **界面文案对照**：三份 `zh_cn.tsv` 的 key 集合与中文关键字统计（评论/附近/听歌房/驾驶舱/KTV/音译/音调/变速/缓存/Apple 等），用于确认用户可见功能差异。
4. **git 历史**：`git log --date=short` 列出 GTNH 版 124 个提交的主题，区分"移植适配"与"新增功能"；确认其基线是上游 v1.7.3、没有合入 v1.7.4–v1.7.6 的发布。
5. **文档交叉核对**：GTNH 版 [README](../tmp/DangoMusic-GTNH/README.md)、[CHANGELOG](../tmp/DangoMusic-GTNH/CHANGELOG.md)、[PORTING_AUDIT](../tmp/DangoMusic-GTNH/docs/PORTING_AUDIT.md)、[INSTALL](../tmp/DangoMusic-GTNH/docs/INSTALL.md)、[NEARBY_SHARING](../tmp/DangoMusic-GTNH/docs/NEARBY_SHARING.md)；上游 [README](../tmp/DangoMusic-main/README.md) 与 v1.7.4–v1.7.6 更新日志；我们的 [dango-port.md](dango-port.md) 与 [../README.md](../README.md)。

可信度提醒：

- GTNH 版的 README 功能表以 0.1.0 / dev.5.38 为准，而当前 checkout 是 **0.5.1-dev.1** 且带若干未发布提交（Apple 电台、评论、大歌词页等文档略滞后）；Apple 与多人被作者本人标为**实验性**。
- 我们这边的 [dango-port.md](dango-port.md) 明确标注了哪些是代码级核对、哪些还没实机验证；本文沿用了同样的边界：**本文没有新增任何游戏内实测**。
- 上游仍在快速迭代（v1.7.6 之后还有提交），GTNH 版和我们的移植都可能继续漂移，本文结论只对上面列出的快照成立。
