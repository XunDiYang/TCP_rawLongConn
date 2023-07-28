package com.socket.tcp_rawlongconn.server.service;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class TcpServer {
    private final String TAG = "TcpServer";
    protected String serverIp;
    protected int mPort;
    protected ExecutorService mExecutorService;
    protected ServerSocket serverSocket;
    protected static volatile boolean runServer;
    protected String httpServerClassPath;
    protected String handleClientClassPath;
    protected int COUNT_ACTIVE_THREAD = 2;

    public Handler handler;

    public TcpServer() {

    }

    public TcpServer(String serverIp, int port, String httpServerClassPath, String handleClientClassPath) {
        this.serverIp = serverIp;
        mPort = port;
        this.httpServerClassPath = httpServerClassPath;
        this.handleClientClassPath = handleClientClassPath;

        mExecutorService = Executors.newFixedThreadPool(COUNT_ACTIVE_THREAD);
        try {
            serverSocket = new ServerSocket(mPort, COUNT_ACTIVE_THREAD);
            runServer = true;
            Log.i(TAG, "服务器启动成功");
        } catch (IOException e) {
            Log.e(TAG, "服务器启动失败" + e.getMessage());
            Log.e(TAG, "服务器启动失败" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void stop() throws IOException {
        runServer = false;
        mExecutorService.shutdownNow();
        serverSocket.close();
    }

    public void start() {
        mExecutorService.execute(new ConnWatchDog());
    }

    public class ConnWatchDog implements Runnable {
        @Override
        public void run() {
            while (runServer) {
                try {
                    Socket client = serverSocket.accept();
                    client.setKeepAlive(true);
                    Log.i(TAG, "有客户端请求链接");
                    if (((ThreadPoolExecutor) mExecutorService).getActiveCount() >= COUNT_ACTIVE_THREAD) {
//                                                池子已经满了，不能再增加用户请求
                        client.close();
                        Log.i(TAG, "连接池已经满了");
                    } else {
                        Class c = Class.forName(httpServerClassPath);
                        Class clz = Class.forName(handleClientClassPath);
                        Object handleClient = clz.getDeclaredConstructors()[0].newInstance(c.newInstance());
                        Method iniClientSocket = clz.getDeclaredMethod("initClientSocket", Socket.class);
                        iniClientSocket.invoke(handleClient, client);
                        mExecutorService.submit((Runnable) handleClient);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "有客户端链接失败" + e.getMessage());
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static abstract class HandleClient implements Runnable {
        @Override
        public abstract void run();

        public abstract void initClientSocket(Socket s);

        public abstract void closeClientSocket();
    }
}
