package com.walkieoffline;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

public class MainActivity extends Activity {
    private static final String SERVICE_ID = "com.walkieoffline.service";
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final int REQ = 7;
    private static final int SAMPLE_RATE = 16000;
    private ConnectionsClient connections;
    private final List<String> endpoints = new ArrayList<>();
    private AudioRecord recorder;
    private volatile boolean transmitting = false;
    private ExecutorService audioExecutor;
    private AudioTrack player;
    private TextView status;
    private Button talk;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        connections = Nearby.getConnectionsClient(this);
        buildUi();
        requestPermissionsIfNeeded();
    }

    private void buildUi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 40, 32, 32);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("📻 Walkie Offline");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, 100));

        status = new TextView(this);
        status.setText("Desconectado");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        box.addView(status, new LinearLayout.LayoutParams(-1, 80));

        Button host = new Button(this);
        host.setText("Criar sala");
        host.setOnClickListener(v -> startAdvertising());
        box.addView(host, new LinearLayout.LayoutParams(-1, 70));

        Button find = new Button(this);
        find.setText("Procurar sala");
        find.setOnClickListener(v -> startDiscovery());
        box.addView(find, new LinearLayout.LayoutParams(-1, 70));

        talk = new Button(this);
        talk.setText("SEGURE PARA FALAR");
        talk.setTextSize(20);
        talk.setEnabled(false);
        talk.setOnTouchListener((v,e) -> {
            if (e.getAction()==MotionEvent.ACTION_DOWN) { startTalking(); return true; }
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) { stopTalking(); return true; }
            return true;
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, 180);
        tp.setMargins(0, 50, 0, 20);
        box.addView(talk,tp);

        TextView hint = new TextView(this);
        hint.setText("Funciona sem internet. Os celulares precisam estar próximos e com Bluetooth/Wi‑Fi local disponíveis.");
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint, new LinearLayout.LayoutParams(-1, 100));

        setContentView(box);
    }

    private void requestPermissionsIfNeeded() {
        ArrayList<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.BLUETOOTH_SCAN);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT <= 32) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ);
    }

    private boolean hasRecordPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startAdvertising() {
        connections.startAdvertising(
                "Walkie Offline", SERVICE_ID,
                lifecycleCallback,
                new com.google.android.gms.nearby.connection.AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener(v -> setStatus("Sala criada — esperando outro celular…"))
         .addOnFailureListener(e -> setStatus("Erro ao criar sala: " + e.getMessage()));
    }

    private void startDiscovery() {
        connections.startDiscovery(
                SERVICE_ID,
                discoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener(v -> setStatus("Procurando outro Walkie Offline…"))
         .addOnFailureListener(e -> setStatus("Erro ao procurar: " + e.getMessage()));
    }

    private final com.google.android.gms.nearby.connection.EndpointDiscoveryCallback discoveryCallback =
        new com.google.android.gms.nearby.connection.EndpointDiscoveryCallback() {
            @Override public void onEndpointFound(String id, com.google.android.gms.nearby.connection.DiscoveredEndpointInfo info) {
                connections.requestConnection("Walkie Offline", id, lifecycleCallback);
                setStatus("Aparelho encontrado — conectando…");
            }
            @Override public void onEndpointLost(String id) {}
        };

    private final ConnectionLifecycleCallback lifecycleCallback = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String id, ConnectionInfo info) {
            connections.acceptConnection(id, payloadCallback);
        }
        @Override public void onConnectionResult(String id, ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                if (!endpoints.contains(id)) endpoints.add(id);
                talk.setEnabled(true);
                setStatus("Conectado ✓");
            } else {
                setStatus("Falha na conexão");
            }
        }
        @Override public void onDisconnected(String id) {
            endpoints.remove(id);
            talk.setEnabled(!endpoints.isEmpty());
            setStatus(endpoints.isEmpty() ? "Desconectado" : "Conectado ✓");
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] data = payload.asBytes();
                if (data != null) play(data);
            }
        }
        @Override public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) {}
    };

    private void startTalking() {
        if (!hasRecordPermission() || endpoints.isEmpty() || transmitting) return;
        transmitting = true;
        talk.setText("FALANDO… SOLTE PARA PARAR");
        audioExecutor = Executors.newSingleThreadExecutor();
        audioExecutor.execute(() -> {
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min, 2048);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            byte[] buf = new byte[1024];
            recorder.startRecording();
            while (transmitting) {
                int n = recorder.read(buf,0,buf.length);
                if (n > 0) {
                    byte[] packet = new byte[n];
                    System.arraycopy(buf,0,packet,0,n);
                    for (String id : new ArrayList<>(endpoints)) connections.sendPayload(id, Payload.fromBytes(packet));
                }
            }
            recorder.stop();
            recorder.release();
            recorder=null;
        });
    }

    private void stopTalking() {
        if (!transmitting) return;
        transmitting = false;
        talk.setText("SEGURE PARA FALAR");
        if (audioExecutor != null) { audioExecutor.shutdownNow(); audioExecutor=null; }
    }

    private void play(byte[] data) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (player == null) {
                int min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                player = new AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min,4096), AudioTrack.MODE_STREAM);
                player.play();
            }
            player.write(data,0,data.length);
        });
    }

    private void setStatus(String s) {
        runOnUiThread(() -> status.setText(s));
    }

    @Override protected void onDestroy() {
        stopTalking();
        if (player != null) { player.stop(); player.release(); player=null; }
        connections.stopAdvertising();
        connections.stopDiscovery();
        super.onDestroy();
    }
}
