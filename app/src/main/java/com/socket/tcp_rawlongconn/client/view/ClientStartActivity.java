package com.socket.tcp_rawlongconn.client.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.utils.NetUtils;

import java.net.SocketException;

public class ClientStartActivity extends AppCompatActivity {
    public TextView txtServerIp;
    public TextView txtServerPort;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_start);

        TextView txtlocalIp = findViewById(R.id.localip);
        String localip = "127.0.0.1";
        try {
            localip = NetUtils.getInnetIp();
            txtlocalIp.setText(localip);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        Button btnConnServer = findViewById(R.id.btnConnServer);
        String finalLocalip = localip;
        btnConnServer.setOnClickListener(v -> {
            txtServerIp = findViewById(R.id.server_ip);
            txtServerPort = findViewById(R.id.server_port);

            if (TextUtils.isEmpty(txtServerIp.getText()) || TextUtils.isEmpty(txtServerPort.getText())) {
                Toast.makeText(ClientStartActivity.this, "请键入正确的Ip和端口号", Toast.LENGTH_LONG).show();
            } else {
                String serverIp = txtServerIp.getText().toString();
                int serverPort = Integer.parseInt(txtServerPort.getText().toString());
                Intent intent = new Intent(ClientStartActivity.this, ClientActivity.class);
                intent.putExtra("localIp", finalLocalip);
                intent.putExtra("serverIp", serverIp);
                intent.putExtra("serverPort", serverPort);
                startActivity(intent);
            }
        });

    }

}
