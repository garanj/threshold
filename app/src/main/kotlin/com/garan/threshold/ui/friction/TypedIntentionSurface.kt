package com.garan.threshold.ui.friction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garan.threshold.R
import com.garan.threshold.core.friction.TypedIntentionSession
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlin.random.Random

/**
 * Full-screen Typed Intention anti-impulse checkpoint for guarded applications.
 *
 * Implements the mechanical lock-tumbler sensory contract:
 * - Plain black surface with edge-to-edge drawing and keyboard insets padding.
 * - Target phrase displayed in [SubtleGray], with matched characters illuminating to [PureWhite].
 * - Wrong characters are rejected with a firm resistance detent haptic without polluting the display.
 * - "Go back" button morphs into "Open" with a target snap when the phrase is complete.
 */
@Composable
fun TypedIntentionSurface(
    appLabel: String,
    onConfirmOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    overridePhrase: String? = null,
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    val context = LocalContext.current
    val targetPhrase = remember(appLabel, overridePhrase) {
        if (overridePhrase != null) {
            overridePhrase
        } else {
            val phrases = context.resources.getStringArray(R.array.typed_intention_phrases)
            val selected = phrases[Random.nextInt(phrases.size)]
            if (selected.contains("%s")) {
                String.format(selected, appLabel)
            } else {
                selected
            }
        }
    }

    val session = remember(targetPhrase) { TypedIntentionSession(targetPhrase) }
    var matchedCount by remember(session) { mutableIntStateOf(session.matchedCount) }
    val isComplete = session.isComplete

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = session.matchedText, selection = TextRange(session.matchedText.length)))
    }

    val baseLabelSmall = MaterialTheme.typography.labelSmall
    val headerStyle = remember(baseLabelSmall) {
        baseLabelSmall.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusRequester.requestFocus()
            }
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "TYPED INTENTION",
                style = headerStyle,
                color = SubtleGray,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dual-tone illuminated phrase: matched text is PureWhite, remaining is SubtleGray
            val annotatedPhrase = remember(matchedCount, targetPhrase) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = PureWhite)) {
                        append(session.matchedText)
                    }
                    withStyle(SpanStyle(color = SubtleGray)) {
                        append(session.remainingText)
                    }
                }
            }

            Text(
                text = annotatedPhrase,
                style = MaterialTheme.typography.headlineMedium,
                lineHeight = 36.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Action Button: "Go back" (SubtleGray) morphs into "Open" (PureWhite)
            AnimatedContent(
                targetState = isComplete,
                transitionSpec = {
                    fadeIn(
                        animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
                    ) togetherWith
                        fadeOut(animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS))
                },
                label = "ActionButtonTransition",
            ) { complete ->
                Text(
                    text = if (complete) "Open" else "Go back",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (complete) PureWhite else SubtleGray,
                    modifier = Modifier
                        .clickable {
                            if (complete) {
                                onConfirmOpen()
                            } else {
                                onDismiss()
                            }
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }

        // Invisible text field capturing keyboard strokes with suggestions disabled
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                if (!session.isComplete) {
                    val newText = newValue.text
                    val oldText = session.matchedText

                    if (newText.length > oldText.length) {
                        val addedChar = newText.last()
                        when (session.typeChar(addedChar)) {
                            TypedIntentionSession.MatchResult.Matched -> {
                                matchedCount = session.matchedCount
                            }
                            TypedIntentionSession.MatchResult.Completed -> {
                                matchedCount = session.matchedCount
                                hapticEngine.targetSnap()
                            }
                            TypedIntentionSession.MatchResult.Mismatch -> {
                                hapticEngine.resistanceDetent()
                            }
                        }
                    } else if (newText.length < oldText.length) {
                        session.backspace()
                        matchedCount = session.matchedCount
                    }
                }

                val currentMatched = session.matchedText
                textFieldValue = TextFieldValue(
                    text = currentMatched,
                    selection = TextRange(currentMatched.length),
                )
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = if (isComplete) ImeAction.Go else ImeAction.None,
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (session.isComplete) {
                        onConfirmOpen()
                    }
                },
            ),
        )
    }
}
