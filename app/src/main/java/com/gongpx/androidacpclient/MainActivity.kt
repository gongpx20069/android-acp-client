package com.gongpx.androidacpclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.gongpx.androidacpclient.ui.AndroidAcpClientApp

class MainActivity : ComponentActivity() {
    private val incomingPairingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingPairingLink.value = intent?.dataString
        setContent {
            AndroidAcpClientApp(incomingPairingLink = incomingPairingLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPairingLink.value = intent.dataString
    }
}

