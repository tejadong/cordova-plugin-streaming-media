package com.hutchind.cordova.plugins.streamingmedia;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import capacitor.android.plugins.R;

public class ForegroundAudioService extends Service implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener {

    private final static String FOREGROUND_CHANNEL_ID = "foreground_channel_id";
    private final static String TAG = ForegroundAudioService.class.getSimpleName();
    static private int sStateService = MusicConstants.STATE_SERVICE.NOT_INIT;
    private final Uri mUriRadioDefault = Uri.parse("https://nfw.ria.ru/flv/audio.aspx?ID=75651129&type=mp3");
    private final Object mLock = new Object();
    private final Handler mHandler = new Handler();
    private MediaPlayer mPlayer;
    private Uri mUriRadio = null;
    private NotificationManager mNotificationManager;
    private WifiManager.WifiLock mWiFiLock;
    private PowerManager.WakeLock mWakeLock;
    private Handler mTimerUpdateHandler = new Handler();
    private Runnable mTimerUpdateRunnable = new Runnable() {

        @Override
        public void run() {
            mNotificationManager.notify(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
            mTimerUpdateHandler.postDelayed(this, MusicConstants.DELAY_UPDATE_NOTIFICATION_FOREGROUND_SERVICE);
        }
    };
    private Runnable mDelayedShutdown = new Runnable() {

        public void run() {
            unlockWiFi();
            unlockCPU();
            stopForeground(true);
            stopSelf();
        }

    };

    public ForegroundAudioService() {
    }

    public static int getState() {
        return sStateService;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(ForegroundAudioService.class.getSimpleName(), "onCreate()");
        sStateService = MusicConstants.STATE_SERVICE.NOT_INIT;
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Bundle b = intent.getExtras();
        if (mUriRadio == null) {
          mUriRadio = Uri.parse(b.getString("mediaUrl"));
        }

        if (intent == null || intent.getAction() == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case MusicConstants.ACTION.START_ACTION:
              Log.i(TAG, "Received start Intent ");
              sStateService = MusicConstants.STATE_SERVICE.PREPARE;

              startForeground(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
              destroyPlayer();
              initPlayer();
              play();
              break;

            case MusicConstants.ACTION.PAUSE_ACTION:
                sStateService = MusicConstants.STATE_SERVICE.PAUSE;
                mNotificationManager.notify(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
                Log.i(TAG, "Clicked Pause");
                destroyPlayer();
                mHandler.postDelayed(mDelayedShutdown, MusicConstants.DELAY_SHUTDOWN_FOREGROUND_SERVICE);
                break;

            case MusicConstants.ACTION.PLAY_ACTION:
                sStateService = MusicConstants.STATE_SERVICE.PLAY;
                mNotificationManager.notify(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
                Log.i(TAG, "Clicked Play");
                destroyPlayer();
                initPlayer();
                play();
                break;

            case MusicConstants.ACTION.STOP_ACTION:
                Log.i(TAG, "Received Stop Intent");
                destroyPlayer();
                stopForeground(true);
                stopSelf();
                break;

            default:
                stopForeground(true);
                stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        destroyPlayer();
        sStateService = MusicConstants.STATE_SERVICE.NOT_INIT;
        try {
            mTimerUpdateHandler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private void destroyPlayer() {
        if (mPlayer != null) {
            try {
                mPlayer.reset();
                mPlayer.release();
                Log.d(TAG, "Player destroyed");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mPlayer = null;
            }
        }
        unlockWiFi();
        unlockCPU();

    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.d(TAG, "Player onError() what:" + what);
//        destroyPlayer();
//        mHandler.postDelayed(mDelayedShutdown, MusicConstants.DELAY_SHUTDOWN_FOREGROUND_SERVICE);
//        mNotificationManager.notify(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
//        sStateService = MusicConstants.STATE_SERVICE.PAUSE;

        sStateService = MusicConstants.STATE_SERVICE.PREPARE;

        startForeground(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
        destroyPlayer();
        initPlayer();
        play();
        return false;
    }

    private void initPlayer() {
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnErrorListener(this);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnBufferingUpdateListener(this);
        mPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                Log.d(TAG, "Player onInfo(), what:" + what + ", extra:" + extra);
                return false;
            }
        });
        lockWiFi();
        lockCPU();
    }

    private void play() {
        try {
            mHandler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (mLock) {
            try {
                if (mPlayer == null) {
                    initPlayer();
                }
                mPlayer.reset();
                mPlayer.setVolume(1.0f, 1.0f);
                mPlayer.setDataSource(this, mUriRadio);
                mPlayer.prepareAsync();

            } catch (Exception e) {
                destroyPlayer();
                e.printStackTrace();
            }
        }
    }

    private Notification prepareNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && mNotificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
            // The user-visible name of the channel.
            CharSequence name = getString(R.string.text_value_radio_notification);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(FOREGROUND_CHANNEL_ID, name, importance);
            mChannel.setSound(null, null);
            mChannel.enableVibration(false);
            mNotificationManager.createNotificationChannel(mChannel);
        }
        Intent notificationIntent = new Intent(this, ForegroundAudioService.class);
        notificationIntent.setAction(MusicConstants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }  else {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Intent lPauseIntent = new Intent(this, ForegroundAudioService.class);
        lPauseIntent.setAction(MusicConstants.ACTION.PAUSE_ACTION);
        PendingIntent lPendingPauseIntent = PendingIntent.getService(this, 0, lPauseIntent, PendingIntent.FLAG_MUTABLE);

        Intent playIntent = new Intent(this, ForegroundAudioService.class);
        playIntent.setAction(MusicConstants.ACTION.PLAY_ACTION);
        PendingIntent lPendingPlayIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_MUTABLE);

        Intent lStopIntent = new Intent(this, ForegroundAudioService.class);
        lStopIntent.setAction(MusicConstants.ACTION.STOP_ACTION);
        PendingIntent lPendingStopIntent = PendingIntent.getService(this, 0, lStopIntent, PendingIntent.FLAG_MUTABLE);

        RemoteViews lRemoteViews = new RemoteViews(getPackageName(), R.layout.radio_notification);
        lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_close_button, lPendingStopIntent);

        Log.i("sStateService: ", sStateService+"");

        switch (sStateService) {

            case MusicConstants.STATE_SERVICE.PAUSE:
                lRemoteViews.setViewVisibility(R.id.ui_notification_progress_bar, View.INVISIBLE);
                lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_player_button, lPendingPlayIntent);
                lRemoteViews.setImageViewResource(R.id.ui_notification_player_button, R.drawable.ic_play_arrow_white);
                break;

            case MusicConstants.STATE_SERVICE.PLAY:
                lRemoteViews.setViewVisibility(R.id.ui_notification_progress_bar, View.INVISIBLE);
                lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_player_button, lPendingPauseIntent);
                lRemoteViews.setImageViewResource(R.id.ui_notification_player_button, R.drawable.ic_pause_white);
                break;

            case MusicConstants.STATE_SERVICE.PREPARE:
                lRemoteViews.setViewVisibility(R.id.ui_notification_progress_bar, View.VISIBLE);
                lRemoteViews.setOnClickPendingIntent(R.id.ui_notification_player_button, lPendingPauseIntent);
                lRemoteViews.setImageViewResource(R.id.ui_notification_player_button, R.drawable.ic_pause_white);
                break;
        }

        NotificationCompat.Builder lNotificationBuilder;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            lNotificationBuilder = new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID);
        } else {
            lNotificationBuilder = new NotificationCompat.Builder(this);
        }

