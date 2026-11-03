package com.garan.threshold.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.search.MatchType
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.core.search.SearchResult
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

private val CursorSolidBrush = SolidColor(PureWhite)
private val SearchKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Go)

/**
 * Mechanical, typographic search surface for Threshold.
 * Features monospaced tabular numerals, real-time filtering, friction indicators,
 * and edge-to-edge safe drawing insets.
 */
@Composable
fun ThresholdSearchSurface(
    query: String,
    onQueryChange: (String) -> Unit,
    results: ImmutableList<SearchResult>,
    onItemClick: (SearchItem) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "Type to launch...",
    isSearchActive: Boolean = query.isNotEmpty(),
    onActivateSearch: () -> Unit = {},
    onItemLongClick: ((SearchItem) -> Unit)? = null,
    hapticEngine: HapticEngine = NoOpHapticEngine,
) {
    val baseBodyLarge = MaterialTheme.typography.bodyLarge
    val inputTextStyle = remember(baseBodyLarge) { baseBodyLarge.copy(color = PureWhite) }
    val baseLabelSmall = MaterialTheme.typography.labelSmall
    val emptyStateTextStyle = remember(baseLabelSmall) {
        baseLabelSmall.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AmoledBlack),
    ) {
        // Search Input Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!isSearchActive && query.isEmpty()) {
                Text(
                    text = placeholderText,
                    style = baseBodyLarge,
                    color = SubtleGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onActivateSearch),
                )
            } else {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = baseBodyLarge,
                        color = SubtleGray,
                    )
                }

                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = inputTextStyle,
                    cursorBrush = CursorSolidBrush,
                    singleLine = true,
                    keyboardOptions = SearchKeyboardOptions,
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val topResult = results.firstOrNull()
                            if (topResult != null) {
                                val item = topResult.item
                                // Reverse Search Drag: Guarded apps require full exact match on IME Go
                                val canQuickLaunch = item !is SearchItem.App ||
                                    item.accessState !is AppAccessState.Guarded ||
                                    topResult.matchType == MatchType.EXACT
                                if (canQuickLaunch) {
                                    onItemClick(item)
                                }
                            }
                        },
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = HairlineBorder,
        )

        // Results or Empty Feedback
        if (query.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            if (results.isEmpty()) {
                Text(
                    text = "NO MATCHING APPS",
                    style = emptyStateTextStyle,
                    color = SubtleGray,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .drop(1)
                        .distinctUntilChanged()
                        .collect {
                            hapticEngine.rotaryDetent()
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = results,
                        key = { result -> result.item.key },
                    ) { result ->
                        val item = result.item
                        SearchResultRow(
                            result = result,
                            onClick = { onItemClick(item) },
                            onLongClick = if (item is SearchItem.App && onItemLongClick != null) {
                                { onItemLongClick(item) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val baseLabelSmall = MaterialTheme.typography.labelSmall
    val badgeTextStyle = remember(baseLabelSmall) {
        baseLabelSmall.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = result.item.label,
            style = MaterialTheme.typography.titleMedium,
            color = PureWhite,
        )

        when (val item = result.item) {
            is SearchItem.App -> {
                if (item.accessState is AppAccessState.Guarded) {
                    Text(
                        text = "GUARDED",
                        style = badgeTextStyle,
                        color = SubtleGray,
                    )
                }
            }
            is SearchItem.Action -> {
                Text(
                    text = "ACTION",
                    style = badgeTextStyle,
                    color = SubtleGray,
                )
            }
        }
    }
}
