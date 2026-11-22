package tw.knox.dailytasks

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import tw.knox.dailytasks.data.TaskRules
import tw.knox.dailytasks.reminder.NotificationHelper
import tw.knox.dailytasks.widget.TaskWidgetUpdater
import java.util.Locale

class DailyTasksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.TAIWAN)
        TaskRules.allowEarlyComplete = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean(MainViewModel.PREF_ALLOW_EARLY_COMPLETE, false)
        NotificationHelper.createChannels(this)
        // 關螢幕時把小工具清單捲回第一筆，下次亮螢幕看到的是最前面的項目
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = TaskWidgetUpdater.scrollToTop(context)
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
