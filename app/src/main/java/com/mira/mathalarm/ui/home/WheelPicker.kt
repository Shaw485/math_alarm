package com.mira.mathalarm.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mira.mathalarm.ui.theme.AppColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val REPEAT_COUNT = 100
private const val VISIBLE_ITEMS = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 56.dp,
) {
    val itemCount = items.size
    val totalItems = itemCount * REPEAT_COUNT
    val halfTotal = totalItems / 2

    val initialIndex = halfTotal + selectedIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex - 1)

    var lastReportedIndex by remember { mutableIntStateOf(selectedIndex) }

    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val scrollOffset by remember {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            firstIndex + firstOffset / itemHeightPx
        }
    }

    val centerOffset = scrollOffset + (VISIBLE_ITEMS - 1) / 2f

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset / 2
            val centerItem = layoutInfo.visibleItemsInfo.minByOrNull {
                Math.abs(it.offset + it.size / 2 - viewportCenter)
            }
            centerItem?.index ?: 0
        }
            .map { it % itemCount }
            .distinctUntilChanged()
            .collect { normalizedIndex ->
                if (normalizedIndex != lastReportedIndex) {
                    lastReportedIndex = normalizedIndex
                    onIndexChanged(normalizedIndex)
                }
            }
    }

    LaunchedEffect(selectedIndex) {
        val currentCenter = listState.layoutInfo.let { info ->
            val viewportCenter = info.viewportStartOffset + info.viewportEndOffset / 2
            val centerItem = info.visibleItemsInfo.minByOrNull {
                Math.abs(it.offset + it.size / 2 - viewportCenter)
            }
            centerItem?.index?.mod(itemCount)
        }
        if (currentCenter != null && currentCenter != selectedIndex) {
            val targetIndex = halfTotal + selectedIndex - 1
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * VISIBLE_ITEMS)
            .clip(RoundedCornerShape(12.dp))
            .background(
                AppColors.WheelBackground,
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(totalItems) { index ->
                val normalizedIndex = index % itemCount
                val distance = Math.abs(index - centerOffset)
                val distanceRatio = distance.coerceIn(0f, 2f)

                val alpha = (1f - distanceRatio * 0.5f).coerceIn(0.3f, 1f)
                val fontSize = (44 - distanceRatio * 8).coerceIn(32f, 44f).sp
                val isSelected = distanceRatio < 0.5f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .alpha(alpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[normalizedIndex],
                        fontSize = fontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) AppColors.WheelSelectedItem else AppColors.WheelUnselectedItem,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
