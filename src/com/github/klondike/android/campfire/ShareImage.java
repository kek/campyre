package com.github.klondike.android.campfire;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.view.Window;
import android.widget.Toast;

import com.github.klondike.java.campfire.Campfire;
import com.github.klondike.java.campfire.CampfireException;
import com.github.klondike.java.campfire.Room;

public class ShareImage extends Activity {
	private static final int UPLOADING = 0;
	
	private Campfire campfire;
	private String roomId;
	private Room room;
	
	private boolean uploaded;
	private String uploadError;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		loadCampfire();
		uploadImage();
	}
	
	final Handler handler = new Handler();
	final Runnable afterUpload = new Runnable() {
		public void run() {
			if (uploaded)
				alert("Uploaded image to Campfire.");
			else
				alert(uploadError);
			
			dismissDialog(UPLOADING);
			finish();
		}
	};
	
	public void uploadImage() {
		Thread uploadThread = new Thread() {
			public void run() {
				try {
					if (campfire.session == null)
						campfire.login();
					
					if (campfire.session == null) {
						uploaded = false;
						uploadError = "Couldn't log in to Campfire, image was not uploaded. Check your Campfire credentials.";
					} else {
						storeSession(campfire.session);
						room = new Room(campfire, roomId);
						
						
						// Don't move this code into another method, or split it up - somehow
						// stuff gets out of scope or garbage collected and file transfers start dying
						Bundle extras = ShareImage.this.getIntent().getExtras();
						Uri uri = (Uri) extras.get("android.intent.extra.STREAM");
						
						ContentResolver cr = ShareImage.this.getContentResolver();
					
						ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
						FileDescriptor fd = pfd.getFileDescriptor();
						FileInputStream image = new FileInputStream(fd);
						
						if (image == null) {
							uploaded = false;
							uploadError = "Error processing photo, image was not uploaded.";
						} else {
							if (room.uploadFile(image))
								uploaded = true;
							else {
								uploaded = false;
								uploadError = "Couldn't upload file to Campfire.";
							}
						}
					}
				} catch (FileNotFoundException e) {
					uploaded = false;
					uploadError = "Error processing photo, image was not uploaded.";
				} catch (CampfireException e) {
					uploaded = false;
					uploadError = "Error connecting to Campfire, image was not uploaded.";
				}
				handler.post(afterUpload);
			}
		};
		uploadThread.start();
		
		showDialog(UPLOADING);
	}
	
	public void loadCampfire() {
    	String subdomain = Preferences.getSubdomain(this);
        String email = Preferences.getEmail(this);
        String password = Preferences.getPassword(this);
        boolean ssl = Preferences.getSsl(this);
        roomId = Preferences.getRoomId(this);
        
        campfire = new Campfire(subdomain, email, password, ssl);
        campfire.session = getSharedPreferences("campfire", 0).getString("session", null);
    }
	
	protected Dialog onCreateDialog(int id) {
        switch(id) {
        case UPLOADING:
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setMessage("Uploading image...");
            return dialog;
        default:
            return null;
        }
    }
	
	public void alert(String msg) {
		Toast.makeText(ShareImage.this, msg, Toast.LENGTH_SHORT).show();
	}
	
	public void storeSession(String session) {
    	SharedPreferences prefs = getSharedPreferences("campfire", 0);
    	prefs.edit().putString("session", session).commit();
    }
    
    public String loadSession() {
    	SharedPreferences prefs = getSharedPreferences("campfire", 0);
    	return prefs.getString("session", null);
    }
	
}
