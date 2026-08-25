package com.Lilith.FMusic.server.bili.http;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class HttpResult {

    public final int statusCode;
    public final String body;
    public final Map<String, List<String>> headers;

    public HttpResult(int statusCode, String body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
    }

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
