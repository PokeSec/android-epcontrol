/*
 * MainActivity.java : Main Activity for EPControl
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

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends AppCompatActivity {

    private final int REQUEST_CODE_ENABLE_ADMIN = 0;
    private final int REQUEST_CODE_PERMISSIONS = 1;

    /* Fragments */
    private HomeFragment mHomeFragment;
    private DebugFragment mDebugFragment;

    /* Service binding related */
    private EPCService mEPCService;

    public ServiceConnection epcServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            mEPCService = ((EPCService.EPCServiceBinder) binder).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            mEPCService = null;
        }
    };

    public Handler epcServiceMessageHandler = new Handler() {
        public void handleMessage(Message message) {
            Bundle data = message.getData();
        }
    };

    private void requestAllPermissions() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        ComponentName deviceAdminComponentName = new ComponentName(this,
                EPCDeviceAdminReceiver.class);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponentName);
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_PERMISSIONS);
            ActivityCompat.requestPermissions(
                    this,
                    info.requestedPermissions,
                    REQUEST_CODE_PERMISSIONS);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("MainActivity", "onCreate start");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mHomeFragment = new HomeFragment();
        mDebugFragment = new DebugFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.frameLayout, mHomeFragment);
        ft.commit();

        requestAllPermissions();

        if (mEPCService == null) {
            Intent intent = new Intent(this, EPCService.class);
            Messenger messenger = new Messenger(epcServiceMessageHandler);
            intent.putExtra("MESSENGER", messenger);
            bindService(intent, epcServiceConnection, Context.BIND_AUTO_CREATE);
        }

        Log.d("MainActivity", "onCreate end");
    }

    @Override
    public void onResume(){
        Log.d("MainActivity", "onResume start");
        Intent serviceIntent = new Intent(this, EPCService.class);
        startService(serviceIntent);
        super.onResume();
        Log.d("MainActivity", "onResume end");
    }

    @Override
    public void onDestroy(){
        if (mEPCService != null) {
            unbindService(epcServiceConnection);
            mEPCService = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_home) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.frameLayout, mHomeFragment);
            ft.commit();
            return true;
        }
        else if (id == R.id.action_debug) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.frameLayout, mDebugFragment);
            ft.commit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void downloadAssets() {
        if (this.mEPCService != null) {
            mEPCService.downloadAssets();
        }
    }
}
