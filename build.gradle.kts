 plugins {
    id("com.github.ElytraServers.elytra-conventions") version "v1.1.1"
    id("com.gtnewhorizons.gtnhconvention")
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
}
