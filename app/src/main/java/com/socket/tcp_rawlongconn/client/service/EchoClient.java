package com.socket.tcp_rawlongconn.client.service;

import android.util.Log;

import com.google.gson.Gson;
import com.socket.tcp_rawlongconn.model.CMessage;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class EchoClient {
    private static final String TAG = "EchoClient";

    private final LongLiveSocket mLongLiveSocket;

    public EchoClient(String localIp,String host, int port) {
        mLongLiveSocket = new LongLiveSocket(localIp,
                host, port,
                (cMsgStr, offset, len) ->
                {
                    Gson gson = new Gson();
                    CMessage cMsg = gson.fromJson(String.valueOf(cMsgStr), CMessage.class);
                    Log.i(TAG, "EchoClient: received: " + cMsg.toString());
                },
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



