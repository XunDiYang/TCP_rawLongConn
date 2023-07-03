package com.socket.tcp_rawlongconn.client.view;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.socket.tcp_rawlongconn.R;
import com.socket.tcp_rawlongconn.client.callback.DataCallback;
import com.socket.tcp_rawlongconn.client.callback.ErrorCallback;
import com.socket.tcp_rawlongconn.client.callback.WritingCallback;
import com.socket.tcp_rawlongconn.client.service.LongLiveSocket;
import com.socket.tcp_rawlongconn.model.CMessage;
import com.socket.tcp_rawlongconn.model.MsgType;

public class ClientActivity extends AppCompatActivity {
    private String TAG = "CLIENT";
    private String localIp;
    private String serverIp;
    private int serverPort;
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
        serverPort = intent.getIntExtra("serverPort", 8888);

        TextView txtlocalIp = findViewById(R.id.localip);
        txtlocalIp.setText(ipPortToString());

        LongLiveSocket clientThread = new LongLiveSocket(localIp,
                serverIp, serverPort,
                dataCallback,
                errorCallback);
        clientThread.start();


        txtSndMsg = findViewById(R.id.sndMsg);
        txtRcvMsg = findViewById(R.id.rcvMsg);
        txtRcvMsg.setMovementMethod(ScrollingMovementMethod.getInstance());
        Button btnSndMsg = findViewById(R.id.btnSndMsg);

        btnSndMsg.setOnClickListener(v -> {
            String sndMsg = txtSndMsg.getText().toString();
            if (TextUtils.isEmpty(sndMsg)) {
                return;
            }
            CMessage cMessage = new CMessage(localIp, serverIp, 200, MsgType.TEXT, sndMsg);
            WritingCallback writingCallback = new WritingCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "onSuccess: 发送成功");
                }

                @Override
                public void onFail(CMessage cMsg) {
                    Log.w(TAG, "onFail: fail to write: " + cMsg.toJsonStr());
                }
            };
//            TODO: 如何调用write，估计用handler
            clientThread.write(cMessage, writingCallback);
        });


    }

    private DataCallback dataCallback = (cMsg) -> {
        txtSndMsg.setText("");
        Log.i(TAG, "EchoClient: received: " + cMsg.toString());
        if (cMsg.getCode() == 200) {
            Toast.makeText(ClientActivity.this, "收到回复", Toast.LENGTH_SHORT).show();
            if (cMsg.getType() == MsgType.TEXT && !cMsg.getMsg().isEmpty()) {
                String txt = "服务器反馈：" + cMsg.getMsg() + "\n" + txtRcvMsg.getText().toString();
                txtRcvMsg.setText(txt);
            }
        } else {
            Toast.makeText(ClientActivity.this, "服务器端错误", Toast.LENGTH_SHORT).show();
        }
    };

    private ErrorCallback errorCallback = ()->{
        Toast.makeText(ClientActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
        finish();
    };

    public String ipPortToString() {
        return
                "client ip='" + localIp + '\'' +
                        "\nserver ip='" + serverIp + '\'' +
                        ", port=" + serverPort;
    }
}
