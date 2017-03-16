/*
 * DebugFragment.java : Debug Fragment
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

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


public class DebugFragment extends Fragment {

    View mFragmentView;

    public DebugFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_debug, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFragmentView = view;
        Button refreshLogsButton = (Button) view.findViewById(R.id.refreshLogsButton);
        if(refreshLogsButton != null) {
            refreshLogsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refreshLogView();
                }
            });
        }

        Button refreshTasksButton = (Button) view.findViewById(R.id.refreshTasksButton);
        if(refreshTasksButton != null) {
            refreshTasksButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(EPCService.mService != null)
                        EPCService.mService.forceTasksRefresh();
                }
            });
        }
        refreshLogView();
    }

    private void refreshLogView() {
        final TextView logTextView = (TextView) mFragmentView.findViewById(R.id.logTextView);
        if (logTextView != null) {
            logTextView.setText(getText(R.string.msg_refreshinglogs));
            new AsyncTask<Void, Void, StringBuilder>() {

                @Override
                protected StringBuilder doInBackground(Void... params) {
                    File file = new File(getActivity().getFilesDir(), "epcontrol.log");
                    ArrayList<String> lines = new ArrayList<>();
                    StringBuilder log = new StringBuilder();
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String line;
                        while ((line = br.readLine()) != null) {
                            lines.add(0, line + '\n');
                        }
                        for (int i = 0; i < Math.min(lines.size(), 200); ++i) {
                            log.append(lines.get(i));
                        }
                    } catch (FileNotFoundException e) {
                        return new StringBuilder(getString(R.string.msg_error_reading_log));
                    } catch (IOException e) {
                        return new StringBuilder(getString(R.string.msg_nologs));
                    }
                    return log;
                }

                @Override
                protected void onPostExecute(final StringBuilder log) {
                    TextView logTextView = (TextView) mFragmentView.findViewById(R.id.logTextView);
                    if (logTextView != null)
                        logTextView.setText(log);
                }

            }.execute();
        }
    }
}
