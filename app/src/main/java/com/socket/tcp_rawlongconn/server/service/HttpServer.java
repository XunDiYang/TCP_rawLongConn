package com.socket.tcp_rawlongconn.server.service;

import android.os.Handler;
import android.util.Log;

import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;
import com.socket.tcp_rawlongconn.server.callback.Callback;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class HttpServer extends TcpServer{
    private static final String TAG = "HttpServer";
    public static Handler handler = new Handler();
    public static Callback<Void> rcvMsgCallback;

    public HttpServer(){

    }

    public HttpServer(String serverIp, int port, Callback<Void> rcvMsgCallback) {
        super(serverIp, port, "com.socket.tcp_rawlongconn.server.service.HttpServer","com.socket.tcp_rawlongconn.server.service.HttpServer$HttpHandleClient");
        this.rcvMsgCallback = rcvMsgCallback;
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() throws IOException {
        super.stop();
        CMessage cMessage = new CMessage();
        cMessage.setCode(500);
        handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
    }

    public class HttpHandleClient extends HandleClient {
        Socket client;
        volatile boolean runHandleClient;

        @Override
        public void initClientSocket(Socket s){
            client = s;
            runHandleClient = true;
            handler.post(() -> rcvMsgCallback.onEvent(new CMessage("","",100, MsgType.CONNECT,""), null));
        }

        @Override
        public void run() {
            try {
                while (runServer && runHandleClient && client != null) {
                    DataInputStream in = new DataInputStream(client.getInputStream());
                    if (in.available() > 0) {
                        Log.i(TAG, "收到消息");

                        CMessage cMessage = new CMessage();
                        cMessage.setFrom(in.readUTF());
                        cMessage.setTo(in.readUTF());
                        cMessage.setCode(in.readInt());
                        cMessage.setType(in.readInt());
                        cMessage.setMsg(in.readUTF());
                        cMessage.setMsg("来自" + cMessage.getFrom() + "客户端: " + cMessage.getMsg());

                        if (cMessage.getCode() != 200 || cMessage.getType() == MsgType.TEXT) {
                            handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                        }
                        if(cMessage.getCode() == 200 || cMessage.getCode() == 100){
                            DataOutputStream out = new DataOutputStream(client.getOutputStream());
                            out.writeUTF((cMessage).getTo());
                            out.writeUTF((cMessage).getFrom());
                            out.writeInt((cMessage).getCode());
                            out.writeInt((cMessage).getType());
                            out.writeUTF((cMessage).getMsg());
                            out.flush();
                            Log.i(TAG, "发送消息");
                        }
                        else if (cMessage.getCode() == 400) {
                            runHandleClient = false;
                        }
                    } else {
                        Thread.sleep(10);
                    }
                }
            } catch (Exception e) {
                CMessage cMessage = new CMessage();
                cMessage.setCode(400);
                handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                Log.e(TAG, "handleClient: ", e);
                closeClientSocket();
            } finally {
                closeClientSocket();
            }
            Log.i(TAG, "结束处理数据");
        }

        @Override
        public void closeClientSocket() {
            if (runHandleClient) {
                runHandleClient = false;
            }
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.i(TAG, "关闭：" + client.getRemoteSocketAddress());
        }
    }
}
