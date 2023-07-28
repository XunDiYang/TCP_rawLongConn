package com.socket.tcp_rawlongconn.client.service;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;

import com.socket.tcp_rawlongconn.client.callback.ErrorCallback;
import com.socket.tcp_rawlongconn.client.callback.ReadingCallback;
import com.socket.tcp_rawlongconn.client.callback.WritingCallback;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;

import org.json.JSONException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public final class LongLiveSocket extends Thread {
    private static final String TAG = "LongLiveSocket";
    private final String localIp;
    private final String serverIp;
    private final int serverPort;
    private final ReadingCallback mReadingCallback;
    private final ErrorCallback mErrorCallback;
    private final Handler mUIHandler = new Handler(Looper.getMainLooper());
    private HandlerThread mWriterThread;
    private Handler mWriterHandler;
    private Thread mReaderThread;
    //    private Thread mHeartBeatThread;
    private static Socket mSocket;  // guarded by mLock
    private Object heartBeatMsg;

    private long lastSendTime; //最后一次发送数据的时间

    private volatile boolean running = false; //连接状态
    private volatile boolean runningWrite = false; //连接状态
    public LongLiveSocket(String localIp, String host, int port,
                          ReadingCallback readingCallback, ErrorCallback errorCallback) {
        this.localIp = localIp;
        serverIp = host;
        serverPort = port;
//        heartBeatMsg = new CMessage(localIp, mHost, 200, MsgType.PING, "");
//        heartBeatMsg = null;
        mReadingCallback = readingCallback;
        mErrorCallback = errorCallback;
    }

    /********************************************************/
    /*
     *   以下使用原生Thread实现
     *
     *
     * */
    @Override
    public void run() {
        iniSocket();
    }

    private void iniSocket() {
        if (running) return;
        try {
            mSocket = new Socket(serverIp, serverPort);
            mSocket.setKeepAlive(true);
            running = true;
            runningWrite = true;
            mWriterThread = new HandlerThread("socket-writer");
            mWriterThread.start();
            mWriterHandler = new Handler(mWriterThread.getLooper());
            mReaderThread = new Thread(new ReaderTask(mSocket), "socket-reader");
            mReaderThread.start();
//            mHeartBeatThread = new Thread(mHeartBeatTask, "socket-heartbeat");
//            mHeartBeatThread.start();
            mUIHandler.post(() -> mReadingCallback.onData(new CMessage("", "", 100, MsgType.CONNECT, "")));
        } catch (IOException e) {
            Log.e(TAG, "initSocket: ", e);
            mUIHandler.post(() -> mErrorCallback.onError());
            stopServerSocket();
        }
    }

    @Override
    public void interrupt() {
        try {
            if (mSocket != null) {
                sendEndPkg();
                if (mWriterThread != null) {
                    mWriterThread.interrupt();
                }
                if (mReaderThread != null) {
                    mReaderThread.interrupt();
                }
//                if (mHeartBeatThread != null) {
//                    mHeartBeatThread.interrupt();
//                }
                mSocket.close();
                Log.i(TAG, "关闭客户端的socket");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.interrupt();
    }

    public void sendEndPkg() {
        if (runningWrite) {
            if (running) {
                int SDK_INT = android.os.Build.VERSION.SDK_INT;
                if (SDK_INT > 8) {
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    //your codes here
                    WriterTask endPkg = new WriterTask(new CMessage(localIp, serverIp, 400, MsgType.CONNECT, ""), new WritingCallback() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onFail(Object cMessage) {
                        }
                    });
                    endPkg.run();
                }
            }
            runningWrite = false;
        }
    }

    public void stopServerSocket() {
        if (running) {
            sendEndPkg();
            running = false;
        }
        mUIHandler.post(() -> mErrorCallback.onError());
    }

    public void write(Object cMessage, WritingCallback callback) {
        mWriterHandler.post(new WriterTask(cMessage, callback));
    }

    private class WriterTask implements Runnable {
        private Object cMsg;
        private WritingCallback callback;

        public WriterTask(Object cmsg, WritingCallback callback) {
            this.cMsg = cmsg;
//            this.cMsg = null;
            this.callback = callback;
        }

        @Override
        public void run() {
            if (!runningWrite) {
                return;
            }
            if (!running) {
                iniSocket();
            }
            try {
                OutputStream outToServer = mSocket.getOutputStream();
                DataOutputStream out = new DataOutputStream(outToServer);
                if (cMsg != null) {
                    out.writeUTF(((CMessage) cMsg).getFrom());
                    out.writeUTF(((CMessage) cMsg).getTo());
                    out.writeInt(((CMessage) cMsg).getCode());
                    out.writeInt(((CMessage) cMsg).getType());
                    out.writeUTF(((CMessage) cMsg).getMsg());
                    out.flush();
                    Log.i(TAG, "发送：\t" + ((CMessage) cMsg).toJsonStr());
                }
//                else {
//                    byte[] mHeartBeat = new byte[0];
//                    out.write(mHeartBeat);
//                    out.flush();
//                    Log.i(TAG, "发送空包！ empty!");
//                }
//                lastSendTime = System.currentTimeMillis();
                callback.onSuccess();
            } catch (IOException e) {
                Log.e(TAG, "write: ", e);
                runningWrite = false;  //服务器端断开了
                callback.onFail(cMsg);
                stopServerSocket();
            }
        }
    }

//    private final HeartBeatTask mHeartBeatTask = new HeartBeatTask();

    private class HeartBeatTask implements Runnable {
        long checkDelay = 2000;
        long keepAliveDelay = 20000;

        @Override
        public void run() {
            while (running) {
                if (System.currentTimeMillis() - lastSendTime > keepAliveDelay) {
                    write(heartBeatMsg, new WritingCallback() {
                        @Override
                        public void onSuccess() {
                            // 每隔 HEART_BEAT_INTERVAL_MILLIS 发送一次
                            Log.i(TAG, "心跳包发送成功");
                        }

                        @Override
                        public void onFail(Object cMessage) {
                            Log.w(TAG, "心跳包发送失败");
                            stopServerSocket();
                        }
                    });
                } else {
                    try {
                        Thread.sleep(checkDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.w(TAG, "睡眠失败？");
                        stopServerSocket();
                    }
                }
            }
        }
    }

    private class ReaderTask implements Runnable {

        private final Socket mSocket;

        public ReaderTask(Socket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    readResponse();
                } catch (IOException | JSONException | ClassNotFoundException |
                         InterruptedException e) {
                    Log.e(TAG, "ReaderTask#run: ", e);
                    stopServerSocket();
                }
            }
        }

        private void readResponse() throws IOException, JSONException, ClassNotFoundException, InterruptedException {

            while (running) {
                InputStream inputStream = mSocket.getInputStream();
                DataInputStream in = new DataInputStream(inputStream);

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
                        mUIHandler.post(() -> {
                            mReadingCallback.onData(cMessage);
                        });
                    }
                } else {
                    Thread.sleep(10);
                }
            }
        }
    }


    /********************************************************/
    /* 有问题！连接不上
     *   以下使用Netty实现
     *
     *
     * */

