package com.chrisirlam.snorenudge.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import com.chrisirlam.snorenudge.wear.ui.WatchMainScreen
import com.chrisirlam.snorenudge.wear.viewmodel.WatchMainViewModel

class WatchMainActivity : ComponentActivity() {

    private val viewModel: WatchMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WatchMainScreen(viewModel = viewModel)
            }
        }
    }
}
