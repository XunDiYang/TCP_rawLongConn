package com.socket.tcp_rawlongconn.client.service;

import android.util.Log;

import com.socket.tcp_rawlongconn.client.callback.WritingCallback;

public class EchoClient {
    private static final String TAG = "EchoClient";

    private final LongLiveSocket mLongLiveSocket;

    public EchoClient(String localIp, String host, int port) {
        mLongLiveSocket = new LongLiveSocket(localIp,
                host, port,
                (cMsg, offset, len) ->
                        Log.i(TAG, "EchoClient: received: " + cMsg.toString()),
                () -> true);
    }

    public void send(String msg) {
//        TODO: 分隔符号：msg + "\0\0\0"
        mLongLiveSocket.write((msg + "\0\0\0").getBytes(), new WritingCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "onSuccess: ");
            }

            @Override
            public void onFail(byte[] data, int offset, int len) {
                Log.w(TAG, "onFail: fail to write: " + new String(data, offset, len));
                mLongLiveSocket.write(data, offset, len, this);
            }
        });
    }

    public void close() {
        mLongLiveSocket.close();
    }


}



