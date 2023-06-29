package com.socket.tcp_rawlongconn.model;

public interface Callback<T> {
    //    void onEvent( int code, String msg, T t);
    void onEvent(CMessage cMessage, T t);
}
