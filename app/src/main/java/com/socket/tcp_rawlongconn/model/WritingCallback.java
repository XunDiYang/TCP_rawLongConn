package com.socket.tcp_rawlongconn.client.callback;

import com.socket.tcp_rawlongconn.model.CMessage;

/**
 * 写数据回调
 */
public interface WritingCallback {
    void onSuccess();

    void onFail(CMessage cMessage);
}
