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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.locus.httpsync.R;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.Toast;


public class Httpd extends Service {
	public static final	String						PREFS_DB = "com.locus.httpSync.db";
	private 			SharedPreferences			prefs;
	private				SharedPreferences.Editor	prefset;
	private				Thread						tserver;
	private				boolean						exiting=false;
	private enum		msgType						{status};
	private				ServerSocket				serverSocket;
	private				boolean						started=false;
	private final		IBinder						mBinder = new LocalBinder();
	private final		Handler						handler = new Handler();

	/* http constants */
	
	private final		String						r200 = "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: text/html\r\n";
	private final		String						r400 = "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Type: text/html\r\n";
	private final		String						r404 = "HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Type: text/html\r\n";
	private final		String						endHeaders = "\r\n";
	
	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		Httpd getService() {
			return Httpd.this;
		}
	}

	@Override
	public void onCreate() {
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Toast.makeText(this, "teste",Toast.LENGTH_LONG).show();
		Log.d("httpSync","onStartCommand");
		if(!started) {
			try {
				prefs = getSharedPreferences(Httpd.PREFS_DB, 0);
				prefset = prefs.edit();
				prefset.putString("SERVER_IP", getLocalIpAddress());
				prefset.commit();
    			serverSocket = new ServerSocket(prefs.getInt("SERVER_PORT",8080),0,InetAddress.getByName(prefs.getString("SERVER_IP","127.0.0.1")));
				tserver = new Thread(new ServerThread());
				tserver.start();
				exiting=false;
				started=true;
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		exiting = true;
		started = false;
		if(serverSocket != null) {
			try {
				serverSocket.close();
				serverSocket = null;
			} catch(Exception e) {
				
			}
		}
	}
	
	public void sendMsg(msgType type,final String msg) {
		switch(type) {
		case status:
			handler.post(new Runnable() {
				
				@Override
				public void run() {
					Toast.makeText(Httpd.this,msg,Toast.LENGTH_LONG).show();
				}
			});
			break;
		}
	}
	
	private class ServerThread implements Runnable {
        public void run() {
           	while (!exiting) {
        		sendMsg(msgType.status,"Listening on IP: " + prefs.getString("SERVER_IP","127.0.0.1") + " port: "+prefs.getInt("SERVER_PORT",8080));
        		try {
        			Socket client = serverSocket.accept();
        			sendMsg(msgType.status,"received connection ip: " + client.getInetAddress().getHostAddress());
        			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream())); 
        			String line = null;
        			while ((line = in.readLine()) != null) {
        				if(line.startsWith("GET ")) {
        					/* verify get line */
        					String getLine[]=line.split(" ");
        					if(getLine.length < 3) {
        						out.write(r400 + endHeaders + getString(R.string.httpBadRequest));
           						out.flush();
        						break;
        					}
        					/* verify uri */
        					String uri[]=getLine[1].split("[?]");
        					if(uri.length < 1) {
        						out.write(r400 + endHeaders + getString(R.string.httpBadRequest));
           						out.flush();
        						break;
        					}
        					/* handle uri */
        					if(uri[0].equals("/")) {	/* home page */
        						
        						out.write(r200 + endHeaders + getString(R.string.app_name) + Httpd.this.getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        						
        					} else if(uri[0].equals("/contacts")) {	/* contacts */
        						String[] projection = new String[] {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
        						Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null);
        						int indexName = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
        						int indexNumber = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
        						String lastName = "";
        						out.write(r200 + endHeaders);
        						
        						cursor.moveToFirst();
        						if(!cursor.isAfterLast()) {
        							out.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
        							out.write("<html lang=\"en-US\" xml:lang=\"en-US\" xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        							out.write("<head>\n");
        							out.write("<title>Contacts</title>\n");
        							out.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n");
        							out.write("</head>\n");
        							out.write("</body>\n");
    								out.write("<ul>");
        							do {
        								if(lastName.equals(cursor.getString(indexName))) {
            								out.write(" [" + cursor.getString(indexNumber) + "]");
        								} else {
        									lastName = cursor.getString(indexName);
        									out.write("<li>" + lastName + " [" + cursor.getString(indexNumber)+ "]");
        								}
        							} while(cursor.moveToNext());
    								out.write("</ul>");
        							out.write("</body>\n");
        							out.write("</html>\n");
        						}
        						cursor.close();
           						out.flush();
        					} else {
        						out.write(r404 + endHeaders + getString(R.string.httpNotFound));
           						out.flush();
        					}
       						out.flush();
        					break;
        				}
        			}
        			sendMsg(msgType.status,"closed connection to ip: " + client.getInetAddress().getHostAddress());
    		        client.close();
        		} catch (Exception e) {
        			sendMsg(msgType.status,"Connection interrupted");
		        }
        	}
        }
    }

	/**
	 * get device WiFi ip address
	 * @return the ipv4 ip address or localhost if not found
	 */
    private String getLocalIpAddress() {
    	try {
    		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
    		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    		int ipAddress = wifiInfo.getIpAddress();
        	return String.format("%d.%d.%d.%d",(ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
    	return "127.0.0.1";
    }    
}
