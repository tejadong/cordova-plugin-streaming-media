package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

public class StreamingMedia extends CordovaPlugin {
	public static final String ACTION_PLAY_AUDIO = "playAudio";
	public static final String ACTION_PLAY_AUDIO_FOREGROUND = "playAudioForeground";
	public static final String ACTION_PLAY_VIDEO = "playVideo";

	private static final int ACTIVITY_CODE_PLAY_MEDIA = 7;

	private CallbackContext callbackContext;

	private static final String TAG = "StreamingMediaPlugin";

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
		JSONObject options = null;

		try {
			options = args.getJSONObject(1);
		} catch (JSONException e) {
			// Developer provided no options. Leave options null.
		}

		if (ACTION_PLAY_AUDIO.equals(action)) {
			return playAudio(args.getString(0), options);
		} else if (ACTION_PLAY_VIDEO.equals(action)) {
			return playVideo(args.getString(0), options);
		} else if (ACTION_PLAY_AUDIO_FOREGROUND.equals(action)) {
			return playAudioForeground(args.getString(0), options);
		} else {
			callbackContext.error("streamingMedia." + action + " is not a supported method.");
			return false;
		}
	}

	private boolean playAudioForeground(String url, JSONObject options) {
		return play(ForegroundAudioService.class, url, options);
	}

	private boolean playAudio(String url, JSONObject options) {
		return play(SimpleAudioStream.class, url, options);
	}

	private boolean playVideo(String url, JSONObject options) {
		return play(SimpleVideoStream.class, url, options);
	}

	private boolean play(final Class activityClass, final String url, final JSONObject options) {
		final CordovaInterface cordovaObj = cordova;
		final CordovaPlugin plugin = this;

		cordova.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				final Intent streamIntent = new Intent(cordovaObj.getActivity().getApplicationContext(), activityClass);
				Bundle extras = new Bundle();
				extras.putString("mediaUrl", url);

				if (options != null) {
					Iterator<String> optKeys = options.keys();
					while (optKeys.hasNext()) {
						try {
							final String optKey = (String)optKeys.next();
							if (options.get(optKey).getClass().equals(String.class)) {
								extras.putString(optKey, (String)options.get(optKey));
								Log.v(TAG, "Added option: " + optKey + " -> " + String.valueOf(options.get(optKey)));
							} else if (options.get(optKey).getClass().equals(Boolean.class)) {
								extras.putBoolean(optKey, (Boolean)options.get(optKey));
								Log.v(TAG, "Added option: " + optKey + " -> " + String.valueOf(options.get(optKey)));
							}

						} catch (JSONException e) {
							Log.e(TAG, "JSONException while trying to read options. Skipping option.");
						}
					}
					streamIntent.putExtras(extras);
				}

				if (activityClass == ForegroundAudioService.class) {

				  final int lState = ForegroundAudioService.getState();
				  if (lState == MusicConstants.STATE_SERVICE.NOT_INIT) {
					if (!NetworkHelper.isInternetAvailable(cordovaObj.getActivity().getApplicationContext())) {
		//              showError(v);
					  return;
					}
					streamIntent.putExtras(extras);
					streamIntent.setAction(MusicConstants.ACTION.START_ACTION);
					cordovaObj.getActivity().startService(streamIntent);

				  } else if (lState == MusicConstants.STATE_SERVICE.PREPARE ||
					lState == MusicConstants.STATE_SERVICE.PLAY) {
					Intent lPauseIntent = new Intent(cordovaObj.getActivity().getApplicationContext(), ForegroundAudioService.class);
					lPauseIntent.setAction(MusicConstants.ACTION.PAUSE_ACTION);
					PendingIntent lPendingPauseIntent = PendingIntent.getService(cordovaObj.getActivity().getApplicationContext(), 0, lPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
					try {
					  lPendingPauseIntent.send();
					} catch (PendingIntent.CanceledException e) {
					  e.printStackTrace();
					}
				  } else if (lState == MusicConstants.STATE_SERVICE.PAUSE) {
					if (!NetworkHelper.isInternetAvailable(cordovaObj.getActivity().getApplicationContext())) {
		//              showError(v);
					  return;
					}
					Intent lPauseIntent = new Intent(cordovaObj.getActivity().getApplicationContext(), ForegroundAudioService.class);
					lPauseIntent.setAction(MusicConstants.ACTION.PLAY_ACTION);
					PendingIntent lPendingPauseIntent = PendingIntent.getService(cordovaObj.getActivity().getApplicationContext(), 0, lPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
					try {
					  lPendingPauseIntent.send();
					} catch (PendingIntent.CanceledException e) {
					  e.printStackTrace();
					}

				  }
				} else {
				  cordovaObj.startActivityForResult(plugin, streamIntent, ACTIVITY_CODE_PLAY_MEDIA);
				}

			}
		});
		return true;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Log.v(TAG, "onActivityResult: " + requestCode + " " + resultCode);
		super.onActivityResult(requestCode, resultCode, intent);
		if (ACTIVITY_CODE_PLAY_MEDIA == requestCode) {
			if (Activity.RESULT_OK == resultCode) {
				this.callbackContext.success();
			} else if (Activity.RESULT_CANCELED == resultCode) {
				String errMsg = "Error";
				if (intent != null && intent.hasExtra("message")) {
					errMsg = intent.getStringExtra("message");
				}
				this.callbackContext.error(errMsg);
			}
		}
	}
}
