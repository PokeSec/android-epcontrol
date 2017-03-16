/*
 * EPCJobScheduler.java : JobService for EPControl
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

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

public class EPCJobService extends JobService {

    public static EPCJobService mService;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        EPCJobService.mService = this;
        Log.i("EPCJobScheduler", "onStartJob");
        Intent service = new Intent(getApplicationContext(), EPCService.class);
        service.putExtra("broadcastIntentAction", "EPCJobService");
        WakeLockManager.startWakefulService(getApplicationContext(), service);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }

}
