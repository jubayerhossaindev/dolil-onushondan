package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.security.configureAppCheck
import com.example.data.local.BangladeshLocationsCatalog
import com.example.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must execute BEFORE setContent, ViewModels, or any Firebase Auth call
        configureAppCheck(this, intent)
        BangladeshLocationsCatalog.ensureLoaded(this)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}
