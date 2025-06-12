package com.example.specialistapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.BufferedReader;
import android.content.Context;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String NORMAL_CHANNEL_ID = "normal_channel";
    private static final String EMERGENCY_CHANNEL_ID = "emergency_channel";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d("FCM", "Message received: " + remoteMessage.getData().toString());
        createNotificationChannels();

        Map<String, String> data = remoteMessage.getData();

        String title = data.get("title") != null ? data.get("title") : "New ECG Report";
        String body = data.get("body") != null ? data.get("body") : "You have a new ECG report.";
        String sound = data.get("sound");
        String patientName = data.get("patient_name");
        String ecgPdf = data.get("ecg_pdf");
        String scanId = data.get("scan_id");
        String timestamp = data.get("timestamp");

        Log.d("FCM Data", "Received data - Patient: " + patientName + ", Scan ID: " + scanId + ", Timestamp: " + timestamp + ", ECG PDF URL: " + ecgPdf);

        String selectedChannel = "normal".equals(sound) ? NORMAL_CHANNEL_ID : EMERGENCY_CHANNEL_ID;

        if (ecgPdf != null && scanId != null && patientName != null && timestamp != null) {
            String fileName = "patient_ecg_" + scanId + ".pdf";
            downloadPdf(ecgPdf, fileName, patientName, scanId, timestamp);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, selectedChannel)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
    }

    private void downloadPdf(String url, String fileName, String patientName, String scanId, String timestamp) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e("PDF", "Failed response: " + response.code());
                    return;
                }

                byte[] pdfBytes = response.body().bytes();
                String savedPath = saveEcgPdf(pdfBytes, fileName);

                if (savedPath != null) {
                    writePatientJson(MyFirebaseMessagingService.this, patientName, scanId, timestamp, savedPath);
                } else {
                    Log.e("PDF", "Failed to save PDF.");
                }
            }

            public void onFailure(Call call, IOException e) {
                Log.e("PDF", "Download failed: " + e.getMessage());
            }
        });
    }

    //Chooses appropriate storage method
    private String saveEcgPdf(byte[] pdfBytes, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return savePdfToDownloadsScopedStorage(pdfBytes, fileName);
        } else {
            // For legacy, try both methods - MediaStore and direct file access
            String path = savePdfViaMediaStore(pdfBytes, fileName);
            if (path == null) {
                path = savePdfToDownloadsLegacy(pdfBytes, fileName);
            }
            return path;
        }
    }


    //Scoped storage for Android 10+
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private String savePdfToDownloadsScopedStorage(byte[] pdfBytes, String fileName) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ECGReports");

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        if (uri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                outputStream.write(pdfBytes);
                return uri.toString();
            } catch (IOException e) {
                Log.e("PDF", "Error writing PDF via MediaStore", e);
                resolver.delete(uri, null, null); // Clean up if failed
            }
        }
        return null;
    }

    //MediaStore method for older Android versions
    private String savePdfViaMediaStore(byte[] pdfBytes, String fileName) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ECGReports");

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        if (uri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                outputStream.write(pdfBytes);
                return uri.toString();
            } catch (IOException e) {
                Log.e("PDF", "Error writing PDF via MediaStore", e);
                resolver.delete(uri, null, null); // Clean up if failed
            }
        }
        return null;
    }

