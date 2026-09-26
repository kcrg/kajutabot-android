package com.tryniecki.kajutabot.ui.more

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R

@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.more_libraries_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(LIBRARIES, key = { it.name }) { library ->
                ListItem(
                    verticalAlignment = Alignment.CenterVertically,
                    trailingContent = { Text(library.license) },
                    supportingContent = { Text(stringResource(library.descriptionResId)) },
                    colors = ListItemDefaults.colors(),
                    content = { Text(library.name) },
                )
                if (library != LIBRARIES.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

private data class LibraryInfo(
    val name: String,
    @StringRes val descriptionResId: Int,
    val license: String,
)

private val LIBRARIES = listOf(
    LibraryInfo("Jetpack Compose", R.string.library_compose_description, "Apache 2.0"),
    LibraryInfo("Material 3", R.string.library_material3_description, "Apache 2.0"),
    LibraryInfo("AndroidX Activity", R.string.library_activity_description, "Apache 2.0"),
    LibraryInfo("Navigation 3", R.string.library_navigation_description, "Apache 2.0"),
    LibraryInfo("AndroidX Lifecycle", R.string.library_lifecycle_description, "Apache 2.0"),
    LibraryInfo("AndroidX Core KTX", R.string.library_core_ktx_description, "Apache 2.0"),
    LibraryInfo("AndroidX Media3", R.string.library_media3_description, "Apache 2.0"),
    LibraryInfo("AndroidX Browser", R.string.library_browser_description, "Apache 2.0"),
    LibraryInfo("Coil 3", R.string.library_coil_description, "Apache 2.0"),
    LibraryInfo("Tabler Icons", R.string.library_tabler_description, "MIT"),
    LibraryInfo("Kotlin Coroutines", R.string.library_coroutines_description, "Apache 2.0"),
    LibraryInfo("Retrofit", R.string.library_retrofit_description, "Apache 2.0"),
    LibraryInfo("SignalR Java Client", R.string.library_signalr_description, "MIT"),
    LibraryInfo("OkHttp", R.string.library_okhttp_description, "Apache 2.0"),
    LibraryInfo("kotlinx.serialization", R.string.library_serialization_description, "Apache 2.0"),
)
