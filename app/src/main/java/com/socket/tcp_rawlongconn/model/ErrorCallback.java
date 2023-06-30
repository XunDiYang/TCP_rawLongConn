package com.socket.tcp_rawlongconn.client.callback;

/**
 * 错误回调
 */
public interface ErrorCallback {
    /**
     * 如果需要重连，返回 true
     */
    boolean onError();
}
