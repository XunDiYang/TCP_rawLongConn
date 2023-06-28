package com.socket.tcp_rawlongconn.server.view;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.model.Callback;
import com.socket.tcp_rawlongconn.server.service.EchoServer;

public class ServerActivity extends AppCompatActivity {
    private String serverIp;
    private int serverPort;
    private EchoServer mEchoServer;
    private TextView txtRcvMsg;
    private TextView txtlocalip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        Intent intent = getIntent();
        serverIp = intent.getStringExtra("serverIp");
        serverPort = intent.getIntExtra("serverPort", 8888);
        txtlocalip = findViewById(R.id.localip);
        txtRcvMsg = findViewById(R.id.rcvMsg);
        txtlocalip.setText(toString());
        mEchoServer = new EchoServer(serverPort, rcvMsgCallback);
        mEchoServer.run();
    }

    @NonNull
    @Override
    public String toString() {
        return
                "ip='" + serverIp + '\'' +
                        ", port=" + serverPort;
    }

    private Callback<Void> rcvMsgCallback = new Callback<Void>() {
        @Override
        public void onEvent(int code, String msg, Void unused) {
            if (code == 200) {
                Toast.makeText(ServerActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                if(!msg.isEmpty()){
                    txtRcvMsg.setText(msg);
                }
            } else {
                Toast.makeText(ServerActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
            }
        }
    };
}
