package com.example.specialistapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_layout);

        View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        v.getPaddingLeft(),
                        systemBars.top,
                        v.getPaddingRight(),
                        v.getPaddingBottom()
                );
                return insets;
            });
        }
    }


    // Load menu with settings icon
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // Handle settings icon click
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Inject child activity layout into base
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.base_layout); // Load base layout first

        FrameLayout contentFrame = findViewById(R.id.content_frame);
        if (contentFrame != null) {
            getLayoutInflater().inflate(layoutResID, contentFrame, true); // Inject child layout
        }
    }

}
