package com.example.specialistapp;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.net.Uri;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Handler;


public class PostLogin extends AppCompatActivity {

    Button viewEcgBtn;
    TextView pendingCountTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_login);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        pendingCountTextView = findViewById(R.id.pendingCountTextView);
        viewEcgBtn = findViewById(R.id.btnViewEcg);

        updatePendingEcgCount();
        synkECGWithServer();

        viewEcgBtn.setOnClickListener(v -> {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File ecgDir = new File(downloadsDir, "ECGReports");
                File file = new File(ecgDir, "patientData.json");

                if (!file.exists()) {
                    Toast.makeText(this, "No ECG data found.", Toast.LENGTH_SHORT).show();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }

                JSONArray jsonArray = new JSONArray(sb.toString());
                if (jsonArray.length() == 0) {
                    Toast.makeText(this, "ECG list is empty.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Find the first ECG with serverStatus = "pending"
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String serverStatus = obj.optString("serverStatus", "");
                    if (serverStatus.equalsIgnoreCase("Report pending")) {
                        String scanId = obj.getString("scanId");

                        Intent intent = new Intent(this, PdfViewerActivity.class);
                        intent.putExtra("scanId", scanId);
                        startActivity(intent);
                        notifyServerPdfViewed(scanId, "opened");  // send status report to server
                        return;
                    }
                }

                Toast.makeText(this, "No pending ECG reviews found.", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to open ECG.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void updatePendingEcgCount() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File ecgDir = new File(downloadsDir, "ECGReports");
            File file = new File(ecgDir, "patientData.json");

            if (!file.exists()) {
                pendingCountTextView.setText("Pending ECG Count: 0");
                Log.w("ECG_LOG", "patientData.json not found.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String jsonContent = sb.toString();
            Log.d("ECG_LOG", "JSON content: " + jsonContent);

            JSONArray jsonArray;
            try {
                jsonArray = new JSONArray(jsonContent);
            } catch (Exception je) {
                Log.e("ECG_LOG", "Invalid JSON in patientData.json", je);
                pendingCountTextView.setText("Pending ECG Count: Invalid JSON");
                return;
            }

            int pendingCount = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject ecg = jsonArray.getJSONObject(i);
                if (!ecg.has("comment") || ecg.getString("comment").isEmpty()) {
                    pendingCount++;
                }
            }

            pendingCountTextView.setText(" ECG Pending for review : " + pendingCount);
            Log.d("ECG_LOG", "Pending count updated: " + pendingCount);

        } catch (Exception e) {
            Log.e("ECG_LOG", "Unexpected error in updatePendingEcgCount", e);
            pendingCountTextView.setText("Pending ECG Count: Error");
        }
    }


    private void notifyServerPdfViewed(String scanId, String status) {
        OkHttpClient client=new OkHttpClient();

        JSONObject json=new JSONObject();
        try{
            json.put("scanId",scanId);
            json.put("status",status);
        }
        catch (Exception e){
            e.printStackTrace();
            return;
        }

        RequestBody body=RequestBody.create(
                json.toString(), MediaType.parse("Application/json"));

        Request request=new Request.Builder()
                .url("http://123.201.117.218:7104/ecgapiAdvance/downloadreport")
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(PostLogin.this, "Failed to send status", Toast.LENGTH_SHORT).show();
//                        if (onComplete != null) onComplete.run();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (response.isSuccessful()) {
                            Toast.makeText(PostLogin.this, "Status " + status + " sent to server", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PostLogin.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
//                        if (onComplete != null) onComplete.run();
                    }
                });
            }
        });
    }

    public void synkECGWithServer() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("http://123.201.117.218:7104/scan_id_status_list")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Failed to fetch ECG list", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;

                String responseBody = response.body().string();
                Log.d("SERVER_RESPONSE", "Response body: " + responseBody);
                try {
                    JSONObject responseObject = new JSONObject(responseBody);
                    JSONArray serverECGs = responseObject.getJSONArray("response");

                    Map<String, String> scanIdStatusMap = new HashMap<>();

                    for (int i = 0; i < serverECGs.length(); i++) {
                        JSONObject ecg = serverECGs.getJSONObject(i);
                        String scanId = ecg.getString("scanId");
                        String status = ecg.has("scan_status") ? ecg.getString("scan_status") : "unknown";

                        scanIdStatusMap.put(scanId, status);
                    }

                    // Clean up unmatched ECGs (only those not in the list from server)
                    cleanUpUnmatchedECGs(getApplicationContext(), scanIdStatusMap.keySet());

                    // Update local status
                    for (Map.Entry<String, String> entry : scanIdStatusMap.entrySet()) {
                        updateserverStatusInJson(entry.getKey(), entry.getValue());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("PostLogin", "Error syncing ECGs", e);
                }
            }
        });
    }


    private void updateserverStatusInJson(String scanId, String newStatus) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ECGReports", "patientData.json");
        Log.d("ECG_UPDATE", "Updating scanId: " + scanId + " with status: " + newStatus);
        Log.d("ECG_UPDATE", "Using file path: " + file.getAbsolutePath());

        if (!file.exists()) {
            Log.w("ECG_UPDATE", "File does not exist: " + file.getAbsolutePath());
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            reader.close();
            fis.close();

            JSONArray jsonArray = new JSONArray(jsonBuilder.toString());
            boolean updated = false;

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String currentScanId = obj.optString("scanId", "").trim();

                if (currentScanId.equals(scanId.trim())) {
                    obj.put("serverStatus", newStatus);
                    updated = true;
                    Log.d("ECG_UPDATE", "Updated serverStatus for scanId: " + scanId);
                    break;
                }
            }

            if (updated) {
                FileOutputStream fos = new FileOutputStream(file);
                String updatedJson = jsonArray.toString(4); // pretty print
                fos.write(updatedJson.getBytes());
                fos.flush();
                fos.close();

                Log.d("ECG_UPDATE", "Successfully wrote updated JSON to file.");
                Log.d("ECG_UPDATE", "Updated JSON:\n" + updatedJson);

                // Send broadcast to update UI
                Intent intent = new Intent("com.example.specialistapp.ECG_UPDATED");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                Log.d("ECG_UPDATE", "Broadcast sent: ECG_UPDATED");
            } else {
                Log.w("ECG_UPDATE", "scanId not found in local JSON: " + scanId);
            }

        } catch (Exception e) {
            Log.e("ECG_UPDATE", "Error updating serverStatus in JSON", e);
        }
    }

    public void cleanUpUnmatchedECGs(Context context, Set<String> validScanIds) {
        JSONArray updatedArray = new JSONArray();

        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File ecgDir = new File(downloadsDir, "ECGReports");
            File jsonFile = new File(ecgDir, "patientData.json");

            if (!jsonFile.exists()) {
                Log.d("CLEANUP", "No patientData.json found, skipping cleanup.");
                return;
            }

            // Read JSON file
            JSONArray jsonArray;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile)))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                jsonArray = new JSONArray(sb.toString());
            }

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String scanId = obj.getString("scanId");

                if (validScanIds.contains(scanId)) {
                    updatedArray.put(obj);
                } else {
                    String path = obj.optString("pdfPath");
                    if (path != null && !path.isEmpty()) {
                        if (path.startsWith("content://")) {
                            Uri pdfUri = Uri.parse(path);
                            try {
                                int rowsDeleted = context.getContentResolver().delete(pdfUri, null, null);
                                Log.d("CLEANUP", "Deleted URI: " + path + " - Rows: " + rowsDeleted);
                            } catch (Exception e) {
                                Log.e("CLEANUP", "Error deleting URI: " + path, e);
                            }
                        } else {
                            File pdf = new File(path);
                            if (pdf.exists()) {
                                boolean deleted = pdf.delete();
                                Log.d("CLEANUP", "Deleted file: " + path + " - " + deleted);
                            }
                        }
                    }
                }
            }

            // Write updated JSON array back to file
            try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                fos.write(updatedArray.toString(4).getBytes());  // pretty print
                fos.flush();
            }

            Log.d("CLEANUP", "Cleanup complete. Updated patientData.json.");

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("CLEANUP", "Failed to clean up ECGs", e);
        }
    }

    // Helper method for MediaStore deletions
    private void deleteUsingMediaStore(Context context, long fileId) {
        ContentResolver resolver = context.getContentResolver();
        Uri contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

        try {
            Uri fileUri = ContentUris.withAppendedId(contentUri, fileId);
            int deleted = resolver.delete(fileUri, null, null);
            if (deleted > 0) {
                Log.d("CLEANUP", "Successfully deleted via MediaStore: " + fileId);
            } else {
                Log.d("CLEANUP", "No files deleted via MediaStore for: " + fileId);
            }
        } catch (Exception e) {
            Log.e("CLEANUP", "MediaStore deletion failed", e);
        }
    }

    private final BroadcastReceiver ecgUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePendingEcgCount(); // update UI when sync happens
        }
    };

    // Refresh count when coming back to this screen
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            synkECGWithServer();
            handler.postDelayed(this, 10000); // repeat every 10 sec
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        updatePendingEcgCount();
        handler.post(syncRunnable);
        LocalBroadcastManager.getInstance(this).registerReceiver(ecgUpdateReceiver,
                new IntentFilter("com.example.specialistapp.ECG_UPDATED"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(syncRunnable);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ecgUpdateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            getSharedPreferences("MyPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("isLoggedIn", false)
                    .apply();

            Intent intent = new Intent(PostLogin.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
