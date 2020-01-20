package tk.burdukowsky.caller;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

/**
 * Created by IDEA
 * User: Stanislav
 * Date: 007 07.03.17
 */

public class SimpleService extends Service {

    private SimpleWebServer mWebServer;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        startServer();
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        mWebServer.stop();
        stopForeground(true);
        super.onDestroy();
    }

    void startServer() {
        final int port = 8080;
        mWebServer = new SimpleWebServer(port, this);
        mWebServer.start();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_working))
                .setContentText(getString(R.string.app_working_on_ip, mWebServer.getIp(), port))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true).build();
        startForeground(42, notification);
    }
}
