package com.socket.tcp_rawlongconn.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.client.view.ClientStartActivity;
import com.socket.tcp_rawlongconn.server.view.ServerStartActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnServer = findViewById(R.id.btnServer);
        btnServer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ServerStartActivity.class);
            startActivity(intent);
        });

        Button btnClient = findViewById(R.id.btnClient);
        btnClient.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ClientStartActivity.class);
            startActivity(intent);
        });
    }
}
