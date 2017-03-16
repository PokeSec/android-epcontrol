/*
 * EPCFirebaseMessagingService.java : Firebase Messaging service
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
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;


public class EPCFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        JSONObject obj = new JSONObject(remoteMessage.getData());
        Log.d("EPCFirebaseMessagingS", obj.toString());
        Intent service = new Intent(getApplicationContext(), EPCService.class);
        service.putExtra("broadcastIntentAction", "FCMMessage");
        service.putExtra("dataString", obj.toString());
        WakeLockManager.startWakefulService(getApplicationContext(), service);
    }
}
