package com.socket.tcp_rawlongconn.client.service;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.socket.tcp_rawlongconn.client.callback.ReadingCallback;
import com.socket.tcp_rawlongconn.client.callback.ErrorCallback;
import com.socket.tcp_rawlongconn.client.callback.WritingCallback;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;

import org.json.JSONException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public final class LongLiveSocket extends Thread {
  private static final String TAG = "LongLiveSocket";
    private final String localIp;
    private final String mHost;
    private final int mPort;
    private final ReadingCallback mReadingCallback;
    private final ErrorCallback mErrorCallback;
    private final Handler mUIHandler = new Handler(Looper.getMainLooper());
    private HandlerThread mWriterThread;
    private Handler mWriterHandler;
    private Thread mReaderThread;
    //    private Thread mHeartBeatThread;
    private static Socket mSocket;  // guarded by mLock
//    private CMessage heartBeatMsg;

    private long lastSendTime; //最后一次发送数据的时间

    private volatile boolean running = false; //连接状态

    public LongLiveSocket(String localIp, String host, int port,
                          ReadingCallback readingCallback, ErrorCallback errorCallback) {
        this.localIp = localIp;
        mHost = host;
        mPort = port;
//        heartBeatMsg = new CMessage(localIp, mHost, 200, MsgType.PING, "");
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
            mSocket = new Socket(mHost, mPort);
            mSocket.setKeepAlive(true);
            running = true;
            mWriterThread = new HandlerThread("socket-writer");
            mWriterThread.start();
            mWriterHandler = new Handler(mWriterThread.getLooper());
            mReaderThread = new Thread(new ReaderTask(mSocket), "socket-reader");
            mReaderThread.start();
//            mHeartBeatThread = new Thread(mHeartBeatTask, "socket-heartbeat").start();
            mUIHandler.post(() -> mReadingCallback.onData(new CMessage("", "", 100, MsgType.CONNECT, "")));
        } catch (IOException e) {
            Log.e(TAG, "initSocket: ", e);
            mUIHandler.post(() -> mErrorCallback.onError());
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        try {
            if (mSocket != null) {
                Log.d(TAG, "关闭客户端的socket");
                if( mWriterThread != null) {
                    mWriterThread.interrupt();
                }
                if(mReaderThread!=null) {
                    mReaderThread.interrupt();
                }
//                mHeartBeatThread.interrupt();
                mSocket.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopServerSocket() {
        if (running) {
            running = false;
        }
        mUIHandler.post(() -> mErrorCallback.onError());
    }

    public void write(CMessage cMessage, WritingCallback callback) {
        mWriterHandler.post(new WriterTask(cMessage, callback));
    }

    private class WriterTask implements Runnable {
        private CMessage cMsg;
        private WritingCallback callback;

        public WriterTask(CMessage cmsg, WritingCallback callback) {
            this.cMsg = cmsg;
            this.callback = callback;
        }

        @Override
        public void run() {
            if (!running) {
                iniSocket();
            }
            try {
                ObjectOutputStream oos = new ObjectOutputStream(mSocket.getOutputStream());
                oos.writeObject(cMsg);
                Log.d(TAG, "发送：\t" + cMsg.toJsonStr());
                oos.flush();
                lastSendTime = System.currentTimeMillis();
                callback.onSuccess();
            } catch (IOException e) {
                Log.e(TAG, "write: ", e);
                callback.onFail(cMsg);
                stopServerSocket();
            }
        }
    }

//    private final HeartBeatTask mHeartBeatTask = new HeartBeatTask();
//
//    private class HeartBeatTask implements Runnable {
//        long checkDelay = 10;
//        long keepAliveDelay = 1000;
//
//        @Override
//        public void run() {
//            while (running) {
//                if (System.currentTimeMillis() - lastSendTime > keepAliveDelay) {
//                    write(heartBeatMsg, new WritingCallback() {
//                        @Override
//                        public void onSuccess() {
//                            // 每隔 HEART_BEAT_INTERVAL_MILLIS 发送一次
//                            Log.d(TAG,"心跳包发送成功");
//                        }
//                        @Override
//                        public void onFail(CMessage cMessage) {
//                            Log.w(TAG,"心跳包发送失败");
//                            stopServerSocket();
//                        }
//                    });
//                } else {
//                    try {
//                        Thread.sleep(checkDelay);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                        Log.w(TAG,"睡眠失败？");
//                        stopServerSocket();
//                    }
//                }
//            }
//        }
//    }

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
            InputStream inputStream = mSocket.getInputStream();
            DataInputStream in = new DataInputStream(inputStream);

            while (running) {
                if (in.available() > 0) {
                    ObjectInputStream ois = new ObjectInputStream(in);
                    Object obj = ois.readObject();
                    Log.d(TAG, "接收：\t" + obj);
                    CMessage cMessage = (CMessage) obj;
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


}


