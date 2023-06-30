package com.socket.tcp_rawlongconn.client.callback;
/**
 * 写数据回调
 */
public interface WritingCallback {
    void onSuccess();

    void onFail(byte[] data, int offset, int len);
}
