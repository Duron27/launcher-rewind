package org.openmw.ui.controls

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.libsdl.app.SDLActivity.onNativeKeyDown
import org.libsdl.app.SDLActivity.onNativeKeyUp
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.isThumbDragging
import org.openmw.ui.controls.UIStateManager.logAllButtonStates
import org.openmw.ui.controls.UIStateManager.updateButtonState
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResizableDraggableThumbstick(
    context: Context,
    id: Int,
    keyCode: Int
) {
    var buttonState = UIStateManager.buttonStates.getOrPut(id) {
        mutableStateOf(ButtonState(id, 200f, 0f, 0f, false, keyCode, "Black", 0.25f))
    }.value

    var buttonSize by remember { mutableStateOf(buttonState.size.dp) }
    var buttonColor = remember { mutableStateOf(buttonState.color.toColor()) }
    var buttonAlpha by remember { mutableFloatStateOf(buttonState.alpha) }
    var offsetX by remember { mutableFloatStateOf(buttonState.offsetX) }
    var offsetY by remember { mutableFloatStateOf(buttonState.offsetY) }
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    val visible = UIStateManager.visible
    val density = LocalDensity.current
    val radiusPx = with(density) { (buttonSize / 2).toPx() }
    val deadZone = 0.2f * radiusPx
    var touchState by remember { mutableStateOf(Offset(0f, 0f)) }
    var showControlsPopup by remember { mutableStateOf(false) }
    val thumbColor = if (isThumbDragging) Color.Red.copy(alpha = buttonAlpha) else buttonColor.value.copy(alpha = buttonAlpha)

    var saveState = {
        var updatedState = buttonState.copy(
            size = buttonSize.value,
            offsetX = offsetX,
            offsetY = offsetY,
            color = buttonColor.value.toColorString(),
            alpha = buttonAlpha
        )
        UIStateManager.buttonStates[id]?.value = updatedState
        // Update the global state
        updateButtonState(buttonState.id, updatedState)
        saveButtonState(context, UIStateManager.buttonStates.values.map { it.value })
        logAllButtonStates()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { with(density) { -20.dp.roundToPx() } },
            animationSpec = tween(durationMillis = 1000)
        ) + expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = tween(durationMillis = 1000)
        ) + fadeIn(
            initialAlpha = 0.3f,
            animationSpec = tween(durationMillis = 1000)
        ),
        exit = slideOutVertically(
            targetOffsetY = { with(density) { -20.dp.roundToPx() } },
            animationSpec = tween(durationMillis = 1000)
        ) + shrinkVertically(
            animationSpec = tween(durationMillis = 1000)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 1000)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(buttonSize)
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .background(Color.Transparent)
                    .then(
                        if (editMode) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isThumbDragging = true },
                                    onDrag = { change, dragAmount ->
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        isThumbDragging = false
                                        saveState()
                                    }
                                )
                            }
                        } else Modifier
                    )
                    .border(2.dp, if (isThumbDragging) Color.Red else thumbColor, shape = CircleShape)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, thumbColor, CircleShape)
                        .then(
                            Modifier.pointerInput(Unit) {
                                if (!configureControls) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val location = Offset(event.changes.first().position.x, event.changes.first().position.y)
                                            when (event.type) {
                                                PointerEventType.Press, PointerEventType.Move -> {
                                                    val newX = (location.x - radiusPx).coerceIn(-radiusPx, radiusPx)
                                                    val newY = (location.y - radiusPx).coerceIn(-radiusPx, radiusPx)
                                                    touchState = Offset(newX, newY)

                                                    // Handle touch actions
                                                    onNativeKeyUp(KeyEvent.KEYCODE_W)
                                                    onNativeKeyUp(KeyEvent.KEYCODE_A)
                                                    onNativeKeyUp(KeyEvent.KEYCODE_S)
                                                    onNativeKeyUp(KeyEvent.KEYCODE_D)

                                                    val xRatio = touchState.x / radiusPx
                                                    val yRatio = touchState.y / radiusPx

                                                    when {
                                                        abs(yRatio) > abs(xRatio) -> {
                                                            if (touchState.y < -deadZone) onNativeKeyDown(KeyEvent.KEYCODE_W)
                                                            if (touchState.y > deadZone) onNativeKeyDown(KeyEvent.KEYCODE_S)
                                                            if (touchState.x < -deadZone) onNativeKeyDown(KeyEvent.KEYCODE_A)
                                                            if (touchState.x > deadZone) onNativeKeyDown(KeyEvent.KEYCODE_D)
                                                        }
                                                        abs(xRatio) > 0.9f -> {
                                                            if (touchState.y < -deadZone) onNativeKeyDown(KeyEvent.KEYCODE_W)
                                                            if (touchState.y > deadZone) onNativeKeyDown(KeyEvent.KEYCODE_S)
                                                            if (touchState.x < -deadZone) onNativeKeyDown(KeyEvent.KEYCODE_A)
                                                            if (touchState.x > deadZone) onNativeKeyDown(KeyEvent.KEYCODE_D)
                                                        }
                                                        else -> {
                                                            if (touchState.y < -deadZone) onNativeKeyDown(KeyEvent.KEYCODE_W)
                                                            if (touchState.y > deadZone) onNativeKeyDown(KeyEvent.KEYCODE_S)
                                                            if (touchState.x < -deadZone) onNativeKeyDown(KeyEvent.KEYCODE_A)
                                                            if (touchState.x > deadZone) onNativeKeyDown(KeyEvent.KEYCODE_D)
                                                        }
                                                    }
                                                }
                                                PointerEventType.Release, PointerEventType.Exit -> {
                                                    touchState = Offset.Zero
                                                    onNativeKeyUp(KeyEvent.KEYCODE_W)
                                                    onNativeKeyUp(KeyEvent.KEYCODE_A)
                                                    onNativeKeyUp(KeyEvent.KEYCODE_S)
                                                    onNativeKeyUp(KeyEvent.KEYCODE_D)
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                }
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .offset {
                                val offsetXx = (touchState.x / density.density).coerceIn(-radiusPx / density.density, radiusPx / density.density).dp
                                val offsetYy = (touchState.y / density.density).coerceIn(-radiusPx / density.density, radiusPx / density.density).dp
                                IntOffset(offsetXx.roundToPx(), offsetYy.roundToPx())
                            }
                            .background(
                                thumbColor,
                                shape = CircleShape
                            )
                    )
                }
                if (UIStateManager.editMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(30.dp)
                            .background(Color.Gray, shape = CircleShape)
                            .clickable { showControlsPopup = true }
                            .border(2.dp, Color.White, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    if (showControlsPopup) {
                        Popup(
                            alignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .offset { offset }
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .border(2.dp, Color.White, shape = RoundedCornerShape(8.dp))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offset = IntOffset(
                                                    offset.x + dragAmount.x.roundToInt(),
                                                    offset.y + dragAmount.y.roundToInt()
                                                )
                                            }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(text = "ID: $id", color = Color.White)
                                        Text(text = "Size: ${buttonSize.value}", color = Color.White)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // + button
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(Color.Black, shape = CircleShape)
                                                    .clickable {
                                                        buttonSize += 20.dp
                                                        saveState()
                                                    }
                                                    .border(2.dp, Color.White, shape = CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "+", color = Color.White, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(Color.Black, shape = CircleShape)
                                                    .clickable {
                                                        buttonSize -= 20.dp
                                                        if (buttonSize < 50.dp) buttonSize = 50.dp
                                                        saveState()
                                                    }
                                                    .border(2.dp, Color.White, shape = CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "-", color = Color.White, fontWeight = FontWeight.Bold)
                                            }

                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Pick a color and set alpha", color = Color.White)
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(
                                                listOf(
                                                    Color.Black,
                                                    Color.Gray,
                                                    Color.White,
                                                    Color.Red,
                                                    Color.Green,
                                                    Color.Blue,
                                                    Color.Yellow,
                                                    Color.Magenta,
                                                    Color.Cyan
                                                )
                                            ) { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(50.dp)
                                                        .background(color, shape = CircleShape)
                                                        .clickable {
                                                            buttonColor.value = color
                                                        }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Slider(
                                            value = buttonAlpha,
                                            onValueChange = { alpha ->
                                                buttonAlpha = alpha
                                            },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "Alpha: ${"%.2f".format(buttonAlpha)}",
                                            color = Color.White
                                        )
                                        Row {
                                            Button(onClick = {
                                                showControlsPopup = false
                                                saveState()
                                            }) {
                                                Text("OK", color = Color.White)
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Button(onClick = { showControlsPopup = false }) {
                                                Text("Cancel", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
