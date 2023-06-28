package com.socket.tcp_rawlongconn.client.service;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class EchoClient {
    private static final String TAG = "EchoClient";

    private final LongLiveSocket mLongLiveSocket;

    public EchoClient(String host, int port) {
        mLongLiveSocket = new LongLiveSocket(
                host, port,
                (data, offset, len) -> Log.i(TAG, "EchoClient: received: " + new String(data, offset, len)),
                () -> true);
    }

    public void send(String msg) {
        mLongLiveSocket.write(msg.getBytes(), new LongLiveSocket.WritingCallback() {
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



