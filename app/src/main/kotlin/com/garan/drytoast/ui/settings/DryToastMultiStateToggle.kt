package com.garan.drytoast.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.drytoast.R
import com.garan.drytoast.core.port.HapticEngine
import com.garan.drytoast.core.port.NoOpHapticEngine
import com.garan.drytoast.ui.motion.DryToastMotion
import com.garan.drytoast.ui.theme.AmoledBlack
import com.garan.drytoast.ui.theme.CharcoalGray
import com.garan.drytoast.ui.theme.HairlineBorder
import com.garan.drytoast.ui.theme.PureWhite
import com.garan.drytoast.ui.theme.SubtleGray

private val MIN_TOUCH_TARGET_SIZE = 48.dp

/**
 * Standard 3-state toggle positions for the Base / Default Profile.
 * Balanced 5-letter verbs: ALLOW, GUARD, BLOCK.
 */
enum class BaseToggleState(val label: String, @StringRes val labelRes: Int) {
    ALLOW("ALLOW", R.string.toggle_allow),
    GUARD("GUARD", R.string.toggle_guard),
    BLOCK("BLOCK", R.string.toggle_block),
}

/**
 * Precision mechanical segmented slider toggle.
 *
 * Implements Dry Toast's tactile instrument aesthetic:
 * - Solid monochrome sliding indicator inside a crisp [height] (`32.dp` default) bordered housing.
 * - Full `>= 48.dp × 48.dp` accessible touch bounds on each interactive segment.
 * - Decelerating optical easing (`cubic-bezier(0.16, 1.0, 0.3, 1.0)`).
 * - Hairline framing (1dp rule) with crisp minimal corners.
 * - Distinct micro-haptic detents on transitions (`PRIMITIVE_CLICK` / resistance detent).
 * - When [selected] is null or [enabled] is false, the slider is visually dimmed to 40% with no indicator.
 */
@Composable
fun <T> DryToastMultiStateToggle(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    enabled: Boolean = true,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    isResistanceState: (T) -> Boolean = { false },
    maxLines: Int = 1,
    optionTestTag: ((T) -> String)? = null,
) {
    require(options.isNotEmpty()) { "Options list cannot be empty" }
    val selectedIndex = if (selected != null) options.indexOf(selected) else -1

    val innerPadding = 2.dp
    val containerShape = RoundedCornerShape(2.dp)
    val indicatorShape = RoundedCornerShape(1.dp)
    val touchHeight = maxOf(height, MIN_TOUCH_TARGET_SIZE)

    val density = LocalDensity.current
    val cappedFontScale = density.fontScale.coerceAtMost(1.25f)
    val labelFontSize = ((11f * cappedFontScale) / density.fontScale.coerceAtLeast(0.01f)).sp

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled && selected != null) 1.0f else 0.4f,
        animationSpec = DryToastMotion.opticalTween(DryToastMotion.RESULT_REVEAL_DURATION_MS),
        label = "multi_state_toggle_alpha",
    )

    BoxWithConstraints(
        modifier = modifier.height(touchHeight),
        contentAlignment = Alignment.Center,
    ) {
        val count = options.size
        val innerTrackWidth = (maxWidth - innerPadding * 2).coerceAtLeast(0.dp)
        val segmentWidth = innerTrackWidth / count

        // Crisp 32.dp (or custom `height`) visual pill housing + sliding mechanical indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .alpha(animatedAlpha)
                .clip(containerShape)
                .background(CharcoalGray)
                .border(1.dp, HairlineBorder, containerShape)
                .padding(innerPadding),
        ) {
            if (selectedIndex >= 0) {
                val targetIndicatorOffset = segmentWidth * selectedIndex
                val animatedIndicatorOffset by animateDpAsState(
                    targetValue = targetIndicatorOffset,
                    animationSpec = DryToastMotion.opticalTween(DryToastMotion.RESULT_REVEAL_DURATION_MS),
                    label = "multi_state_toggle_indicator",
                )

                Box(
                    modifier = Modifier
                        .offset(x = animatedIndicatorOffset)
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(indicatorShape)
                        .background(PureWhite),
                )
            }
        }

        // Full-height (>= 48.dp) discrete interactive segments
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = innerPadding)
                .alpha(animatedAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val tag = optionTestTag?.invoke(option)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = MIN_TOUCH_TARGET_SIZE, minHeight = MIN_TOUCH_TARGET_SIZE)
                        .fillMaxHeight()
                        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                        .semantics {
                            this.selected = isSelected
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!isSelected || !enabled) {
                                if (isResistanceState(option)) {
                                    hapticEngine.resistanceDetent()
                                } else {
                                    hapticEngine.rotaryDetent()
                                }
                                onSelect(option)
                            }
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = if (isSelected && enabled) AmoledBlack else SubtleGray,
                        fontSize = labelFontSize,
                        fontWeight = if (isSelected && enabled) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = maxLines,
                        softWrap = maxLines > 1,
                    )
                }
            }
        }
    }
}

/**
 * Combined profile override toggle:
 * - A standalone [DEFAULT] button (`>= 48.dp × 48.dp` touch target around a crisp `32.dp` visual pill).
 * - Followed by a hairline vertical separator.
 * - Followed by the 3-state slider ([ALLOW | GUARD | BLOCK]).
 */
@Composable
fun DryToastProfileOverrideToggle(
    overrideState: BaseToggleState?,
    onOverrideChange: (BaseToggleState?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    hapticEngine: HapticEngine = NoOpHapticEngine,
) {
    val touchHeight = maxOf(height, MIN_TOUCH_TARGET_SIZE)
    val density = LocalDensity.current
    val cappedFontScale = density.fontScale.coerceAtMost(1.25f)
    val labelFontSize = ((11f * cappedFontScale) / density.fontScale.coerceAtLeast(0.01f)).sp

    Row(
        modifier = modifier.height(touchHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val isDefault = overrideState == null
        val interactionSource = remember { MutableInteractionSource() }

        // [DEFAULT] Button (>= 48.dp × 48.dp interactive bounds with 32.dp visual pill frame)
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = MIN_TOUCH_TARGET_SIZE, minHeight = MIN_TOUCH_TARGET_SIZE)
                .fillMaxHeight()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (!isDefault) {
                        hapticEngine.rotaryDetent()
                        onOverrideChange(null)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDefault) PureWhite else CharcoalGray)
                    .border(1.dp, if (isDefault) PureWhite else HairlineBorder, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.toggle_default),
                    color = if (isDefault) AmoledBlack else SubtleGray,
                    fontSize = labelFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    softWrap = false,
                )
            }
        }

        // Hairline vertical notch
        Box(
            modifier = Modifier
                .height(height / 2)
                .width(1.dp)
                .background(HairlineBorder),
        )

        // 3-State Slider (dimmed when isDefault is true)
        DryToastMultiStateToggle(
            options = BaseToggleState.entries,
            selected = overrideState,
            onSelect = { newState ->
                onOverrideChange(newState)
            },
            label = { stringResource(it.labelRes) },
            modifier = Modifier.weight(1f),
            height = height,
            enabled = !isDefault,
            hapticEngine = hapticEngine,
            isResistanceState = { it == BaseToggleState.BLOCK },
        )
    }
}
