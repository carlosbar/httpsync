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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.widget.TextView;

public class HttpSyncActivity extends Activity {
	private			PowerManager.WakeLock		wl;
	private 		SharedPreferences			prefs;
	private			SharedPreferences.Editor	prefset;
    private 		Messenger 					mService = null;
	private			boolean						mBound;

	/* receive messages from server */
	private final	Messenger					mMessenger = new Messenger(new Handler() {
    	public void handleMessage(Message msg) {
    		switch(msg.what) {
    		case Httpd.ipcMsg_showMsg:
    			((TextView) findViewById(R.id.statusBar)).setText((String) msg.obj);
    			break;
    		}
		}
	});


    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null,Httpd.ipcMsg_registerClient);
                msg.replyTo = mMessenger;
            	mService.send(msg);
                msg = Message.obtain(null,Httpd.ipcMsg_startServer);
            	mService.send(msg);
            } catch(Exception e) {
            	e.printStackTrace();
            }
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            mBound = false;
        }
    };
	
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
    }

    @Override
    public void onBackPressed() {
    	// TODO Auto-generated method stub
    	super.onBackPressed();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
    
    
    @Override
    protected void onStart() {
    	// TODO Auto-generated method stub
    	super.onStart();
    	wl.acquire();
        bindService(new Intent(this, Httpd.class), mConnection,Context.BIND_AUTO_CREATE);
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
