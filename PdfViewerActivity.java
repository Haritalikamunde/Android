package com.example.specialistapp;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PdfViewerActivity extends AppCompatActivity {

    private ImageView pdfImageView;
    private RadioGroup statusGroup;
    private EditText commentEditText;
    private Button submitButton;

    private String scanId;
    private String pdfPath;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor fileDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_pdf_viewer);

        pdfImageView = findViewById(R.id.pdfImageView);
        statusGroup = findViewById(R.id.decisionGroup);
        commentEditText = findViewById(R.id.commentInput);
        submitButton = findViewById(R.id.submitButton);
//        Button cancelButton = findViewById(R.id.cancelButton);

        scanId = getIntent().getStringExtra("scanId");
        if (scanId == null) {
            Toast.makeText(this, "Missing scan ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadPdfForScanId(scanId);

        submitButton.setOnClickListener(v -> {
            String comment = commentEditText.getText().toString().trim();

            if(comment.isEmpty()){
                Toast.makeText(this, "please enter comment", Toast.LENGTH_SHORT).show();
                return;
            }
            sendReportToServer(scanId, comment);
            updateCommentInJson(scanId, comment);
        });

    }
    @Override
    public void onBackPressed() {
        String comment = commentEditText.getText().toString().trim();

        if (comment.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Exit Confirmation")
                    .setMessage("You have not entered a comment. Do you want to close this report?")
                    .setPositiveButton("OK", (dialog, which) -> {
                        notifyServerPdfViewed(scanId, "closed", () -> {
                            if (!isFinishing() && !isDestroyed()) {
                                runOnUiThread(this::finish);
                            }
                        });
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .setCancelable(false)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private void updateCommentInJson(String scanId, String newComment) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ECGReports", "patientData.json");
            if (!file.exists()) return;

            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray jsonArray = new JSONArray(sb.toString());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                if (obj.getString("scanId").equals(scanId)) {
                    obj.put("comment", newComment);
                    break;
                }
            }

            // Save updated JSON back to file
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(jsonArray.toString(4).getBytes());  // formatted with indentation
            fos.close();

            // Notify success
            Toast.makeText(this, "Comment submitted", Toast.LENGTH_SHORT).show();

            // Notify PostLogin to refresh data
            Intent intent = new Intent("com.example.specialistapp.ECG_UPDATED");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Close and return to dashboard
            finish();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save comment", Toast.LENGTH_SHORT).show();
        }
    }


    private void loadPdfForScanId(String scanId) {
        try {
            File jsonFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ECGReports", "patientData.json");
            FileInputStream fis = new FileInputStream(jsonFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONArray jsonArray = new JSONArray(sb.toString());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                if (obj.getString("scanId").equals(scanId)) {
                    if (!obj.has("pdfPath")) {
                        Toast.makeText(this, "PDF path missing in JSON", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    pdfPath = obj.getString("pdfPath");
                    openPdf(new File(pdfPath));
                    return;
                }
            }

            Toast.makeText(this, "Scan ID not found", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void openPdf(File file) {
        try {
            Uri pdfUri = Uri.parse(pdfPath);
            fileDescriptor = getContentResolver().openFileDescriptor(pdfUri, "r");
            if (fileDescriptor != null) {
                pdfRenderer = new PdfRenderer(fileDescriptor);
                showPage(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPage(int index) {
        if (pdfRenderer.getPageCount() <= index) return;

        if (currentPage != null) currentPage.close();
        currentPage = pdfRenderer.openPage(index);

        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        pdfImageView.setImageBitmap(bitmap);

        currentPage.close();
    }

    private void sendReportToServer(String scanId, String comment) {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("Scan_id", scanId);
            json.put("comment", comment);
            Log.d("SEND_REPORT", "Sending scanId: " + scanId + ", comment: " + comment);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        RequestBody body = RequestBody.create(
                json.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url("http://123.201.117.218:7104/ecgapiAdvance/downloadreport")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(PdfViewerActivity.this, "Failed to submit report", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (response.isSuccessful()) {
                            Toast.makeText(PdfViewerActivity.this, "Report submitted successfully", Toast.LENGTH_SHORT).show();

                            Intent updateIntent = new Intent("com.example.specialistapp.ECG_UPDATED");
                            LocalBroadcastManager.getInstance(PdfViewerActivity.this).sendBroadcast(updateIntent);

                            finish();
//                            commentSuccess();
                        } else {
                            Toast.makeText(PdfViewerActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

//    private void commentSuccess(){
//            try {
//                // 1. Load JSON file
//                File jsonFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ECGReports", "patientData.json");
//                if (jsonFile.exists()) {
//                    FileInputStream fis = new FileInputStream(jsonFile);
//                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
//                    StringBuilder sb = new StringBuilder();
//                    String line;
//                    while ((line = reader.readLine()) != null) {
//                        sb.append(line);
//                    }
//                    reader.close();
//
//                    JSONArray jsonArray = new JSONArray(sb.toString());
//
//                    // 2. Find and remove the JSON object
//                    JSONArray updatedArray = new JSONArray();
//                    String pathToDelete = null;
//
//                    for (int i = 0; i < jsonArray.length(); i++) {
//                        JSONObject obj = jsonArray.getJSONObject(i);
//                        if (obj.getString("scanId").equals(scanId)) {
//                            // Store PDF path for deletion
//                            if (obj.has("pdfPath")) {
//                                pathToDelete = obj.getString("pdfPath");
//                            }
//                        } else {
//                            updatedArray.put(obj);  // Keep others
//                        }
//                    }
//
//                    // 3. Save updated JSON
//                    FileOutputStream fos = new FileOutputStream(jsonFile);
//                    fos.write(updatedArray.toString(4).getBytes());
//                    fos.close();
//
//                    // 4. Delete the PDF
//                    if (pathToDelete != null) {
//                        File pdfFile = new File(pathToDelete);
//                        if (pdfFile.exists()) {
//                            pdfFile.delete();
//                        }
//                    }
//                }
//
//                Toast.makeText(PdfViewerActivity.this, "Report submitted and removed", Toast.LENGTH_SHORT).show();
//
//                Intent updateIntent = new Intent("com.example.specialistapp.ECG_UPDATED");
//                LocalBroadcastManager.getInstance(PdfViewerActivity.this).sendBroadcast(updateIntent);
//
//                finish();
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                Toast.makeText(PdfViewerActivity.this, "Report submitted but failed to clean up", Toast.LENGTH_SHORT).show();
//                finish();
//            }
//    }

    private void notifyServerPdfViewed(String scanId, String status, Runnable onComplete) {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("scanId", scanId);
            json.put("status", status);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        RequestBody body = RequestBody.create(
                json.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url("http://123.201.117.218:7104/ecgapiAdvance/downloadreport")
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(PdfViewerActivity.this, "Failed to send status", Toast.LENGTH_SHORT).show();
                        if (onComplete != null) onComplete.run();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (response.isSuccessful()) {
                            Toast.makeText(PdfViewerActivity.this, "Status " + status + " sent to server", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PdfViewerActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                        if (onComplete != null) onComplete.run();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        try {
            if (currentPage != null) currentPage.close();
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}
