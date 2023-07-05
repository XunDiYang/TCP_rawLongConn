package com.socket.tcp_rawlongconn.server.service;


import android.os.Handler;
import android.util.Log;

import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;
import com.socket.tcp_rawlongconn.server.callback.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoServer {
        private final String TAG = "EchoServer";

    private final String serverIp;
    private final int mPort;
    private Callback<Void> rcvMsgCallback;

    private Handler handler;
    /********************************************************/
    /*
     *   以下使用Executors实现(线程池)（readObject）
     *
     *
     * */
    private final ExecutorService mExecutorService;
    private ServerSocket serverSocket;

    private volatile boolean running = true;

    public EchoServer(String serverIp, int port, Callback<Void> rcvMsgCallback) {
        this.serverIp = serverIp;
        mPort = port;
        this.rcvMsgCallback = rcvMsgCallback;
        handler = new Handler();
        mExecutorService = Executors.newFixedThreadPool(5);
        try {
            serverSocket = new ServerSocket(mPort);
            Log.e(TAG, "服务器启动成功");
        } catch (IOException e) {
            Log.e(TAG, "服务器启动失败" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void start() {
        mExecutorService.execute(new ConnWatchDog());
    }

    public void stop() throws IOException {
        running = false;
        mExecutorService.shutdown();
        serverSocket.close();
        CMessage cMessage = new CMessage();
        cMessage.setCode(500);
        handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
    }

    class ConnWatchDog implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setKeepAlive(true);
                    Log.d(TAG, "有客户端请求链接");
                    CMessage cMessage = new CMessage();
                    cMessage.setCode(100);
                    handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                    mExecutorService.submit(new HandleClient(client));
                } catch (IOException e) {
                    CMessage cMessage = new CMessage();
                    cMessage.setCode(400);
                    handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                    Log.e(TAG, "有客户端链接失败" + e.getMessage());
                }
            }
        }
    }

    private class HandleClient implements Runnable {
        Socket s;
        volatile boolean run = true;
        long lastReceiveTime = System.currentTimeMillis();

        public HandleClient(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            try {
                while (running && run) {
                    InputStream in = s.getInputStream();
                    if (in.available() > 0) {
                        ObjectInputStream ois = new ObjectInputStream(in);
                        Object obj = ois.readObject();
                        Log.d(TAG, "收到消息");
                        lastReceiveTime = System.currentTimeMillis();
                        CMessage cMessage = (CMessage) obj;
                        Object out = new CMessage(cMessage.getTo(), cMessage.getFrom(), cMessage.getCode(), cMessage.getType(), cMessage.getMsg());
                        cMessage.setMsg("来自" + cMessage.getFrom() + "客户端: " + cMessage.getMsg());
                        if (cMessage.getCode() != 200 || cMessage.getType() == MsgType.TEXT) {
                            handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                        }
                        if (out != null) {
                            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                            oos.writeObject(out);
                            oos.flush();
                            Log.d(TAG, "发送消息");
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
            Log.d(TAG, "结束处理数据");
        }

        private void closeClientSocket() {
            if (run) {
                run = false;
            }
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
//            handler.post(() -> rcvMsgCallback.onEvent(new CMessage("", "", 400, MsgType.CONNECT, ""), null));
            Log.e(TAG, "关闭：" + s.getRemoteSocketAddress());
        }
    }
}


