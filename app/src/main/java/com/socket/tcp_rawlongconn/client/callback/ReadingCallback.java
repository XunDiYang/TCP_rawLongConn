package com.socket.tcp_rawlongconn.client.callback;

import com.socket.tcp_rawlongconn.model.CMessage;

/**
 * 读数据回调
 */
public interface ReadingCallback {
    void onData(CMessage data);
}

