/*
 * This file is part of the httpSync project.
 *
 * Copyright (C) 2011-2012 Carlos Barcellos <carlosbar@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301 USA
 */

package com.locus.httpsync;

import com.locus.httpsync.Httpd;
import com.locus.httpsync.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;

public class HttpSyncActivity extends Activity {
	private	PowerManager.WakeLock		wl;
	private SharedPreferences			prefs;
	private	SharedPreferences.Editor	prefset;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "smsListener");
		prefs = getSharedPreferences(Httpd.PREFS_DB, 0);
		prefset = prefs.edit();
		prefset.putInt("SERVER_PORT",8080);
		prefset.commit();

		Intent intent = new Intent(HttpSyncActivity.this,Httpd.class);
		startService(intent);
		
    }

    @Override
    public void onBackPressed() {
    	// TODO Auto-generated method stub
    	super.onBackPressed();
		Intent intent = new Intent(HttpSyncActivity.this, Httpd.class);
		HttpSyncActivity.this.stopService(intent);
    }
    
    
    @Override
    protected void onStart() {
    	// TODO Auto-generated method stub
    	super.onStart();
    	wl.acquire();
    }
    
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}    
    
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
    	wl.release();
	}    
}
