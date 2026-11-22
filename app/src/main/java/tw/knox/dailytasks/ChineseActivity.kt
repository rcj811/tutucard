package tw.knox.dailytasks

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import java.util.Locale

open class ChineseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(Locale.TAIWAN) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
}

internal fun Throwable.chineseMessage(fallback: String): String = message?.takeIf { text ->
    text.any { it in '\u3400'..'\u9fff' } && text.none { it in 'A'..'Z' || it in 'a'..'z' }
} ?: fallback
