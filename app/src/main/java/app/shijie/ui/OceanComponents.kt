package app.shijie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
fun OceanCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CafeWhite)
            .padding(padding),
        content = content,
    )
}

@Composable
fun CafeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = CafeAccent,
            contentColor = CafeWhite,
            disabledContainerColor = CafeTrack,
            disabledContentColor = CafeMuted2,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CafeChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(41.dp)
            .clip(shape)
            .background(if (selected) CafeCream else CafeWhite)
            .border(1.dp, if (selected) CafeAccent else CafeLine, shape)
            .noRippleClickable(onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) CafeAccent else CafeInk,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CafeSegmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CafeTrack)
            .padding(4.dp),
    ) {
        val segment = if (labels.isEmpty()) maxWidth else maxWidth / labels.size
        val offset by animateDpAsState(
            targetValue = segment * selected.coerceAtLeast(0),
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .height(35.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CafeAccent),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(35.dp)
                        .noRippleClickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (active) CafeWhite else CafeInk,
                        style = if (active) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun CafeRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(CardShape)
            .background(CafeWhite)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun CafeIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CafeTrack)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = CafeInk, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun CafeChoice(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(41.dp)
            .clip(shape)
            .background(if (selected) CafeCream else CafeWhite)
            .border(1.dp, if (selected) CafeAccent else CafeLine, shape)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) CafeAccent else CafeInk,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun CafeMark(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, size: Dp = 54.dp) {
    Box(
        Modifier
            .size(size)
            .clip(TileShape)
            .background(CafeCream),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun oceanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CafeInk,
    unfocusedTextColor = CafeInk,
    focusedContainerColor = CafeWhite,
    unfocusedContainerColor = CafeWhite,
    focusedBorderColor = CafeAccent,
    unfocusedBorderColor = CafeLine,
    focusedLabelColor = CafeAccent,
    unfocusedLabelColor = CafeMuted,
    cursorColor = CafeAccent,
)
