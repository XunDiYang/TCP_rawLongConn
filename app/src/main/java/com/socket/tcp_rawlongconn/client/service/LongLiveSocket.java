//因为Android是单线程模型，不允许程序员在自定义的线程类中直接操作UI界面，
// 为了解决这个问题，Android开发了Handler对象，由它来负责与子线程进行通信，从而让子线程与主线程之间建立起协作的桥梁，
// 当然也就可以传递数据（大多使用Message对象传递），使Android的UI更新问题得到解决。
// https://blog.csdn.net/dlwh_123/article/details/36174025

package com.socket.tcp_rawlongconn.client.service;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.socket.tcp_rawlongconn.client.callback.DataCallback;
import com.socket.tcp_rawlongconn.client.callback.ErrorCallback;
import com.socket.tcp_rawlongconn.client.callback.WritingCallback;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public final class LongLiveSocket {
    private static final String TAG = "LongLiveSocket";
    private static final long RETRY_INTERVAL_MILLIS = 3 * 1000;
    private static final long HEART_BEAT_INTERVAL_MILLIS = 5 * 1000;
    private static final long HEART_BEAT_TIMEOUT_MILLIS = 2 * 1000;
    private final String localIp;
    private final String mHost;
    private final int mPort;
    private final DataCallback mDataCallback;
    private final ErrorCallback mErrorCallback;
    private final HandlerThread mWriterThread;
    private final Handler mWriterHandler;
    private final Handler mUIHandler = new Handler(Looper.getMainLooper());
    private final Object mLock = new Object();
    private Socket mSocket;  // guarded by mLock
    private boolean mClosed; // guarded by mLock
    private volatile int mSeqNumHeartBeatSent;
    private volatile int mSeqNumHeartBeatRecv;
    private byte[] mHeartBeat;

    public LongLiveSocket(String localIp, String host, int port,
                          DataCallback dataCallback, ErrorCallback errorCallback) {
        this.localIp = localIp;
        mHost = host;
        mPort = port;
//        mHeartBeat = new byte[0];
        CMessage heartBeatMsg = new CMessage(localIp,mHost,200, MsgType.PING,"");
        mHeartBeat = (heartBeatMsg.toJson()+"\0\0\0").getBytes();
        mDataCallback = dataCallback;
        mErrorCallback = errorCallback;

        mWriterThread = new HandlerThread("socket-writer");
        mWriterThread.start();
        mWriterHandler = new Handler(mWriterThread.getLooper());
        mWriterHandler.post(this::initSocket);
    }

    private static void silentlyClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.e(TAG, "silentlyClose: ", e);
                // error ignored
            }
        }
    }

    private void initSocket() {
        while (true) {
            if (closed()) return;

            try {
                Socket socket = new Socket(mHost, mPort);
//                socket.setKeepAlive(true);
                socket.setReceiveBufferSize(1024);
                socket.setSendBufferSize(1024);
                synchronized (mLock) {
                    // 在我们创建 socket 的时候，客户可能就调用了 close()
                    if (mClosed) {
                        silentlyClose(socket);
                        return;
                    }
                    mSocket = socket;
                    // 每次创建新的 socket，会开一个线程来读数据
                    Thread reader = new Thread(new ReaderTask(socket), "socket-reader");
                    reader.start();
                    mWriterHandler.post(mHeartBeatTask);
                }
                break;
            } catch (IOException e) {
                Log.e(TAG, "initSocket: ", e);
                if (closed() || !mErrorCallback.onError()) {
                    break;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MILLIS);
                } catch (InterruptedException e1) {
                    // interrupt writer-thread to quit
                    break;
                }
            }
        }
    }

    public void write(byte[] data, WritingCallback callback) {
        write(data, 0, data.length, callback);
    }

    public void write(byte[] data, int offset, int len, WritingCallback callback) {
        mWriterHandler.post(() -> {
            Socket socket = getSocket();
            if (socket == null) {
                // 心跳超时的情况下，这里 socket 会是 null
                initSocket();
                socket = getSocket();
                if (socket == null) {
                    if (!closed()) {
                        callback.onFail(data, offset, len);
                    } /* else {
                        // silently drop the data
                    } */
                    return;
                }
            }
            try {
                OutputStream outputStream = socket.getOutputStream();
                DataOutputStream out = new DataOutputStream(outputStream);

                out.write(data, offset, len);
                out.flush();
                callback.onSuccess();
            } catch (IOException e) {
                Log.e(TAG, "write: ", e);
                closeSocket();
                callback.onFail(data, offset, len);
                if (!closed() && mErrorCallback.onError()) {
                    initSocket();
                }
            }
        });
    }

    private final Runnable mHeartBeatTask = new Runnable() {

        @Override
        public void run() {
            // no need to be atomic
            // noinspection NonAtomicOperationOnVolatileField
            ++mSeqNumHeartBeatSent;
            // 我们使用长度为 0 的数据作为 heart beat
            write(mHeartBeat, new WritingCallback() {
                @Override
                public void onSuccess() {
                    // 每隔 HEART_BEAT_INTERVAL_MILLIS 发送一次
                    mWriterHandler.postDelayed(mHeartBeatTask, HEART_BEAT_INTERVAL_MILLIS);
                    // At this point, the heart-beat might be received and handled
                    if (mSeqNumHeartBeatRecv < mSeqNumHeartBeatSent) {
                        mUIHandler.postDelayed(mHeartBeatTimeoutTask, HEART_BEAT_TIMEOUT_MILLIS);
                        // double check
                        if (mSeqNumHeartBeatRecv == mSeqNumHeartBeatSent) {
                            mUIHandler.removeCallbacks(mHeartBeatTimeoutTask);
                        }
                    }
                }

                @Override
                public void onFail(byte[] data, int offset, int len) {
                    // nop
                    // write() 方法会处理失败
                }
            });
        }
    };

    private final Runnable mHeartBeatTimeoutTask = () -> {
        Log.e(TAG, "mHeartBeatTimeoutTask#run: heart beat timeout");
        closeSocket();
    };


    private boolean closed() {
        synchronized (mLock) {
            return mClosed;
        }
    }

    private Socket getSocket() {
        synchronized (mLock) {
            return mSocket;
        }
    }

    private void closeSocket() {
        synchronized (mLock) {
            closeSocketLocked();
        }
    }

    private void closeSocketLocked() {
        if (mSocket == null) return;

        silentlyClose(mSocket);
        mSocket = null;
        mWriterHandler.removeCallbacks(mHeartBeatTask);
    }

    public void close() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            new Thread() {
                @Override
                public void run() {
                    doClose();
                }
            }.start();
        } else {
            doClose();
        }
    }

    private void doClose() {
        synchronized (mLock) {
            mClosed = true;
            // 关闭 socket，从而使得阻塞在 socket 上的线程返回
            closeSocketLocked();
        }
        mWriterThread.quit();
        // 在重连的时候，有个 sleep
        mWriterThread.interrupt();
    }


    private class ReaderTask implements Runnable {

        private final Socket mSocket;

        public ReaderTask(Socket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            try {
                readResponse();
            } catch (IOException | JSONException e) {
                Log.e(TAG, "ReaderTask#run: ", e);
            }
        }

        private void readResponse() throws IOException, JSONException {
            InputStream inputStream = mSocket.getInputStream();
            DataInputStream in = new DataInputStream(inputStream);

            while (true) {
                int n;
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((n = in.read(buffer)) > 0) {
                    baos.write(buffer, 0, n);
                }
                if (baos.size() > 0) {
                    ArrayList<String> rcvCMsgStrArray = trimNull(baos.toByteArray());
                    for (String rcvCMsgStr : rcvCMsgStrArray) {
                        CMessage rcvMsg = new Gson().fromJson(rcvCMsgStr, CMessage.class);
                        if(rcvMsg.getType() == MsgType.PING){
                            Log.i(TAG, "readResponse: heart beat received");
                            mUIHandler.removeCallbacks(mHeartBeatTimeoutTask);
                            mSeqNumHeartBeatRecv = mSeqNumHeartBeatSent;
                        }else{
                            mDataCallback.onData(rcvMsg, 0, buffer.length);
                        }
                    }
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


}



