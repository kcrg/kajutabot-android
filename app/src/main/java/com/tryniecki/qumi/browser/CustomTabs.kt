package com.tryniecki.qumi.browser

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
        intent.launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        uriHandler.openUri(url)
    }
}

@Composable
fun rememberCustomTabColors(): CustomTabColorSchemeParams {
    val scheme = MaterialTheme.colorScheme
    val toolbarColor = scheme.surfaceContainer.toArgb()
    val navigationBarColor = scheme.surface.toArgb()
    val dividerColor = scheme.outlineVariant.toArgb()
    return remember(toolbarColor, navigationBarColor, dividerColor) {
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .setNavigationBarColor(navigationBarColor)
            .setNavigationBarDividerColor(dividerColor)
            .build()
    }
}

@Composable
fun rememberOpenCustomTab(): (String) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = rememberCustomTabColors()
    return remember(context, uriHandler, colors) {
        { url -> openCustomTab(context, uriHandler, url, colors) }
    }
}
