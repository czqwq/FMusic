 plugins {
    id("com.github.ElytraServers.elytra-conventions") version "v1.1.1"
    id("com.gtnewhorizons.gtnhconvention")
    // Kotlin: 移植自 DangoMusic 的客户端播放器代码是 Kotlin 写的，这里只为它提供编译能力。
    // 不写版本号：GTNH/Elytra 的约定插件链上已经有 Kotlin Gradle 插件，指定版本会被 Gradle 拒绝。
    kotlin("jvm")
 }

kotlin {
    compilerOptions {
        // MC 1.7.10 运行在 Java 8 上，Kotlin 也必须产出 Java 8 字节码
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

// 移植代码保持 DangoMusic 上游的原始排版，交给 spotless 会自动改写成另一种风格，
// 反而让以后同步上游的 diff 变得没法看。这里把移植过来的文件排除在 spotless 之外。
spotless {
    kotlin {
        targetExclude("src/main/kotlin/com/Lilith/FMusic/dango/**")
    }
    java {
        targetExclude(
            "src/main/java/com/Lilith/FMusic/dango/**",
            // DangoVorbis 是移植过来的，为了能访问 package-private 的 vd/decode_ready 才放进这个包
            "src/main/java/com/Lilith/FMusic/client/core/player/decoder/ogg/jcraft/jorbis/DangoVorbis.java",
        )
    }
}

// 【临时脚手架】-PskipPlatform：只编译与 Minecraft 版本无关的那部分移植代码（core/ui），
// 用来在 1.7.10 平台层重写期间单独验证移植主体。平台层已完成，这段可以删掉。
if (project.hasProperty("skipPlatform")) {
    kotlin.sourceSets.getByName("main").kotlin.exclude(
        "com/Lilith/FMusic/dango/mc1710/**",
        "com/Lilith/FMusic/dango/font/CjkFont.kt",
        "com/Lilith/FMusic/dango/font/UiFont.kt",
        "com/Lilith/FMusic/dango/font/FontManager.kt",
        "com/Lilith/FMusic/dango/shader/**",
        "com/Lilith/FMusic/dango/utils/render/**",
        "com/Lilith/FMusic/dango/ui/GlassRenderContext.kt",
        "com/Lilith/FMusic/dango/ui/glass/**",
        "com/Lilith/FMusic/dango/lyrics/DeepGlow.kt",
        "com/Lilith/FMusic/dango/events/ShadersLinkedEvent.kt",
    )
}

dependencies {
    // Apache HttpClient 5 (client/server 核心的 HTTP 与音乐流下载)
    // 注意: httpclient5 5.6+ (DefaultHttpClientConnectionOperator) 与 httpcore5 5.4+ (ReflectionUtils)
    // 会引用 jdk.net.ExtendedSocketOptions.TCP_KEEPIDLE*; 旧版 Java 8 (如 1.8.0_51) 没有这些字段,
    // 静态初始化会抛 NoSuchFieldError, 让整个游戏/服务器启动崩溃。5.5 + httpcore5 5.3.6 无此引用。
    shadowImplementation("org.apache.httpcomponents.client5:httpclient5:5.5")
    shadowImplementation("org.apache.httpcomponents.core5:httpcore5:5.3.6")
    shadowImplementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.6")

    // Adventure (服务端 MiniMessage 消息与 JSON 序列化)
    shadowImplementation("net.kyori:adventure-text-minimessage:4.26.1")
    shadowImplementation("net.kyori:adventure-api:4.26.1")
    shadowImplementation("net.kyori:adventure-text-serializer-gson:4.8.1")
    shadowImplementation("net.kyori:adventure-text-serializer-legacy:4.8.1")
    shadowImplementation("net.kyori:adventure-text-serializer-plain:4.8.1")
    shadowImplementation("net.kyori:adventure-key:4.8.1")

    // Kotlin 运行时 (移植自 DangoMusic 的播放器/UI 代码需要), 会被 relocate 进 shadow 包
    shadowImplementation(kotlin("stdlib"))

    // 扫码登录 (网易云 / QQ音乐 / 酷狗 的二维码)
    shadowImplementation("com.google.zxing:core:3.5.3")

    // DangoMusic 播放器的 MP3 解码。
    // 我们自己的 client/core/player/decoder/mp3 是同一份 jlayer 的魔改移植（构造器绑定了
    // FMusicPlayer 网络流、Decoder 被合并进 Mp3Decoder、LayerXDecoder 包私有），没有 byte[] 入口，
    // 而 DangoMusic 的播放链路是「byte[] → 分块解码」。为了不动那份已经在跑的代码，
    // 这里额外 shade 一份未改动的 jlayer 给它用（会被 relocate 到 shadow 包，互不影响）。
    shadowImplementation("javazoom:jlayer:1.0.1")
 }