//    public SocketChannel socketChannel;
//    @Override
//    public void run() {
//        super.run();
//        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
//        new Bootstrap()
//                .channel(NioSocketChannel.class)
//                .group(workerGroup)
//                .option(ChannelOption.SO_KEEPALIVE, true)
//                .option(ChannelOption.TCP_NODELAY, true)
//                .handler(new ChannelInitializer<SocketChannel>() {
//                    @Override
//                    protected void initChannel(SocketChannel ch) throws Exception {
//                        ch.pipeline().addLast(new ObjectEncoder());
//                        ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
//                        ch.pipeline().addLast(new ClientHandler());
//                    }
//                })
//                .connect(new InetSocketAddress(mHost, mPort))
//                .addListener((ChannelFutureListener) future -> {
//                    if (future.isSuccess()) {
//                        socketChannel = (SocketChannel) future.channel();
//                        Log.i(TAG,"连接成功");
//                    } else {
//                        Throwable cause = future.cause();
//                        Log.e(TAG,"连接失败"+cause.getMessage());
//                        closeChannel();
//                        // 这里一定要关闭，不然一直重试会引发OOM
//                        future.channel().close();
//                        workerGroup.shutdownGracefully();
//                    }
//                });
//    }
//
//
//    public void sendMsg(CMessage message) throws InterruptedException {
//        socketChannel.writeAndFlush(message)
//                .addListener((ChannelFutureListener) future -> {
//                    if (future.isSuccess()) {
//                        Log.i(TAG,"发送成功");
//                    } else {
//                        closeChannel();
//                        Throwable cause = future.cause();
//                        Log.i(TAG,"发送成功"+cause.getMessage());
//                    }
//                });
//    }
//
//    public void closeChannel() {
//        if (socketChannel != null) {
//            socketChannel.close();
//            socketChannel = null;
//        }
//    }
//
//    private class ClientHandler extends ChannelInboundHandlerAdapter {
//        private String TAG = "ClientHandler";
//
//        @Override
//        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//            super.channelInactive(ctx);
//            closeChannel();
//        }
//
//        @Override
//        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//            super.channelRead(ctx, msg);
//            CMessage recvCMsg = (CMessage) msg;
//
//            if(recvCMsg.getType() == MsgType.PING){
//                Log.i(TAG, "receive ping from server");
//            } else if (recvCMsg.getType() == MsgType.CONNECT) {
//                if (recvCMsg.getCode() == 200){
//                    socketChannel = (SocketChannel) ctx;
//                }else{
//                    closeChannel();
//                    Log.e(TAG,"服务器端错误");
//                }
//            } else if (recvCMsg.getType() == MsgType.TEXT) {
//                socketChannel = (SocketChannel) ctx;
//                Log.i(TAG, "receive text message ");
//            }
//
//            ReferenceCountUtil.release(msg);
//        }
//    }

}


