package com.socket.tcp_rawlongconn.client.callback;

import com.socket.tcp_rawlongconn.model.CMessage;

import org.json.JSONException;
/**
 * 读数据回调
 */
public interface DataCallback {
    void onData(CMessage data) throws JSONException;
}

