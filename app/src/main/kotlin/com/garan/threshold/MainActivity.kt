package com.garan.threshold

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.data.packageindex.AndroidPackageIndexSource
import com.garan.threshold.ui.home.ThresholdHomeCanvas
import com.garan.threshold.ui.launcher.AndroidAppLauncher
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.search.ThresholdSearchSurface
import com.garan.threshold.ui.theme.ThresholdTheme

class MainActivity : ComponentActivity() {

    private val packageIndexSource by lazy { AndroidPackageIndexSource(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(packageIndexSource = packageIndexSource) as T
            },
        )[SearchViewModel::class.java]

        setContent {
            ThresholdTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var isSearchActive by remember { mutableStateOf(false) }

                ThresholdHomeCanvas {
                    Spacer(modifier = Modifier.height(24.dp))

                    ThresholdSearchSurface(
                        query = uiState.query,
                        onQueryChange = viewModel::onQueryChanged,
                        results = uiState.results,
                        onItemClick = { item ->
                            if (item is SearchItem.App) {
                                appLauncher.launchApp(item.component)
                            }
                        },
                        isSearchActive = isSearchActive || uiState.query.isNotEmpty(),
                        onActivateSearch = {
                            isSearchActive = true
                            viewModel.ensureIndexLoaded()
                        },
                    )
                }
            }
        }
    }
}
