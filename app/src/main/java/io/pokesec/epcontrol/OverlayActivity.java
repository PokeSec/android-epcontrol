/*
 * OverlayActivity.java : Overlay Activity for EPControl
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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


public class OverlayActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_overlay);

        // Get the reason for calling this activity
        Intent intent = getIntent();
        if (intent.hasExtra("reason")) {
            String reason = intent.getStringExtra("reason");
            TextView threatDetectedInfoView = (TextView) findViewById(R.id.threatDetectedInfoView);
            if (threatDetectedInfoView != null) {
                threatDetectedInfoView.setText(reason);
            }
        }

        View btnDismiss = findViewById(R.id.btn_dismiss);

        // Set up the user interaction to manually show or hide the system UI.
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });
        }
    }
}
