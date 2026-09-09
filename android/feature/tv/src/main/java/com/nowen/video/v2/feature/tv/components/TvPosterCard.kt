package com.nowen.video.v2.feature.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nowen.video.v2.core.model.MediaCard

/** TV 海报卡尺寸：比手机版 MediaPosterCard 更大，匹配 10-foot 观看距离。 */
object TvCardMetrics {
    val PosterWidth = 156.dp
    val CardRadius = 12.dp
    val CardGap = 18.dp
    val RailGap = 28.dp
}

/**
 * TV 竖版海报卡：聚焦反馈只作用于海报图片（放大 + 描边），
 * 底部标题与进度条不参与缩放，避免 Lazy 槽位固定时文字被顶出显示区域。
 */
@Composable
fun TvPosterCard(
    media: MediaCard,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TvCardMetrics.CardRadius)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .padding(4.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .tvFocusedAppearance(focused = focused, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val progress = media.normalizedProgress
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.35f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            media.displayTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
        val subtitle = listOfNotNull(
            media.year?.takeIf { it > 0 }?.toString(),
            media.resolution.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** TV 背景渐变遮罩：压暗 Hero 底图保证文字可读。 */
fun TvScrimBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.15f),
        Color.Black.copy(alpha = 0.55f),
        Color.Black.copy(alpha = 0.92f),
    ),
)
