/*
 * HomeFragment.java : Home Fragment
 *
 * This file is part of EPControl.
 *
 * Copyright (C) 2016  Jean-Baptiste Galet & Timothe Aeberhardt
 *
 * EPControl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EPControl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with EPControl.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.pokesec.epcontrol;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.JsonWriter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;



public class HomeFragment extends Fragment {

    private Button mEnrollButton;

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        this.mEnrollButton = (Button) view.findViewById(R.id.enroll_button);
        updateEnrollButtonVisibility();
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Button enrollButton = (Button) view.findViewById(R.id.enroll_button);
        if(enrollButton != null) {
            enrollButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    enroll();
                    updateEnrollButtonVisibility();
                }
            });
        }
    }

    @Override
    public void onResume() {
        this.updateEnrollButtonVisibility();
        super.onResume();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        //FIXME: Better error handling
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(getActivity(), "Enrollment canceled", Toast.LENGTH_LONG).show();
            }
            else {
                try {
                    Uri uri = Uri.parse(result.getContents());
                    if (!uri.getScheme().equals("epcontrol"))
                        Toast.makeText(getActivity(), "Wrong QR Code provided", Toast.LENGTH_LONG).show();

                    FileOutputStream out = new FileOutputStream(getContext().getFilesDir() + "/user-settings.json");
                    JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
                    writer.beginObject();
                    for (String name : uri.getQueryParameterNames()) {
                        switch (name) {
                            case "INSTANCE_ID":
                                writer.name(name).value(uri.getQueryParameter(name));
                                break;
                            case "PROXY":
                                writer.name("PROXIES");
                                writer.beginObject();
                                writer.name("http").value(uri.getQueryParameter(name));
                                writer.name("https").value(uri.getQueryParameter(name));
                                writer.endObject();
                                break;
                            default:
                                Log.w("MainActivity", "Unexpected argument " + name + " in QR Code");
                                break;
                        }
                    }
                    writer.endObject();
                    writer.close();
                    out.close();
                    Toast.makeText(getActivity(), "QR Code successfully scanned, starting enrollment", Toast.LENGTH_LONG).show();
                    MainActivity mainActivity = (MainActivity)(getActivity());
                    mainActivity.downloadAssets();
                }
                catch (IOException e) {
                    Toast.makeText(getActivity(), "Enrollment failed", Toast.LENGTH_LONG).show();
                }
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    private void enroll() {
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setBeepEnabled(false);
        integrator.setPrompt("Please scan your enrollment barcode");
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void updateEnrollButtonVisibility() {
        File userSettings = new File(getContext().getFilesDir() + "/user-settings.json");
        if (userSettings.exists()) {
            mEnrollButton.setVisibility(View.GONE);
        }
    }

}
