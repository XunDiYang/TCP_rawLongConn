package com.socket.tcp_rawlongconn.client.view;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.client.service.EchoClient;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.ConnState;
import com.socket.tcp_rawlongconn.model.MsgType;

import java.io.IOException;

public class ClientActivity extends AppCompatActivity {
    private String localIp;
    private String serverIp;
    private int serverPort;
    private EchoClient mEchoClient;
    private EditText txtSndMsg;
    private TextView txtRcvMsg;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        Intent intent = getIntent();
        localIp = intent.getStringExtra("localIp");
        serverIp = intent.getStringExtra("serverIp");
        serverPort = intent.getIntExtra("serverPort",8888);

        TextView txtlocalIp = findViewById(R.id.localip);
        txtlocalIp.setText(ipPortToString());

        txtSndMsg = findViewById(R.id.sndMsg);
        Button btnSndMsg = findViewById(R.id.btnSndMsg);
        btnSndMsg.setOnClickListener(v ->{
            String sndMsg = txtSndMsg.getText().toString();
            if (TextUtils.isEmpty(sndMsg)) {
                return;
            }
            mEchoClient = new EchoClient(localIp,serverIp,serverPort);
            CMessage cMessage = new CMessage();
            cMessage.setCode(200);
            cMessage.setFrom(localIp);
            cMessage.setTo(serverIp);
            cMessage.setType(MsgType.TEXT);
            cMessage.setMsg(sndMsg);
            mEchoClient.send(cMessage.toJson());
            txtSndMsg.setText("");
        });

        txtRcvMsg = findViewById(R.id.rcvMsg);
    }

    public String ipPortToString() {
        return
                "client ip='" + localIp + '\'' +
                        "\nserver ip='" + serverIp + '\'' +
                        ", port=" + serverPort;
    }


}
