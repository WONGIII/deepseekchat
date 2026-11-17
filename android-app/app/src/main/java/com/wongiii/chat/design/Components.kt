package com.wongiii.chat.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    color: Color = chatColors().accent,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bubble = Path().apply {
            val left = w * 0.12f
            val top = h * 0.10f
            val right = w * 0.88f
            val bottom = h * 0.72f
            val radius = w * 0.26f
            moveTo(left + radius, top)
            lineTo(right - radius, top)
            quadraticTo(right, top, right, top + radius)
            lineTo(right, bottom - radius)
            quadraticTo(right, bottom, right - radius, bottom)
            lineTo(w * 0.47f, bottom)
            lineTo(w * 0.30f, h * 0.92f)
            lineTo(w * 0.34f, bottom)
            lineTo(left + radius, bottom)
            quadraticTo(left, bottom, left, bottom - radius)
            lineTo(left, top + radius)
            quadraticTo(left, top, left + radius, top)
            close()
        }
        drawPath(bubble, color)
        val dot = w * 0.07f
        listOf(0.37f, 0.5f, 0.63f).forEach { x ->
            drawCircle(Color.White, dot, Offset(w * x, h * 0.41f))
        }
    }
}

@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    showTagline: Boolean = false,
    markSize: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val colors = chatColors()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(size = markSize, color = colors.accent)
        Spacer(Modifier.height(8.dp))
        Text(
            text = com.wongiii.chat.data.model.Brand.NAME,
            style = chatTypography().title.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = colors.textPrimary,
        )
        if (showTagline) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = com.wongiii.chat.data.model.Brand.TAGLINE,
                style = chatTypography().caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
fun TopBar(
    title: String?,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    muted: Boolean = true,
    onToggleMute: () -> Unit = {},
    showNewChat: Boolean = true,
    titleModifier: Modifier = Modifier,
) {
    val colors = chatColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = WebIcons.Menu20,
            contentDescription = "菜单",
            onClick = onMenu,
            size = 44.dp,
            iconSize = 20.dp,
        )
        if (title != null) {
            Text(
                text = title,
                modifier = titleModifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
                style = chatTypography().title.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        CircleIconButton(
            icon = if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
            contentDescription = if (muted) "开启自动朗读" else "关闭自动朗读",
            onClick = onToggleMute,
            size = 40.dp,
            iconSize = 22.dp,
        )
        Spacer(Modifier.width(2.dp))
        if (showNewChat) {
            CircleIconButton(
                icon = WebIcons.NewChat20,
                contentDescription = "新对话",
                onClick = onNewChat,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    outlined: Boolean = false,
    enabled: Boolean = true,
    tint: Color = chatColors().textPrimary,
) {
    val colors = chatColors()
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (outlined) Color.Transparent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (outlined) {
                Canvas(Modifier.size(size)) {
                    drawCircle(colors.textPrimary, radius = this.size.minDimension / 2f - 1f, style = Stroke(width = 1.6f))
                }
            }
            Icon(icon, contentDescription, tint = if (enabled) tint else colors.textCaption, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = chatColors()
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        color = if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text, color = Color.White, style = chatTypography().label, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = chatColors()
    val tint = if (danger) colors.danger else colors.textPrimary
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (danger) colors.dangerSoft else Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) colors.danger.copy(alpha = 0.45f) else colors.borderStrong),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp)) {
            Text(text, color = tint, style = chatTypography().label)
        }
    }
}

@Composable
fun PressableScale(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.FAST, easing = Motion.OutSoft), label = "press")
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp),
        color = chatColors().textTertiary,
        style = chatTypography().caption,
    )
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = chatColors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(CardRadius)),
        color = colors.card,
        shape = RoundedCornerShape(CardRadius),
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    showDivider: Boolean = false,
    labelModifier: Modifier = Modifier,
) {
    val colors = chatColors()
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) colors.danger else colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                modifier = labelModifier.weight(1f),
                color = if (danger) colors.danger else colors.textPrimary,
                style = chatTypography().body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Text(
                    text = value,
                    color = colors.textTertiary,
                    style = chatTypography().label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textCaption,
                modifier = Modifier.size(20.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = colors.divider,
            )
        }
    }
}

@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = chatColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = chatTypography().body, color = colors.textPrimary)
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun ToastBanner(message: String, onDismiss: () -> Unit) {
    val colors = chatColors()
    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth(),
        color = colors.toastBg,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        onClick = onDismiss,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            color = Color.White,
            style = chatTypography().label,
        )
    }
}

@Composable
fun EmptyDots(modifier: Modifier = Modifier) {
    val colors = chatColors()
    val transition = rememberInfiniteTransition(label = "waitingDots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 1300,
                        delayMillis = index * 120,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(CircleShape)
                    .background(colors.textCaption),
            )
        }
    }
}
