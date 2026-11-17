package com.wongiii.chat.feature

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wongiii.chat.design.chatColors
import com.wongiii.chat.design.chatTypography

@Composable
fun AttachmentPanel(
    recentImages: List<Uri>,
    selectedImages: Set<Uri>,
    onToggleRecent: (Uri) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
) {
    val colors = chatColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.settingsPage,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            if (recentImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                ) {
                    items(recentImages, key = { it.toString() }) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.inputFilled)
                                    .clickable { onToggleRecent(uri) },
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uri in selectedImages) colors.accent else colors.card.copy(alpha = 0.9f)
                                    )
                                    .border(1.5.dp, colors.card, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uri in selectedImages) {
                                    Text("✓", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AttachmentTile("拍照", Icons.Outlined.PhotoCamera, onCamera, Modifier.weight(1f))
                AttachmentTile("相册", Icons.Outlined.PhotoLibrary, onGallery, Modifier.weight(1f))
                AttachmentTile("文件", Icons.Outlined.AttachFile, onFile, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chatColors()
    Surface(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = colors.card,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = colors.textPrimary, style = chatTypography().caption.copy(fontSize = 14.sp))
        }
    }
}
