package org.openmw.ui.overlay

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.libsdl.app.SDLActivity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ExpandableCircleButton(expandedSize: Dp = 200.dp) {
    var expanded by remember { mutableStateOf(false) }
    val animationDuration = 500 // duration in milliseconds
    val size by animateDpAsState(
        targetValue = if (expanded) expandedSize else 60.dp,
        animationSpec = tween(durationMillis = animationDuration), label = ""
    )
    val sections = listOf(
        "Quickload" to {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F6)
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F6)
        },
        "Screenshot" to {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F12)
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F12)
        },
        "F2" to {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F2)
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F2)
        },
        "Journal" to {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_J)
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_J)
        },
        "Quicksave" to {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F5)
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F5)
        },
        "F12" to {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F12)
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F12)
        }
    )
    val colors = listOf(Color.LightGray, Color.Gray, Color.LightGray, Color.Gray, Color.LightGray, Color.Gray)
    val radius = expandedSize / 2

    Box(contentAlignment = Alignment.Center) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .clickable { expanded = false }
            ) {
                PieChart(
                    sections = sections,
                    colors = colors,
                    radius = radius,
                    onSectionClick = { index ->
                        sections[index].second.invoke()
                        expanded = false
                        println("Clicked section $index")
                    }
                )
            }
        }

        Button(
            onClick = { expanded = !expanded },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent // Set your desired color here
            ),
            modifier = Modifier.size(60.dp)
        ) {
            Text("")
        }
    }
}

@Composable
fun PieChart(
    sections: List<Pair<String, () -> Unit>>,
    colors: List<Color>,
    radius: Dp,
    onSectionClick: (Int) -> Unit
) {
    val angleStep = 360f / sections.size
    val radiusPx = with(LocalDensity.current) { radius.toPx() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            sections.forEachIndexed { index, _ ->
                drawPieSegment(center, radiusPx, angleStep * index, angleStep, colors[index])
            }
        }
        sections.forEachIndexed { index, (label, _) ->
            val angle = (angleStep * index - 90) * PI / 180
            val x = cos(angle) * radiusPx / 1.5f
            val y = sin(angle) * radiusPx / 1.5f
            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(radius / 3)
                    .background(Color.Transparent)
                    .clickable { onSectionClick(index) }
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp, // Adjust text size here
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

fun DrawScope.drawPieSegment(center: Offset, radius: Float, startAngle: Float, sweepAngle: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(2 * radius, 2 * radius),
        style = Stroke(width = 5f) // Adds a border to the pie slices
    )
}

@Composable
fun HiddenMenu() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpandableCircleButton(expandedSize = 300.dp) // Change the expanded size here
    }
}
