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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.locus.httpsync.R;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
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
	private				boolean						running=false;
	private enum		msgType						{status};
	private				ServerSocket				serverSocket;
	private				Messenger					clientMessenger = null;

	/* http constants */
	
	private final		String						r200 		= "HTTP/1.0 200 OK\r\nConnection: close\r\nContent-Type: text/html\r\n";
	private final		String						r200jpg		= "HTTP/1.0 200 OK\r\nConnection: close\r\nContent-Type: image/jpeg\r\n";
	private final		String						r400 		= "HTTP/1.0 400 Bad Request\r\nConnection: close\r\nContent-Type: text/html\r\n";
	private	final		String						r404 		= "HTTP/1.0 404 Not Found\r\nConnection: close\r\nContent-Type: text/html\r\n";
	private	final		String						endHeaders	= "\r\n";

	/* messages from client */
	
	public	static	final	int						ipcMsg_registerClient = 0;
	public	static	final	int						ipcMsg_showMsg = 1;
	public	static	final	int						ipcMsg_startServer = 2;
	
    private final		Messenger					mMessenger = new Messenger(new Handler() {
    	public void handleMessage(Message msg) {
    	
	    	switch(msg.what) {
	    	case ipcMsg_registerClient:
	    		clientMessenger = msg.replyTo;
	   			break;
	    	case ipcMsg_startServer:
	    		try {
	    			serverSocket = new ServerSocket(prefs.getInt("SERVER_PORT",8080),0,InetAddress.getByName(prefs.getString("SERVER_IP","127.0.0.1")));
	    			tserver = new Thread(new ServerThread());
	    			tserver.start();
	    		} catch(Exception e) {
	    			if(running) {
	    				sendMsg(msgType.status,"Listening on IP: " + prefs.getString("SERVER_IP","127.0.0.1") + " port: "+prefs.getInt("SERVER_PORT",8080));
	    			}
	    			e.printStackTrace();
	    		}
	    		break;
	    	}
    	}
    	
	});
	
	@Override
	public void onCreate() {
		Toast.makeText(this,"teste",Toast.LENGTH_LONG).show();

		prefs = getSharedPreferences(Httpd.PREFS_DB, 0);
		prefset = prefs.edit();
		prefset.putString("SERVER_IP", getLocalIpAddress());
		prefset.commit();
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		running = false;
		if(serverSocket != null) {
			try {
				serverSocket.close();
				serverSocket = null;
			} catch(Exception e) {
				
			}
		}
	}
	
	public void sendMsg(msgType type,final String strmsg) {
		switch(type) {
		case status:
			if(clientMessenger != null) {
				Message msg = Message.obtain(null,ipcMsg_showMsg,strmsg);
				try {
					clientMessenger.send(msg);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
			break;
		}
	}
	
	private void sendBuffer(OutputStream out,byte[] b)
	{
		try {
			out.write(b);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void sendBuffer(OutputStream out,String str)
	{
		try {
			sendBuffer(out,str.getBytes());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private class ServerThread implements Runnable {
        public void run() {
			running=true;
           	while (running) {
        		try {
        			Socket client = serverSocket.accept();
        			sendMsg(msgType.status,"received connection ip: " + client.getInetAddress().getHostAddress());
        			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        			OutputStream out = client.getOutputStream(); 
        			String line = null;
        			while ((line = in.readLine()) != null) {
        				if(line.startsWith("GET ")) {
        					/* verify get line */
        					String getLine[]=line.split(" ");
        					if(getLine.length < 3) {
        						sendBuffer(out,r400 + endHeaders + getString(R.string.httpBadRequest));
           						out.flush();
        						break;
        					}
        					/* verify uri */
        					String uri[]=getLine[1].split("[?]");
        					if(uri.length < 1) {
        						sendBuffer(out,r400 + endHeaders + getString(R.string.httpBadRequest));
           						out.flush();
        						break;
        					}
        					/* handle uri */
        					if(uri[0].equals("/")) {	/* home page */
        						sendBuffer(out,r200 + endHeaders + getString(R.string.app_name) + Httpd.this.getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        					} else if(uri[0].startsWith("/cimg/")) {	/* image from contact */
        						byte[] 		img=null;
        						int 		photoId = Integer.parseInt(uri[0].split("/")[2].split("[.]")[0]);
        					    Uri 		imguri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, photoId);
        					    Cursor		c = getContentResolver().query(imguri, new String[] {ContactsContract.CommonDataKinds.Photo.PHOTO}, null, null, null);
        					    
        					    try {
        				            if (c.moveToFirst()) img = c.getBlob(c.getColumnIndex(ContactsContract.CommonDataKinds.Photo.PHOTO));
        					    } catch(Exception e) {
        					    	img = null;
        					    }

        					    if(img != null) {
        					    	sendBuffer(out,r200jpg);
        					    	sendBuffer(out,"Content-Disposition: inline; " + photoId + ".png\r\n");
        					    	sendBuffer(out,"Content-Length: " + img.length + "\r\n" + endHeaders);
        					    	sendBuffer(out,img);
        					    } else {
            						sendBuffer(out,r404 + endHeaders + getString(R.string.httpNotFound));
               						out.flush();
        					    }
        					} else if(uri[0].equals("/contacts")) {	/* contacts */
        						String[] projection = new String[] {ContactsContract.Data.RAW_CONTACT_ID,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.Contacts.PHOTO_ID};
        						Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC;");
        						//int indexID = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
        						int indexName = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
        						int indexNumber = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
        						int indexPhotoId = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_ID);
        						String lastName = "";
        						sendBuffer(out,r200 + endHeaders);
        						cursor.moveToFirst();
    							/* get contacts html file */
    							InputStream f = getAssets().open("contacts.html");
    							if(f != null) {
    								byte[]	b = new byte[512];
    								int		sz=0;
    								
    								do {
    									sz=f.read(b);
    									if(sz > 0) sendBuffer(out,b);
    								} while(sz >= 0);
    								f.close();
    							}
    							/* write records count */
        						sendBuffer(out,"<div>"+ cursor.getCount() + " records</div></br>");
        						if(!cursor.isAfterLast()) {
        							/* now insert all contact names */
    								sendBuffer(out,"<ul>");
          							do {
        								if(lastName.equals(cursor.getString(indexName))) {
            								sendBuffer(out," [" + cursor.getString(indexNumber) + "]");
        								} else {
        									lastName = cursor.getString(indexName);
        									int id = cursor.getInt(indexPhotoId); 
        									if(id > 0) {
        										sendBuffer(out,"<li><img align=\"middle\" src=\"/cimg/" + id + ".jpg\"/> "  + lastName + " [" + cursor.getString(indexNumber)+ "]");
        									} else {
        										sendBuffer(out,"<li>" + lastName + " [" + cursor.getString(indexNumber)+ "]");
        									}
        								}
        							} while(cursor.moveToNext());
          							/* end html file */
    								sendBuffer(out,"</ul>");
        							sendBuffer(out,"</body>\n");
        							sendBuffer(out,"</html>\n");
        						}
        						cursor.close();
        					} else {
        						sendBuffer(out,r404 + endHeaders + getString(R.string.httpNotFound));
        					}
        					break;
        				}
        			}
        			sendMsg(msgType.status,"closed connection to ip: " + client.getInetAddress().getHostAddress());
        			client.shutdownOutput();
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
