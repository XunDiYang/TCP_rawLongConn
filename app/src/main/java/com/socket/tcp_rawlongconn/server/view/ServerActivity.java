package com.socket.tcp_rawlongconn.server.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.Callback;
import com.socket.tcp_rawlongconn.server.service.EchoServer;

public class ServerActivity extends AppCompatActivity {
    private String serverIp;
    private int serverPort;
    private EchoServer mEchoServer;
    private TextView txtRcvMsg;
    private TextView txtlocalip;
    private Callback<Void> rcvMsgCallback = new Callback<Void>() {
        @Override
        public void onEvent(CMessage cMessage, Void unused) {
            if (cMessage.getCode() == 200) {
                Toast.makeText(ServerActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                if (!cMessage.getMsg().isEmpty()) {
                    txtRcvMsg.setText(cMessage.getMsg());
                }
            } else {
                Toast.makeText(ServerActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
            }
        }
    };

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
}
