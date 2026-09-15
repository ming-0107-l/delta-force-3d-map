package com.deltamap.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView webView;
    private ServerSocket serverSocket;
    private Thread serverThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        setContentView(webView);

        startServer();
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(0);
                int port = serverSocket.getLocalPort();

                runOnUiThread(() ->
                        webView.loadUrl("http://127.0.0.1:" + port + "/index.html")
                );

                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> handleRequest(socket)).start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverThread.start();
    }

    private void handleRequest(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            String requestLine = reader.readLine();

            if (requestLine == null) {
                socket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";

            while (true) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) break;
            }

            if (path.equals("/")) {
                path = "/index.html";
            }

            if (path.contains("?")) {
                path = path.substring(0, path.indexOf("?"));
            }

            if (path.contains("..")) {
                send404(socket);
                return;
            }

            String assetPath = path.substring(1);

            InputStream input = getAssets().open(assetPath);

            byte[] data = readAll(input);
            input.close();

            String contentType = getContentType(assetPath);

            OutputStream output = socket.getOutputStream();

            String header =
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";

            output.write(header.getBytes("UTF-8"));
            output.write(data);
            output.flush();

            socket.close();

        } catch (Exception e) {
            try {
                send404(socket);
            } catch (Exception ignored) {
            }
        }
    }

    private void send404(Socket socket) throws IOException {
        byte[] data = "404 Not Found".getBytes("UTF-8");

        OutputStream output = socket.getOutputStream();

        String header =
                "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Connection: close\r\n\r\n";

        output.write(header.getBytes("UTF-8"));
        output.write(data);
        output.flush();

        socket.close();
    }

    private byte[] readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();

        int count;

        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }

        return output.toByteArray();
    }

    private String getContentType(String path) {
        String lower = path.toLowerCase();

        if (lower.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }

        if (lower.endsWith(".js")) {
            return "application/javascript";
        }

        if (lower.endsWith(".css")) {
            return "text/css";
        }

        if (lower.endsWith(".json")) {
            return "application/json";
        }

        if (lower.endsWith(".bin")) {
            return "application/octet-stream";
        }

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }

        if (webView != null) {
            webView.destroy();
        }
    }
}
