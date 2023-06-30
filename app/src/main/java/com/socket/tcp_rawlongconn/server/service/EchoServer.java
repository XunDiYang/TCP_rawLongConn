package com.socket.tcp_rawlongconn.server.service;

import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;
import com.socket.tcp_rawlongconn.server.callback.Callback;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoServer {
    private static final String TAG = "EchoServer";

    private final int mPort;
    private final ExecutorService mExecutorService;
    private Callback<Void> rcvMsgCallback;
    private Handler handler;

    public EchoServer(int port, Callback<Void> rcvMsgCallback) {
        mPort = port;
        this.rcvMsgCallback = rcvMsgCallback;
        mExecutorService = Executors.newFixedThreadPool(4);
        handler = new Handler();
    }

    public EchoServer(int port) {
        mPort = port;
        handler = new Handler();
        mExecutorService = Executors.newFixedThreadPool(4);
    }

    public void run() {
        mExecutorService.submit(() -> {
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(mPort);
                Log.e(TAG, "服务器启动成功");
            } catch (IOException e) {
                Log.e(TAG, "服务器启动失败" + e.getMessage());
                return;
            }
            while (true) {
                try {
                    Socket client = serverSocket.accept();
//                    client.setKeepAlive(true);
                    Log.e(TAG, "有客户端请求链接");
                    handleClient(client);
                } catch (IOException e) {
                    CMessage cMessage = new CMessage();
                    cMessage.setCode(300);
                    handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                    Log.e(TAG, "有客户端链接失败" + e.getMessage());
                }
            }
        });
    }

    private void handleClient(Socket socket) {
        mExecutorService.submit(() -> {
            try {
                doHandleClient(socket);
            } catch (IOException e) {
                CMessage cMessage = new CMessage();
                cMessage.setCode(300);
                handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                Log.e(TAG, "handleClient: ", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    CMessage cMessage = new CMessage();
                    cMessage.setCode(300);
                    handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                    Log.e(TAG, "handleClient: ", e);
                }
            }
        });
    }

    private void doHandleClient(Socket socket) throws IOException {
        InputStream inputStream = (socket.getInputStream());
        DataInputStream in = new DataInputStream(inputStream);

        OutputStream outputStream = (socket.getOutputStream());
        DataOutputStream out = new DataOutputStream(outputStream);
        byte[] buffer = new byte[1024];
        int n;

        processPkg(in, out);
    }

    private void processHeartBeat(DataInputStream in, DataOutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            out.flush();
        }
        CMessage heartMsg = new CMessage("", "", 200, MsgType.PING, "");
    }

    private void processPkg(DataInputStream in, DataOutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int n;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((n = in.read(buffer)) > 0) {
            baos.write(buffer, 0, n);
        }
        if (baos.size() > 0) {
            ArrayList<String> rcvCMsgStrArray = trimNull(baos.toByteArray());
            for (String rcvCMsgStr : rcvCMsgStrArray) {
                CMessage rcvMsg = new Gson().fromJson(rcvCMsgStr, CMessage.class);
                CMessage sndMsg = new CMessage(rcvMsg.getTo(), rcvMsg.getFrom(), rcvMsg.getCode(), rcvMsg.getType(), "你好" + rcvMsg.getFrom() + ",我已收到消息：" + rcvMsg.getMsg());
                byte[] sndData = (sndMsg.toJson()+"\0\0\0").getBytes();
                int sndDataLen = sndData.length;
//                out.writeInt(sndDataLen);
                out.write(sndData, 0, sndDataLen);
                out.flush();
                handler.post(() -> rcvMsgCallback.onEvent(rcvMsg, null));
            }
        }
    }

    private ArrayList<String> trimNull(byte[] bytes) throws UnsupportedEncodingException {
//        000为分隔符
        ArrayList<Byte> list = new ArrayList<Byte>();
        ArrayList<String> strArrayList = new ArrayList<String>();
        int cntZero = 0;
        for (int i = 0; bytes != null && i < bytes.length; i++) {
            if (0 != bytes[i]) {
                list.add(bytes[i]);
            } else {
                if (cntZero == 2) {
//                    将前面的字节流转为str
                    byte[] newbytes = new byte[list.size()];
                    for (int j = 0; j < list.size(); j++) {
                        newbytes[j] = (Byte) list.get(j);
                    }
                    list.clear();
                    strArrayList.add(new String(newbytes, "UTF-8"));
                    cntZero = 0;
                } else {
                    cntZero += 1;
                }
            }
        }

        return strArrayList;
    }
}


