package com.Lilith.FMusic.server.bili.bilibili;

import java.util.Collections;
import java.util.List;

public final class DanmuInfo {

    public final String token;
    public final List<DanmuServer> servers;

    public DanmuInfo(String token, List<DanmuServer> servers) {
        this.token = token;
        this.servers = Collections.unmodifiableList(servers);
    }
}
