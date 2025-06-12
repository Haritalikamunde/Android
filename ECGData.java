package com.example.specialistapp;

public class ECGData {
    public String patientName;
    public String scanId;
    public String timestamp;
    public String pdfPath;
    public String serverStarus;

    public ECGData(String patientName, String scanId, String timestamp, String pdfPath, String serverStarus) {
        this.patientName = patientName;
        this.scanId = scanId;
        this.timestamp = timestamp;
        this.pdfPath = pdfPath;
        this.serverStarus = serverStarus;
    }

    @Override
    public String toString() {
        return patientName + " - " + timestamp;
    }
}