        lNotificationBuilder
                .setContent(lRemoteViews)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            lNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }

        return lNotificationBuilder.build();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "Player onPrepared()");
        sStateService = MusicConstants.STATE_SERVICE.PLAY;
        mNotificationManager.notify(MusicConstants.NOTIFICATION_ID_FOREGROUND_SERVICE, prepareNotification());
        try {
            mPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mPlayer.start();
        mTimerUpdateHandler.postDelayed(mTimerUpdateRunnable, 0);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        Log.d(TAG, "Player onBufferingUpdate():" + percent);
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
      Log.d(TAG, "onCompletion");
    }

    private void lockCPU() {
        PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (mgr == null) {
            return;
        }
        mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getSimpleName());
        mWakeLock.acquire();
        Log.d(TAG, "Player lockCPU()");
    }

    private void unlockCPU() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
            Log.d(TAG, "Player unlockCPU()");
        }
    }

    private void lockWiFi() {
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        if (connManager == null) {
            return;
        }
        NetworkInfo lWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (lWifi != null && lWifi.isConnected()) {
            WifiManager manager = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE));
            if (manager != null) {
                mWiFiLock = manager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL, ForegroundAudioService.class.getSimpleName());
                mWiFiLock.acquire();
            }
            Log.d(TAG, "Player lockWiFi()");
        }
    }

    private void unlockWiFi() {
        if (mWiFiLock != null && mWiFiLock.isHeld()) {
            mWiFiLock.release();
            mWiFiLock = null;
            Log.d(TAG, "Player unlockWiFi()");
        }
    }


}
