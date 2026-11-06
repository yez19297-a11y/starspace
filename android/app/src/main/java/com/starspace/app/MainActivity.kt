package com.starspace.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starspace.app.data.ApiClient
import com.starspace.app.data.AppLifecycle
import com.starspace.app.data.AppStore
import com.starspace.app.notify.Notify
import com.starspace.app.notify.SpaceCheckWorker
import com.starspace.app.ui.StarSpaceApp
import com.starspace.app.ui.StarSpaceTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private var pendingConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.ensureChannels(applicationContext)
        runCatching { SpaceCheckWorker.schedule(applicationContext) }
        pendingConversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)

        val store = AppStore(applicationContext)
        val api = ApiClient()
        setContent {
            StarSpaceTheme(darkMode = store.data.settings.darkMode) {
                StarSpaceApp(
                    store = store,
                    api = api,
                    initialConversationId = pendingConversationId,
                    onInitialConversationConsumed = { pendingConversationId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
    }

    override fun onResume() {
        super.onResume()
        AppLifecycle.foreground = true
    }

    override fun onPause() {
        super.onPause()
        AppLifecycle.foreground = false
    }
}
