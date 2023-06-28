package com.socket.tcp_rawlongconn.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetUtils {
    public static String getInnetIp() throws SocketException {
        String localip="127.0.0.1";
        Enumeration<NetworkInterface> networkInterfaces;
        networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces != null && networkInterfaces.hasMoreElements()){
            NetworkInterface ni = networkInterfaces.nextElement();
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            
            while(addresses.hasMoreElements()){
                InetAddress address = addresses.nextElement();
                if(address.isSiteLocalAddress() && !address.isLoopbackAddress() && address.getHostAddress().indexOf(":")==-1){
                    String hostAddress = address.getHostAddress();
                    if(hostAddress.contains("192.") || hostAddress.contains("10.36.")){
//                        很怪，排除ipv6和127.*.*.*，为什么必须是192.*.*.*?
                        return hostAddress;
                    }
                }
            }
        }

        return localip;
    }
}
