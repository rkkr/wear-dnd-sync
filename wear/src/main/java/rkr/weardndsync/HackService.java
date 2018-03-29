package rkr.weardndsync;

import android.app.Service;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class HackService extends NotificationListenerService {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf();
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
    }
}
