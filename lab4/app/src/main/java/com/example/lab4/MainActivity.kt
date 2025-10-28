package com.example.lab4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DodgeGame()
        }
    }
}

data class GameObject(
    val position: Offset,
    val velocity: Offset,
    val radius: Float,
    val imageId: Int
)

@Composable
fun DodgeGame() {
    var gameState by remember { mutableStateOf(GameState.WAITING) }
    var player by remember { mutableStateOf(createPlayer()) }
    var enemies by remember { mutableStateOf(listOf<GameObject>()) }
    var score by remember { mutableStateOf(0) }

    // Размеры экрана
    var screenSize by remember { mutableStateOf(Offset.Zero) }

    // Загружаем изображения
    val playerImage = ImageBitmap.imageResource(id = R.drawable.player1)
    val enemyImage = ImageBitmap.imageResource(id = R.drawable.enemy1)
    val backgroundImage = ImageBitmap.imageResource(id = R.drawable.background)

    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            while (gameState == GameState.PLAYING) {
                delay(16)

                if (screenSize != Offset.Zero) {
                    player = updatePlayer(player, screenSize)
                    enemies = enemies.map { updateEnemy(it, screenSize) }

                    if (checkCollisions(player, enemies)) {
                        gameState = GameState.GAME_OVER
                    }

                    score++
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameState) {
                detectDragGestures { change, dragAmount ->
                    when (gameState) {
                        GameState.WAITING -> {
                            gameState = GameState.PLAYING
                            player = createPlayer()
                            if (screenSize != Offset.Zero) {
                                enemies = createEnemies(5, screenSize)
                            }
                            score = 0
                        }
                        GameState.PLAYING -> {
                            val dragVector = Offset(dragAmount.x, dragAmount.y)
                            val newVelocity = calculateNewVelocity(player.velocity, dragVector)
                            player = player.copy(velocity = newVelocity)
                        }
                        else -> {}
                    }
                }
            }
    ) {
        // Игровое поле с Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val newSize = Offset(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                    if (newSize != screenSize) {
                        screenSize = newSize
                        if (gameState == GameState.PLAYING) {
                            enemies = createEnemies(5, newSize)
                        }
                    }
                }
        ) {
            if (screenSize != Offset.Zero && gameState == GameState.PLAYING) {
                // Рисуем фон - ИСПРАВЛЕНО
                drawImage(
                    image = backgroundImage,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(screenSize.x.toInt(), screenSize.y.toInt())
                )

                // Рисуем игрока - ИСПРАВЛЕНО
                drawImage(
                    image = playerImage,
                    dstOffset = IntOffset(
                        (player.position.x - player.radius).toInt(),
                        (player.position.y - player.radius).toInt()
                    ),
                    dstSize = IntSize((player.radius * 2).toInt(), (player.radius * 2).toInt())
                )

                // Рисуем врагов - ИСПРАВЛЕНО
                enemies.forEach { enemy ->
                    drawImage(
                        image = enemyImage,
                        dstOffset = IntOffset(
                            (enemy.position.x - enemy.radius).toInt(),
                            (enemy.position.y - enemy.radius).toInt()
                        ),
                        dstSize = IntSize((enemy.radius * 2).toInt(), (enemy.radius * 2).toInt())
                    )
                }
            }
        }

        // Интерфейс
        when (gameState) {
            GameState.WAITING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "Увернись от врагов!",
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = "Свайпайте чтобы начать и управлять",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            GameState.PLAYING -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Счет: ${score / 60}",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Скорость: ${"%.1f".format(sqrt(player.velocity.x * player.velocity.x + player.velocity.y * player.velocity.y))}",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            GameState.GAME_OVER -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "Игра окончена!",
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = "Счет: ${score / 60}",
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(
                        onClick = {
                            gameState = GameState.WAITING
                        }
                    ) {
                        Text("Начать заново")
                    }
                }
            }
        }
    }
}

