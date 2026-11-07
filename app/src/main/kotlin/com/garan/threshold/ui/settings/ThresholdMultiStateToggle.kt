package com.garan.threshold.ui.settings

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.CharcoalGray
import com.garan.threshold.ui.theme.HairlineBorder
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray

/**
 * Standard 3-state toggle positions for the Base / Default Profile.
 * Balanced 5-letter verbs: ALLOW, GUARD, BLOCK.
 */
enum class BaseToggleState(val label: String) {
    ALLOW("ALLOW"),
    GUARD("GUARD"),
    BLOCK("BLOCK"),
}

/**
 * Precision mechanical segmented slider toggle.
 *
 * Implements Threshold's tactile instrument aesthetic:
 * - Solid monochrome sliding indicator.
 * - Decelerating optical easing (`cubic-bezier(0.16, 1.0, 0.3, 1.0)`).
 * - Hairline framing (1dp rule) with crisp zero-radius or minimal corners.
 * - Distinct micro-haptic detents on transitions (`PRIMITIVE_CLICK` / resistance detent).
 * - When [selected] is null or [enabled] is false, the slider is visually dimmed to 40% with no indicator.
 *
 * @param options Discrete ordered states to present.
 * @param selected The currently active state, or null if no state is selected / unmanaged.
 * @param onSelect Callback invoked when a state is clicked.
 * @param label Text extraction for each state.
 * @param modifier Composable modifier for sizing and placement.
 * @param height Height of the mechanical toggle housing (defaults to 32.dp).
 * @param enabled Whether the toggle is active or dimmed.
 * @param hapticEngine Tactile feedback provider.
 * @param isResistanceState Predicate determining if entering a state triggers a resistance detent.
 */
@Composable
fun <T> ThresholdMultiStateToggle(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    enabled: Boolean = true,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    isResistanceState: (T) -> Boolean = { false },
) {
    require(options.isNotEmpty()) { "Options list cannot be empty" }
    val selectedIndex = if (selected != null) options.indexOf(selected) else -1

    val innerPadding = 2.dp
    val containerShape = RoundedCornerShape(2.dp)
    val indicatorShape = RoundedCornerShape(1.dp)

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled && selected != null) 1.0f else 0.4f,
        animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.RESULT_REVEAL_DURATION_MS),
        label = "multi_state_toggle_alpha",
    )

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .alpha(animatedAlpha)
            .clip(containerShape)
            .background(CharcoalGray)
            .border(1.dp, HairlineBorder, containerShape)
            .padding(innerPadding),
    ) {
        val count = options.size
        val availableWidth = maxWidth
        val segmentWidth = availableWidth / count

        if (selectedIndex >= 0) {
            val targetIndicatorOffset = segmentWidth * selectedIndex
            val animatedIndicatorOffset by animateDpAsState(
                targetValue = targetIndicatorOffset,
                animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.RESULT_REVEAL_DURATION_MS),
                label = "multi_state_toggle_indicator",
            )

            // Sliding Mechanical Indicator
            Box(
                modifier = Modifier
                    .offset(x = animatedIndicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(indicatorShape)
                    .background(PureWhite),
            )
        }

        // Discrete Interactive Segments
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
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
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = if (isSelected && enabled) AmoledBlack else SubtleGray,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected && enabled) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Combined profile override toggle implementing Approach 1:
 * - A standalone [DEFAULT] button.
 * - Followed by a hairline vertical separator.
 * - Followed by the 3-state slider ([STD | GUARD | BLOCK]).
 *
 * When [overrideState] is null (Default / Inactive):
 * - [DEFAULT] is highlighted in pure white with black text.
 * - The 3-state slider is dimmed to 40% opacity with no active sliding indicator.
 *
 * When [overrideState] is set ([BaseToggleState.STANDARD], [BaseToggleState.GUARD], or [BaseToggleState.BLOCK]):
 * - [DEFAULT] is unselected (subtle outline).
 * - The 3-state slider is active at 100% opacity with the solid white sliding indicator.
 *
 * @param overrideState The explicit override state, or null if the app inherits the default profile.
 * @param onOverrideChange Callback invoked when the override changes or is reset to null.
 * @param modifier Composable modifier for sizing and placement.
 * @param height Height of the controls (defaults to 32.dp).
 * @param hapticEngine Tactile feedback provider.
 */
@Composable
fun ThresholdProfileOverrideToggle(
    overrideState: BaseToggleState?,
    onOverrideChange: (BaseToggleState?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    hapticEngine: HapticEngine = NoOpHapticEngine,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val isDefault = overrideState == null
        val interactionSource = remember { MutableInteractionSource() }

        // [DEFAULT] Button
        Box(
            modifier = Modifier
                .height(height)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isDefault) PureWhite else CharcoalGray)
                .border(1.dp, if (isDefault) PureWhite else HairlineBorder, RoundedCornerShape(2.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (!isDefault) {
                        hapticEngine.rotaryDetent()
                        onOverrideChange(null)
                    }
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "DEFAULT",
                color = if (isDefault) AmoledBlack else SubtleGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }

        // Hairline vertical notch
        Box(
            modifier = Modifier
                .height(height / 2)
                .width(1.dp)
                .background(HairlineBorder),
        )

        // 3-State Slider (dimmed when isDefault is true)
        ThresholdMultiStateToggle(
            options = BaseToggleState.entries,
            selected = overrideState,
            onSelect = { newState ->
                onOverrideChange(newState)
            },
            label = { it.label },
            modifier = Modifier.weight(1f),
            height = height,
            enabled = !isDefault,
            hapticEngine = hapticEngine,
            isResistanceState = { it == BaseToggleState.BLOCK },
        )
    }
}
