package com.garan.threshold.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine

private val AmoledBlack = Color(0xFF000000)
private val CardBackground = Color(0xFF121212)
private val HairlineBorder = Color(0xFF222222)
private val PureWhite = Color(0xFFFFFFFF)
private val SubtleGray = Color(0xFF888888)

private val DAY_ABBREVIATIONS = listOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

/**
 * Minimalist modal dialog for configuring schedule days and time windows.
 */
@Composable
fun ScheduleEditorDialog(
    initialSchedule: ScheduleTrigger,
    onSave: (ScheduleTrigger) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
) {
    var selectedDays by remember { mutableStateOf(initialSchedule.days) }
    var isAllDay by remember { mutableStateOf(initialSchedule.isAllDay) }
    var startHour by remember { mutableIntStateOf(initialSchedule.startTime.hour) }
    var startMinute by remember { mutableIntStateOf(initialSchedule.startTime.minute) }
    var endHour by remember { mutableIntStateOf(initialSchedule.endTime.hour) }
    var endMinute by remember { mutableIntStateOf(initialSchedule.endTime.minute) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, HairlineBorder, RoundedCornerShape(4.dp)),
            color = CardBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "Configure Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                )
                Text(
                    text = "Profile activates during these recurring times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleGray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                // Day-of-week Pills
                Text(
                    text = "ACTIVE DAYS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = SubtleGray,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DAY_ABBREVIATIONS.forEachIndexed { index, (day, label) ->
                        val isSelected = day in selectedDays
                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) PureWhite else AmoledBlack)
                                .border(1.dp, if (isSelected) PureWhite else HairlineBorder, RoundedCornerShape(2.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Checkbox,
                                ) {
                                    hapticEngine.rotaryDetent()
                                    selectedDays = if (isSelected) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AmoledBlack else PureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // All-Day Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "All day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = PureWhite,
                        )
                        Text(
                            text = "Active for the full 24 hours of selected days",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleGray,
                        )
                    }
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = {
                            hapticEngine.rotaryDetent()
                            isAllDay = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmoledBlack,
                            checkedTrackColor = PureWhite,
                            uncheckedThumbColor = SubtleGray,
                            uncheckedTrackColor = CardBackground,
                            uncheckedBorderColor = HairlineBorder,
                        ),
                    )
                }

                if (!isAllDay) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = HairlineBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Time Windows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TimeStepperColumn(
                            title = "START TIME",
                            hour = startHour,
                            minute = startMinute,
                            onHourChange = {
                                hapticEngine.rotaryDetent()
                                startHour = (it + 24) % 24
                            },
                            onMinuteChange = {
                                hapticEngine.rotaryDetent()
                                startMinute = (it + 60) % 60
                            },
                        )

                        Text(
                            text = "→",
                            style = MaterialTheme.typography.titleLarge,
                            color = SubtleGray,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )

                        TimeStepperColumn(
                            title = "END TIME",
                            hour = endHour,
                            minute = endMinute,
                            onHourChange = {
                                hapticEngine.rotaryDetent()
                                endHour = (it + 24) % 24
                            },
                            onMinuteChange = {
                                hapticEngine.rotaryDetent()
                                endMinute = (it + 60) % 60
                            },
                        )
                    }

                    if (startHour > endHour || (startHour == endHour && startMinute > endMinute)) {
                        Text(
                            text = "☾ Spans overnight past midnight into following day",
                            style = MaterialTheme.typography.labelSmall,
                            color = SubtleGray,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = SubtleGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newSchedule = ScheduleTrigger(
                                days = selectedDays,
                                startTime = TimeOfDay(startHour, startMinute),
                                endTime = TimeOfDay(endHour, endMinute),
                                isAllDay = isAllDay,
                            )
                            onSave(newSchedule)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureWhite,
                            contentColor = AmoledBlack,
                        ),
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(text = "Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeStepperColumn(
    title: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = SubtleGray,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Hour
            StepperBox(
                value = hour.toString().padStart(2, '0'),
                onIncrement = { onHourChange(hour + 1) },
                onDecrement = { onHourChange(hour - 1) },
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PureWhite,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // Minute (step by 5)
            StepperBox(
                value = minute.toString().padStart(2, '0'),
                onIncrement = { onMinuteChange((minute + 5) / 5 * 5) },
                onDecrement = { onMinuteChange((minute - 5 + 60) / 5 * 5) },
            )
        }
    }
}

@Composable
private fun StepperBox(
    value: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(44.dp)
            .background(AmoledBlack)
            .border(1.dp, HairlineBorder, RoundedCornerShape(2.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIncrement() }
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "▲", fontSize = 9.sp, color = SubtleGray)
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
            modifier = Modifier.padding(vertical = 2.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDecrement() }
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "▼", fontSize = 9.sp, color = SubtleGray)
        }
    }
}
