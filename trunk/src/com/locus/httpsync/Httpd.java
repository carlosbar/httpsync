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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import com.locus.httpsync.R;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContacts.Entity;


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
	private final		String						r200xml		= "HTTP/1.0 200 OK\r\nConnection: close\r\nContent-Type: text/xml\r\n";
	private final		String						r200raw		= "HTTP/1.0 200 OK\r\nConnection: close\r\n";
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
    				sendMsg(msgType.status,"Listening on IP: " + prefs.getString("SERVER_IP","127.0.0.1") + " port: "+prefs.getInt("SERVER_PORT",8080));
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
	
	private void sendBuffer(OutputStream out,byte[] b,int size)
	{
		try {
			out.write(b, 0,size);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void sendBuffer(OutputStream out,String str)
	{
		try {
			byte[] b = str.getBytes();
			
			sendBuffer(out,b,b.length);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private class ServerThread implements Runnable {
        public void run() {
			running=true;
           	while (running) {
           		Socket client;
           		
        		try {
        			client = serverSocket.accept();
        		} catch(Exception e) {
        			e.printStackTrace();
        			continue;
        		}
        		try {
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
    							/* get contacts html file */
        						sendBuffer(out,r200 + endHeaders);
        						InputStream f = getAssets().open("home.html");
    							if(f != null) {
    								byte[]	b = new byte[1024];
    								int		sz=0;
    								
    								do {
    									sz=f.read(b);
    									if(sz > 0) sendBuffer(out,b,sz);
    								} while(sz >= 0);
    								f.close();
    							}
        					} else if(uri[0].equals("/version")) {
        						sendBuffer(out,r200xml + endHeaders);
        						sendBuffer(out,"<version>" + getString(R.string.app_name) + Httpd.this.getPackageManager().getPackageInfo(getPackageName(), 0).versionName + "</version>");
        					} else if(uri[0].startsWith("/img/")) {	/* image from contact */
        							int			sz;
    								byte[]		b = new byte[512];
    								String		name = uri[0].split("/")[2];
									InputStream	icon=null;
	        					    String		mime="";
        							
    								try {
    									if(name.startsWith("contact")) {
    										icon = getResources().openRawResource(R.drawable.contact);
    		        					    mime = "Content-Type: image/jpeg";
    									}
    									if(mime.length() != 0) {
    										sendBuffer(out,r200raw);
    										sendBuffer(out,mime + "\r\n" + endHeaders);
    										do {
    											sz=icon.read(b);
    											if(sz > 0) sendBuffer(out,b,sz);
    										} while(sz > 0);
    										icon.close();
    									}
    								} catch(Exception e) {
    									e.printStackTrace();
    								}
        					} else if(uri[0].startsWith("/cimg/")) {	/* image from contact */
        						byte[] 		img=null;
        						int 		photoId = Integer.parseInt(uri[0].split("/")[2].split("[.]")[0]);
        					    String		mime = "Content-Type: image/jpeg";
        						
        					    Uri 		imguri = ContentUris.withAppendedId(Data.CONTENT_URI, photoId);
        					    Cursor		c = getContentResolver().query(imguri, new String[] {Photo.PHOTO,Photo.MIMETYPE}, null, null, null);
        					    
        					    try {
        				            if (c.moveToFirst()) {
        				            	img = c.getBlob(c.getColumnIndex(CommonDataKinds.Photo.PHOTO));
        				            	//mime = c.getString(c.getColumnIndex(CommonDataKinds.Photo.MIMETYPE));
        				            }
        					    } catch(Exception e) {
        					    	img = null;
        					    }

        					    if(img != null) {
        					    	sendBuffer(out,r200raw);
        					    	sendBuffer(out,mime + "\r\n");
        					    	sendBuffer(out,"Content-Length: " + img.length + "\r\n" + endHeaders);
        					    	sendBuffer(out,img,img.length);
        					    } else {
            						sendBuffer(out,r404 + endHeaders + getString(R.string.httpNotFound));
               						out.flush();
        					    }
        					} else if(uri[0].startsWith("/contact/")) {
        						int photoId,id = Integer.parseInt(uri[0].split("/")[2]);
        						try {
        							photoId = Integer.parseInt(uri[0].split("/")[3]);
        						} catch(Exception e) {
        							photoId = 0;
        						}
    							sendBuffer(out,getContactInfo(id,photoId,true));
        						
        					} else if(uri[0].equals("/contacts/xml")) {
        						LinkedList<Integer> ll = new LinkedList<Integer>(); 
        						
        						sendBuffer(out,r200xml + endHeaders);
        						Cursor cursor = getContentResolver().query(Phone.CONTENT_URI, new String[] {Phone.RAW_CONTACT_ID,Phone.PHOTO_ID}, null, null, Phone.DISPLAY_NAME + " COLLATE NOCASE ASC;");
								sendBuffer(out,"<contacts version=\"1.0\">");
        						while(cursor.moveToNext()) {
        							int 	id = cursor.getInt(cursor.getColumnIndex(Phone.RAW_CONTACT_ID));

        							if(ll.contains(id)) {
        								continue;
        							}
        							ll.add(id);
        							sendBuffer(out,getContactInfo(id,cursor.getInt(cursor.getColumnIndex(Phone.PHOTO_ID)),true));
        						}
								sendBuffer(out,"</contacts>");
        						cursor.close();
        					} else if(uri[0].equals("/contacts")) {		/* contacts page */
    							/* get contacts html file */
        						sendBuffer(out,r200 + endHeaders);
        						InputStream f = getAssets().open("contacts.html");
    							if(f != null) {
    								byte[]	b = new byte[1024];
    								int		sz=0;
    								
    								do {
    									sz=f.read(b);
    									if(sz > 0) sendBuffer(out,b,sz);
    								} while(sz >= 0);
    								f.close();
    							}
        					} else {
        						sendBuffer(out,r404 + endHeaders + getString(R.string.httpNotFound));
        					}
        					break;
        				}
        			}
        			sendMsg(msgType.status,"closed connection to ip: " + client.getInetAddress().getHostAddress());
        		} catch (Exception e) {
        			sendMsg(msgType.status,"Connection interrupted");
        			e.printStackTrace();
		        }
        		try {
        			client.shutdownOutput();
        			client.close();
        		} catch(Exception e) {
        			e.printStackTrace();
        		}
        	}
        }
    }

	private String getContactInfo(int id,int photoid,boolean full)
	{
		StringBuilder cinfo = new StringBuilder();
		
		Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,id);
		Uri entityUri = Uri.withAppendedPath(rawContactUri,Entity.CONTENT_DIRECTORY);
		Cursor c = getContentResolver().query(entityUri,new String[]{RawContacts.SOURCE_ID,Entity.DATA_ID,Entity.MIMETYPE,Entity.DATA1,Entity.DATA2},null,null,null);
		if(c.isAfterLast()) {
			return("");
		}
		cinfo.append("<c id=\"" + id + "\">");
		while (c.moveToNext()) {
			if (c.isNull(c.getColumnIndex(Entity.DATA_ID))) {
				continue;
			}
			String entityMime = c.getString(c.getColumnIndex(Entity.MIMETYPE));
			String value = c.getString(c.getColumnIndex(Entity.DATA1));
			if(StructuredName.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"structuredname\"");
			} else if(Phone.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"phone\"");
				switch(Integer.parseInt(c.getString(c.getColumnIndex(Entity.DATA2)))) {
				case BaseTypes.TYPE_CUSTOM: cinfo.append(" t=\"custom\""); break;
				case Phone.TYPE_HOME: cinfo.append(" t=\"home\""); break;
				case Phone.TYPE_MOBILE: cinfo.append(" t=\"mobile\""); break;
				case Phone.TYPE_WORK: cinfo.append(" t=\"work\""); break;
				case Phone.TYPE_FAX_WORK: cinfo.append(" t=\"fax_work\""); break;
				case Phone.TYPE_FAX_HOME: cinfo.append(" t=\"fax_home\""); break;
				case Phone.TYPE_PAGER: cinfo.append(" t=\"pager\""); break;
				case Phone.TYPE_OTHER: cinfo.append(" t=\"other\""); break;
				case Phone.TYPE_CALLBACK: cinfo.append(" t=\"callback\""); break;
				case Phone.TYPE_CAR: cinfo.append(" t=\"car\""); break;
				case Phone.TYPE_COMPANY_MAIN: cinfo.append(" t=\"company_main\""); break;
				case Phone.TYPE_ISDN: cinfo.append(" t=\"isdn\""); break;
				case Phone.TYPE_MAIN: cinfo.append(" t=\"main\""); break;
				case Phone.TYPE_OTHER_FAX: cinfo.append(" t=\"other_fax\""); break;
				case Phone.TYPE_RADIO: cinfo.append(" t=\"radio\""); break;
				case Phone.TYPE_TELEX: cinfo.append(" t=\"telex\""); break;
				case Phone.TYPE_TTY_TDD: cinfo.append(" t=\"tty_tdd\""); break;
				case Phone.TYPE_WORK_MOBILE: cinfo.append(" t=\"work_mobile\""); break;
				case Phone.TYPE_WORK_PAGER: cinfo.append(" t=\"work_pager\""); break;
				case Phone.TYPE_ASSISTANT: cinfo.append(" t=\"assitant\""); break;
				case Phone.TYPE_MMS: cinfo.append(" t=\"mms\""); break;
				default:
					cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\"");
					break;
				}
			} else if(Email.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"email\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && Event.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"event\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && Nickname.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"nickname\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(Photo.CONTENT_ITEM_TYPE.equals(entityMime)) {
				if(photoid == 0) continue;
				cinfo.append("<i ct=\"photo\"");
				value = String.valueOf(photoid);
			} else if(full && GroupMembership.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"groupmembership\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && StructuredPostal.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"structuredpostal\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && Im.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"im\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && Note.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"note\"");
			} else if(Organization.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"organization\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && Relation.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"relation\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else if(full && Website.CONTENT_ITEM_TYPE.equals(entityMime)) {
				cinfo.append("<i ct=\"website\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			} else {
				cinfo.append("<i ct=\"" + entityMime + "\"");
				cinfo.append(" t=\"" + c.getString(c.getColumnIndex(Entity.DATA2)) + "\""); // type
			}
			cinfo.append(" v=\"" + value + "\"/>");
		}
		cinfo.append("</c>");
		c.close();
		return(cinfo.toString());
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
