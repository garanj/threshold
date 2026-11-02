package com.garan.threshold.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.core.search.SearchResult
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.collections.immutable.ImmutableList

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
) {
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = SubtleGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onActivateSearch),
                )
            } else {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyLarge,
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = PureWhite),
                    cursorBrush = SolidColor(PureWhite),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val first = results.firstOrNull()?.item
                            if (first != null) {
                                onItemClick(first)
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = SubtleGray,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = results,
                        key = { result ->
                            when (val item = result.item) {
                                is SearchItem.App -> item.component.toString()
                                is SearchItem.Action -> "action:${item.id}"
                            }
                        },
                    ) { result ->
                        SearchResultRow(
                            result = result,
                            onClick = { onItemClick(result.item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(result: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = when (val item = result.item) {
            is SearchItem.App -> item.label
            is SearchItem.Action -> item.label
        }

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = PureWhite,
        )

        if (result.item is SearchItem.App) {
            val app = result.item as SearchItem.App
            if (app.accessState is AppAccessState.Guarded) {
                Text(
                    text = "GUARDED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = SubtleGray,
                )
            }
        } else if (result.item is SearchItem.Action) {
            Text(
                text = "ACTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = SubtleGray,
            )
        }
    }
}
