package com.example.specialistapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.messaging.FirebaseMessaging;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;


public class MainActivity extends AppCompatActivity {
    EditText usernameText;
    EditText passwordText;
    Button loginButtonText;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            startActivity(new Intent(MainActivity.this, PostLogin.class));
            finish(); // Close the login activity
            return;
        }

        usernameText = findViewById(R.id.username);
        passwordText = findViewById(R.id.password);
        loginButtonText = findViewById(R.id.loginButton);

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        loginButtonText.setOnClickListener(v -> {
            String username = usernameText.getText().toString();
            String password = passwordText.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
            } else {
                sendCredentials(username, password);
            }
        });
    }

    public void sendCredentials(String username, String password) {
        OkHttpClient client = new OkHttpClient();

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("username", username);
            jsonObject.put("password", password);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("http://123.201.117.218:7104/Check_login")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        // Get FCM token after successful login
                        FirebaseMessaging.getInstance().getToken()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        String token = task.getResult();
                                        sendFcmTokenToDjango(username, token);
                                        Log.d("FCM Token", "Token after login: " + token);
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putBoolean("isLoggedIn", true);
                                        editor.apply();
                                        Toast.makeText(MainActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(MainActivity.this, PostLogin.class));
                                    } else {
                                        Log.w("FCM Token", "Failed to get token after login", task.getException());
                                        Toast.makeText(MainActivity.this, "Could not get FCM token", Toast.LENGTH_LONG).show();
                                    }
                                });
                    } else {
                        Toast.makeText(MainActivity.this, "Invalid Credentials", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    public void sendFcmTokenToDjango(String username, String token) {
        if (token == null) {
            Log.w("FCM", "Token is null, not sending to server");
            return;
        }

        OkHttpClient client = new OkHttpClient();

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("username", username);
            jsonObject.put("fcm_token", token);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("http://123.201.117.218:7104/save_fcm_token")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("FCM", "Failed to send FCM token", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("FCM", "Token sent response: " + response.body().string());
            }
        });
    }
}

