package com.ogrchatai.app.ui.screen.chatdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ogrchatai.app.domain.model.Attachment

@Composable
fun AttachmentPreview(
    attachments: List<Attachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = attachments,
            key = { it.id }
        ) { attachment ->
            AttachmentChip(
                attachment = attachment,
                onRemove = { onRemove(attachment.id) }
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (attachment.isImage) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(attachment.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 100.dp)
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
