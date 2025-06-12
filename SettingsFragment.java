package com.example.specialistapp;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view != null) {
            int padding = getResources().getDimensionPixelSize(R.dimen.preference_screen_padding);
            view.setPadding(padding, padding, padding, padding);
            view.setFitsSystemWindows(true);
        }

        return view;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        ListPreference orientationPref = findPreference("screen_orientation");
        if (orientationPref != null) {
            orientationPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (getActivity() != null) {
                    if ("landscape".equals(newValue)) {
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                }
                return true; // save the new value
            });
    }}
}

