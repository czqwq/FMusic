package com.Lilith.FMusic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Config {

    /**
     * FMusic 的配置不在此 cfg 中,而是分散在游戏运行目录下:
     * - 服务端配置: ../fmusic_server/ (config.json / message.json / ban.json / hud.json / music.json / cookie.json)
     * - 客户端配置: ./fmusic_client.json
     * 此文件仅写入固定提示内容,不注册任何选项。
     */
    public static void synchronizeConfiguration(File configFile) {
        try {
            if (!configFile.exists() || configFile.length() == 0) {
                String comment = "# FMusic configuration\n" + "#\n"
                    + "# server config is on ../fmusic_server\n"
                    + "# client config is on ./fmusic_client.json\n";
                Files.write(configFile.toPath(), comment.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
