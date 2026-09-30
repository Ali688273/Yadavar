package com.yadavar.app.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YadavarBannerAd(modifier: Modifier = Modifier) {
    var useTapsell by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            FrameLayout(context).also { container ->
                if (useTapsell) {
                    YadavarAds.loadTapsellBanner(context, container) {}
                } else {
                    val banner = YadavarAds.createAdiveryBanner(context) {
                        useTapsell = true
                    }
                    container.addView(banner)
                    banner.loadAd()
                }
            }
        },
        update = { container ->
            if (useTapsell && container.childCount == 0) {
                YadavarAds.loadTapsellBanner(container.context, container) {}
            }
        }
    )
}
