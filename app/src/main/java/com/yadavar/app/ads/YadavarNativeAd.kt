package com.yadavar.app.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YadavarNativeAd(modifier: Modifier = Modifier) {
    var useTapsell by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            FrameLayout(context).also { container ->
                if (useTapsell) {
                    YadavarAds.loadTapsellNativeBanner(context, container) {}
                } else {
                    val native = YadavarAds.createAdiveryNative(context) {
                        container.removeAllViews()
                        useTapsell = true
                    }
                    container.addView(native)
                    native.loadAd()
                }
            }
        },
        update = { container ->
            if (useTapsell && container.childCount == 0) {
                YadavarAds.loadTapsellNativeBanner(container.context, container) {}
            }
        }
    )
}
