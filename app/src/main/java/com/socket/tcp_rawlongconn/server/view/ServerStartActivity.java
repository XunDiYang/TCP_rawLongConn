package com.socket.tcp_rawlongconn.server.view;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.utils.NetUtils;

import java.net.SocketException;

public class ServerStartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_start);

        TextView txtlocalip = findViewById(R.id.localip);
        String localip = null;
        try {
            localip = NetUtils.getInnetIp();
            txtlocalip.setText(localip);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        EditText txtServerPort = findViewById(R.id.serverPort);

        Button btnStartServer = findViewById(R.id.btnStartServer);
        String ip = localip;
        btnStartServer.setOnClickListener(v -> {
            if (TextUtils.isEmpty(txtServerPort.getText())) {
                Toast.makeText(ServerStartActivity.this, "请键入正确的端口号", Toast.LENGTH_LONG).show();
            } else {
                int serverPort = Integer.parseInt(txtServerPort.getText().toString());
                Intent intent = new Intent(ServerStartActivity.this, ServerActivity.class);
                intent.putExtra("serverIp",ip);
                intent.putExtra("serverPort",serverPort);
                startActivity(intent);
            }

        });
    }

}