// Остальные функции остаются без изменений
private fun calculateNewVelocity(currentVelocity: Offset, dragVector: Offset): Offset {
    val sensitivity = 0.3f
    val velocityChange = dragVector * sensitivity

    var newVelocity = currentVelocity + velocityChange

    val maxSpeed = 8f
    val currentSpeed = sqrt(newVelocity.x * newVelocity.x + newVelocity.y * newVelocity.y)
    if (currentSpeed > maxSpeed) {
        newVelocity = newVelocity * (maxSpeed / currentSpeed)
    }

    val minSpeed = 2f
    if (currentSpeed < minSpeed && currentSpeed > 0) {
        newVelocity = newVelocity * (minSpeed / currentSpeed)
    }

    return newVelocity
}

private fun Offset.normalize(): Offset {
    val length = sqrt(x * x + y * y)
    return if (length > 0) Offset(x / length, y / length) else Offset(0f, 0f)
}

// Создание игрока с изображением
private fun createPlayer(): GameObject {
    return GameObject(
        position = Offset(200f, 200f),
        velocity = Offset(3f, 3f),
        radius = 40f,
        imageId = R.drawable.player
    )
}

// Создание врагов с изображениями
private fun createEnemies(count: Int, screenSize: Offset): List<GameObject> {
    return List(count) {
        val angle = Random.nextDouble(0.0, 2 * Math.PI).toFloat()
        val speed = Random.nextFloat() * 3 + 2
        GameObject(
            position = Offset(
                Random.nextFloat() * (screenSize.x - 80) + 40,
                Random.nextFloat() * (screenSize.y - 80) + 40
            ),
            velocity = Offset(
                cos(angle) * speed,
                sin(angle) * speed
            ),
            radius = Random.nextFloat() * 25 + 20,
            imageId = R.drawable.enemy
        )
    }
}

// Функции updatePlayer, updateEnemy, checkCollisions остаются без изменений
private fun updatePlayer(player: GameObject, screenSize: Offset): GameObject {
    var newPosition = player.position + player.velocity
    var newVelocity = player.velocity

    if (newPosition.x - player.radius < 0) {
        newPosition = newPosition.copy(x = player.radius)
        newVelocity = newVelocity.copy(x = -newVelocity.x)
    } else if (newPosition.x + player.radius > screenSize.x) {
        newPosition = newPosition.copy(x = screenSize.x - player.radius)
        newVelocity = newVelocity.copy(x = -newVelocity.x)
    }

    if (newPosition.y - player.radius < 0) {
        newPosition = newPosition.copy(y = player.radius)
        newVelocity = newVelocity.copy(y = -newVelocity.y)
    } else if (newPosition.y + player.radius > screenSize.y) {
        newPosition = newPosition.copy(y = screenSize.y - player.radius)
        newVelocity = newVelocity.copy(y = -newVelocity.y)
    }

    return player.copy(
        position = newPosition,
        velocity = newVelocity
    )
}

private fun updateEnemy(enemy: GameObject, screenSize: Offset): GameObject {
    var newPosition = enemy.position + enemy.velocity
    var newVelocity = enemy.velocity

    if (newPosition.x - enemy.radius < 0) {
        newPosition = newPosition.copy(x = enemy.radius)
        newVelocity = newVelocity.copy(x = -newVelocity.x)
    } else if (newPosition.x + enemy.radius > screenSize.x) {
        newPosition = newPosition.copy(x = screenSize.x - enemy.radius)
        newVelocity = newVelocity.copy(x = -newVelocity.x)
    }

    if (newPosition.y - enemy.radius < 0) {
        newPosition = newPosition.copy(y = enemy.radius)
        newVelocity = newVelocity.copy(y = -newVelocity.y)
    } else if (newPosition.y + enemy.radius > screenSize.y) {
        newPosition = newPosition.copy(y = screenSize.y - enemy.radius)
        newVelocity = newVelocity.copy(y = -newVelocity.y)
    }

    return enemy.copy(
        position = newPosition,
        velocity = newVelocity
    )
}

private fun checkCollisions(player: GameObject, enemies: List<GameObject>): Boolean {
    return enemies.any { enemy ->
        val dx = player.position.x - enemy.position.x
        val dy = player.position.y - enemy.position.y
        val distance = sqrt(dx * dx + dy * dy)
        distance < player.radius + enemy.radius
    }
}

enum class GameState {
    WAITING, PLAYING, GAME_OVER
}