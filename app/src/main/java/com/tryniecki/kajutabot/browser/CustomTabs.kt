package com.tryniecki.kajutabot.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler

fun openCustomTab(
    context: Context,
    uriHandler: UriHandler,
    url: String,
    colors: CustomTabColorSchemeParams? = null,
) {
    val builder = CustomTabsIntent.Builder()
    if (colors != null) {
        builder.setDefaultColorSchemeParams(colors)
    }
    val intent = builder
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .build()
    try {
        intent.launchUrl(context, Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        uriHandler.openUri(url)
    }
}

@Composable
fun rememberCustomTabColors(): CustomTabColorSchemeParams {
    val scheme = MaterialTheme.colorScheme
    return CustomTabColorSchemeParams.Builder()
        .setToolbarColor(scheme.surfaceContainer.toArgb())
        .setNavigationBarColor(scheme.surface.toArgb())
        .setNavigationBarDividerColor(scheme.outlineVariant.toArgb())
        .build()
}

@Composable
fun rememberOpenCustomTab(): (String) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = rememberCustomTabColors()
    return { url ->
        openCustomTab(context, uriHandler, url, colors)
    }
}
