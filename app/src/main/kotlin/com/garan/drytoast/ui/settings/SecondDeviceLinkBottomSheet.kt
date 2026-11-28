package com.garan.drytoast.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garan.drytoast.R

object SecondDeviceSheetTestTags {
    const val SHEET = "second_device_link_sheet"
    const val URL_TEXT = "second_device_link_url"
    const val SHARE_BUTTON = "second_device_share_button"
    const val COPY_BUTTON = "second_device_copy_button"
    const val OPEN_HERE_BUTTON = "second_device_open_here_button"
}

/**
 * Reusable Material 3 ModalBottomSheet that explains a Console URL is meant to be opened on a
 * computer or second device (so the phone's camera can scan the QR code or connect via USB),
 * offering Share, Copy, and an "Open on this phone" fallback.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun SecondDeviceLinkBottomSheet(
    url: String,
    onOpenHere: (String) -> Unit,
    onDismiss: () -> Unit,
    onShare: ((String) -> Unit)? = null,
    onCopy: ((String) -> Unit)? = null,
    onShowMessage: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayUrl = url.removePrefix("https://").removePrefix("http://")
    val copiedToastText = stringResource(R.string.toast_link_copied)

    val shareHandler: (String) -> Unit = onShare ?: { targetUrl ->
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, targetUrl)
        }
        val chooser = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    val copyHandler: (String) -> Unit = onCopy ?: { targetUrl ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Dry Toast Console URL", targetUrl))
        if (onShowMessage != null) {
            onShowMessage(copiedToastText)
        } else {
            Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag(SecondDeviceSheetTestTags.SHEET),
        ) {
            Text(
                text = stringResource(R.string.second_device_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.second_device_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .testTag(SecondDeviceSheetTestTags.URL_TEXT),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        shareHandler(url)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(SecondDeviceSheetTestTags.SHARE_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_share_link))
                }

                OutlinedButton(
                    onClick = {
                        copyHandler(url)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(SecondDeviceSheetTestTags.COPY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_copy_link))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    onDismiss()
                    onOpenHere(url)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(SecondDeviceSheetTestTags.OPEN_HERE_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.action_open_on_this_phone),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
