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

public class EchoServer {
    private static final String TAG = "EchoServer";

    private final int mPort;
    private Callback<Void> rcvMsgCallback;
    private volatile boolean running = false;
    private Thread connWatchDog;
    private Handler handler;

//    3s的判断
    private long receiveTimeDelay=3000;

    public EchoServer(int port, Callback<Void> rcvMsgCallback) {
        mPort = port;
        this.rcvMsgCallback = rcvMsgCallback;
        handler = new Handler();
    }

    @SuppressWarnings("deprecation")
    public void stopServer() {
        if (running){
            running = false;
        }
        if (connWatchDog != null){
            connWatchDog.stop();
        }
    }

    public void start() {
        if (running) return;
        running = true;
        connWatchDog = new Thread(new ConnWatchDog());
        connWatchDog.start();
    }

    class ConnWatchDog implements Runnable {

        @Override
        public void run() {
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(mPort, 5);
                Log.d(TAG, "服务器启动成功");
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        Log.d(TAG, "有客户端请求链接");
                        new Thread(new HandleClient(client)).start();
                    } catch (IOException e) {
                        CMessage cMessage = new CMessage();
                        cMessage.setCode(300);
                        handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                        Log.e(TAG, "有客户端链接失败" + e.getMessage());
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "服务器启动失败" + e.getMessage());
                stopServer();
            }

        }
    }

    class HandleClient implements Runnable {
        Socket s;
        boolean run = true;

        long lastReceiveTime = System.currentTimeMillis();

        public HandleClient(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            while (running && run) {
//                TODO: 心跳包超时
                if(System.currentTimeMillis()-lastReceiveTime>receiveTimeDelay){
                    closeClientSocket();
                }else{
                    try {
                        InputStream in = s.getInputStream();
                        if (in.available() > 0) {
                            ObjectInputStream ois = new ObjectInputStream(in);
                            Object obj = ois.readObject();
                            Log.d(TAG, "收到消息");
                            lastReceiveTime = System.currentTimeMillis();
                            CMessage cMessage = (CMessage) obj;
                            Object out = new CMessage(cMessage.getTo(),cMessage.getFrom(),cMessage.getCode(), cMessage.getType(),cMessage.getMsg());
                            cMessage.setMsg("来自"+ cMessage.getFrom()+"客户端: "+cMessage.getMsg());
                            if(cMessage.getCode()!=200 || cMessage.getType() == MsgType.TEXT){
                                handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                            }
                            else if(cMessage.getType() == MsgType.PING){
                                Log.d(TAG,"收到心跳包");
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
                    } catch (Exception e) {
                        CMessage cMessage = new CMessage();
                        cMessage.setCode(300);
                        handler.post(() -> rcvMsgCallback.onEvent(cMessage, null));
                        Log.e(TAG, "handleClient: ", e);
                        closeClientSocket();
                    }
                }

            }
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
            handler.post(() -> rcvMsgCallback.onEvent(new CMessage("","",100,MsgType.CONNECT,""), null));
            Log.e(TAG, "关闭：" + s.getRemoteSocketAddress());
        }
    }

}


