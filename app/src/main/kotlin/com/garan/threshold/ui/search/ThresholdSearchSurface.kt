package com.garan.threshold.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.R
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.search.MatchType
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.core.search.SearchResult
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

private val CursorSolidBrush = SolidColor(PureWhite)
internal val SearchKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Go,
)

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
    placeholderText: String = stringResource(R.string.search_placeholder),
    isSearchActive: Boolean = query.isNotEmpty(),
    onActivateSearch: () -> Unit = {},
    onItemLongClick: ((SearchItem) -> Unit)? = null,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    listState: LazyListState = rememberLazyListState(),
    quickActions: ImmutableList<QuickActionUiItem> = kotlinx.collections.immutable.persistentListOf(),
    onQuickActionClick: (QuickActionUiItem) -> Unit = {},
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
        // Search Input Box with Optical Transition
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val active = isSearchActive || query.isNotEmpty()
            AnimatedContent(
                targetState = active,
                transitionSpec = {
                    fadeIn(
                        animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
                    ) togetherWith
                        fadeOut(animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS))
                },
                label = "SearchInputShutterWipe",
            ) { isInputActive ->
                if (!isInputActive) {
                    Text(
                        text = placeholderText,
                        style = baseBodyLarge,
                        color = SubtleGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onActivateSearch),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
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

                        var textFieldValueState by remember {
                            mutableStateOf(
                                TextFieldValue(
                                    text = query,
                                    selection = TextRange(query.length),
                                ),
                            )
                        }
                        var lastTextValue by remember { mutableStateOf(query) }
                        if (query != lastTextValue) {
                            lastTextValue = query
                            textFieldValueState = TextFieldValue(
                                text = query,
                                selection = TextRange(query.length),
                            )
                        }
                        val textFieldValue = textFieldValueState.copy(text = query)

                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                textFieldValueState = newValue
                                if (newValue.text != lastTextValue) {
                                    lastTextValue = newValue.text
                                    onQueryChange(newValue.text)
                                }
                            },
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
                                        } else {
                                            hapticEngine.resistanceDetent()
                                        }
                                    }
                                },
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Hairline Shutter Wipe (1dp rule anchors transitions)
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = HairlineBorder,
        )

        // Quick Actions (visible strictly when search query is blank)
        AnimatedVisibility(
            visible = query.isBlank() && quickActions.isNotEmpty(),
            enter = ThresholdMotion.ResultRevealEnter,
            exit = ThresholdMotion.ResultRevealExit,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                quickActions.forEach { item ->
                    QuickActionRow(
                        item = item,
                        onClick = { onQuickActionClick(item) },
                        onLongClick = if (onItemLongClick != null) {
                            { onItemLongClick(SearchItem.App(item.component, item.label, item.accessState)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // Results or Empty Feedback with Decelerating Optical Revealing
        AnimatedVisibility(
            visible = query.isNotBlank(),
            enter = ThresholdMotion.ResultRevealEnter,
            exit = ThresholdMotion.ResultRevealExit,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))

                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_no_matching_apps),
                        style = emptyStateTextStyle,
                        color = SubtleGray,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LaunchedEffect(query) {
                        listState.scrollToItem(0)
                    }

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .drop(1)
                            .distinctUntilChanged()
                            .collect {
                                if (listState.isScrollInProgress) {
                                    hapticEngine.rotaryDetent()
                                }
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
                                modifier = Modifier.animateItem(
                                    fadeInSpec = ThresholdMotion.opticalTween(
                                        ThresholdMotion.RESULT_REVEAL_DURATION_MS,
                                    ),
                                    fadeOutSpec = ThresholdMotion.opticalTween(
                                        ThresholdMotion.SHUTTER_CLOSE_DURATION_MS,
                                    ),
                                    placementSpec = ThresholdMotion.opticalTween(
                                        ThresholdMotion.RESULT_REVEAL_DURATION_MS,
                                    ),
                                ),
                            )
                        }
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
                        text = stringResource(R.string.badge_guarded),
                        style = badgeTextStyle,
                        color = SubtleGray,
                    )
                }
            }
            is SearchItem.Action -> {
                Text(
                    text = stringResource(R.string.badge_action),
                    style = badgeTextStyle,
                    color = SubtleGray,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickActionRow(
    item: QuickActionUiItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val baseLabelMedium = MaterialTheme.typography.labelMedium
    val badgeTextStyle = remember(baseLabelMedium) {
        baseLabelMedium.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    val isBlocked = item.accessState is AppAccessState.Blocked
    val isGuarded = item.accessState is AppAccessState.Guarded
    val textColor = if (isBlocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    val slotFormatted = String.format("%02d", item.slotNumber)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$slotFormatted   ${item.label}",
            style = MaterialTheme.typography.titleLarge,
            color = textColor,
        )

        if (isGuarded) {
            Text(
                text = stringResource(R.string.badge_guarded),
                style = badgeTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