//    Direct filesystem fallback
    private String savePdfToDownloadsLegacy(byte[] pdfBytes, String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File ecgDir = new File(downloadsDir, "ECGReports");

        if (!ecgDir.exists()) {
            if (!ecgDir.mkdirs()) {
                Log.e("PDF", "Failed to create ECGReports directory in Downloads");
                return null;
            }
        }

        File file = new File(ecgDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(pdfBytes);
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e("PDF", "Error writing PDF", e);
            return null;
        }
    }
    private void writePatientJson(Context context, String name, String scanId, String timestamp, String pdfPath) {
        JSONArray jsonArray = null;
        try {
            JSONObject newRecord = new JSONObject();
            newRecord.put("patientName", name);
            newRecord.put("scanId", scanId);
            newRecord.put("timestamp", timestamp);
            newRecord.put("pdfPath", pdfPath);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri jsonUri = null;

                try (Cursor cursor = resolver.query(collection, null,
                        MediaStore.Downloads.DISPLAY_NAME + "=?", new String[]{"patientData.json"}, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                        jsonUri = ContentUris.withAppendedId(collection, id);
                    }
                }

                if (jsonUri == null) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, "patientData.json");
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ECGReports");

                    jsonUri = resolver.insert(collection, values);
                }

                if (jsonUri != null) {
                    try (InputStreamReader reader = new InputStreamReader(resolver.openInputStream(jsonUri));
                         BufferedReader br = new BufferedReader(reader)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        if (!sb.toString().isEmpty()) {
                            jsonArray = new JSONArray(sb.toString());
                        }
                    } catch (Exception e) {
                        Log.w("JSON", "No existing JSON or malformed, starting fresh.");
                    }

                    if (jsonArray == null) {
                        jsonArray = new JSONArray();
                    }

                    for (int i = 0; i < jsonArray.length(); i++) {
                        if (scanId.equals(jsonArray.getJSONObject(i).getString("scanId"))) {
                            Log.d("JSON", "Duplicate scanId. Skipping insert.");
                            return;
                        }
                    }

                    jsonArray.put(newRecord); // Append instead of overwriting

                    try (OutputStream os = resolver.openOutputStream(jsonUri, "wt")) {
                        os.write(jsonArray.toString().getBytes());
                        os.flush();
                        Log.d("JSON", "Successfully wrote JSON to: " + jsonUri);
                    }
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File ecgDir = new File(downloadsDir, "ECGReports");
                if (!ecgDir.exists()) ecgDir.mkdirs();
                File file = new File(ecgDir, "patientData.json");

                if (file.exists()) {
                    try (FileInputStream fis = new FileInputStream(file);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        if (!sb.toString().isEmpty()) {
                            jsonArray = new JSONArray(sb.toString());
                        }
                    } catch (Exception e) {
                        Log.w("JSON", "Failed to read existing legacy JSON, creating new.");
                    }
                }

                if (jsonArray == null) {
                    jsonArray = new JSONArray();
                }

                for (int i = 0; i < jsonArray.length(); i++) {
                    if (scanId.equals(jsonArray.getJSONObject(i).getString("scanId"))) {
                        Log.d("JSON", "Duplicate scanId. Skipping insert.");
                        return;
                    }
                }

                jsonArray.put(newRecord); // Append instead of overwriting

                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(jsonArray.toString().getBytes());
                    fos.flush();
                    Log.d("JSON", "Legacy JSON written to: " + file.getAbsolutePath());
                }
            }

            // Notify update
            Intent intent = new Intent("com.example.specialistapp.ECG_UPDATED");
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        } catch (Exception e) {
            Log.e("JSON", "Error writing patientData.json: " + e.getMessage(), e);
        }
    }

    private void createNotificationChannels() {
//        Build.VERSION.SDK_INT = Current version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            NotificationChannel normalChannel = new NotificationChannel(
                    NORMAL_CHANNEL_ID,
                    "Normal Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            normalChannel.setDescription("Normal ECG Notifications");
            normalChannel.setSound(Uri.parse("android.resource://" + getPackageName() + "/raw/normal"), null);

            NotificationChannel emergencyChannel = new NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "Emergency Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            emergencyChannel.setDescription("Emergency ECG Notifications");
            emergencyChannel.setSound(Uri.parse("android.resource://" + getPackageName() + "/raw/emergency"), null);

            if (manager != null) {
                manager.createNotificationChannel(normalChannel);
                manager.createNotificationChannel(emergencyChannel);
            }
        }
    }
}
