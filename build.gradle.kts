plugins {
    id("com.github.ElytraServers.elytra-conventions") version "v1.1.1"
    id("com.gtnewhorizons.gtnhconvention")
}

// AllMusic (client+server 二合一) 运行时依赖,打包进 jar 并自动 relocate 到 com.Lilith.FMusic.shadow
dependencies {
    // Apache HttpClient 5 (client/server 核心的 HTTP 与音乐流下载)
    shadowImplementation("org.apache.httpcomponents.client5:httpclient5:5.6.1")
    shadowImplementation("org.apache.httpcomponents.core5:httpcore5:5.4.2")
    shadowImplementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.2")

    // Adventure (服务端 MiniMessage 消息与 JSON 序列化)
    shadowImplementation("net.kyori:adventure-text-minimessage:4.26.1")
    shadowImplementation("net.kyori:adventure-api:4.26.1")
    shadowImplementation("net.kyori:adventure-text-serializer-gson:4.8.1")
    shadowImplementation("net.kyori:adventure-text-serializer-legacy:4.8.1")
    shadowImplementation("net.kyori:adventure-text-serializer-plain:4.8.1")
    shadowImplementation("net.kyori:adventure-key:4.8.1")
}
