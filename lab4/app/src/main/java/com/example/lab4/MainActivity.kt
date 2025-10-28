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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.atan2
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
    val color: Color
)

@Composable
fun DodgeGame() {
    var gameState by remember { mutableStateOf(GameState.WAITING) }
    var player by remember { mutableStateOf(createPlayer()) }
    var enemies by remember { mutableStateOf(listOf<GameObject>()) }
    var score by remember { mutableStateOf(0) }

    // Размеры экрана
    var screenSize by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            while (gameState == GameState.PLAYING) {
                // Игровой цикл - 60 FPS
                delay(16)

                // Обновление позиций только если экран инициализирован
                if (screenSize != Offset.Zero) {
                    player = updatePlayer(player, screenSize)
                    enemies = enemies.map { updateEnemy(it, screenSize) }

                    // Проверка столкновений
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
                            // Начало игры при первом свайпе
                            gameState = GameState.PLAYING
                            player = createPlayer()
                            if (screenSize != Offset.Zero) {
                                enemies = createEnemies(5, screenSize)
                            }
                            score = 0
                        }
                        GameState.PLAYING -> {
                            // Управление направлением игрока
                            val dragVector = Offset(dragAmount.x, dragAmount.y)
                            val newVelocity = calculateNewVelocity(player.velocity, dragVector)
                            player = player.copy(velocity = newVelocity)
                        }
                        else -> {}
                    }
                }
            }
    ) {
        // Игровое поле
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
                        // Пересоздаем врагов при изменении размера экрана
                        if (gameState == GameState.PLAYING) {
                            enemies = createEnemies(5, newSize)
                        }
                    }
                }
        ) {
            // Отрисовка только если игра активна и экран инициализирован
            if (screenSize != Offset.Zero && gameState == GameState.PLAYING) {
                // Отрисовка игрока
                drawCircle(
                    color = player.color,
                    center = player.position,
                    radius = player.radius
                )

                // Отрисовка направления игрока (маленькая линия)
                val directionLine = player.position + player.velocity.normalize() * player.radius * 1.5f
                drawLine(
                    color = Color.White,
                    start = player.position,
                    end = directionLine,
                    strokeWidth = 3f
                )

                // Отрисовка врагов
                enemies.forEach { enemy ->
                    drawCircle(
                        color = enemy.color,
                        center = enemy.position,
                        radius = enemy.radius
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
                        text = "Увернись от шаров!",
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
                    Text(
                        text = "Управление: свайпайте в нужном направлении",
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

// Функция для вычисления нового направления на основе свайпа
private fun calculateNewVelocity(currentVelocity: Offset, dragVector: Offset): Offset {
    // Базовое изменение скорости (можно настроить чувствительность)
    val sensitivity = 0.3f
    val velocityChange = dragVector * sensitivity

    // Добавляем изменение к текущей скорости
    var newVelocity = currentVelocity + velocityChange

    // Ограничиваем максимальную скорость
    val maxSpeed = 8f
    val currentSpeed = sqrt(newVelocity.x * newVelocity.x + newVelocity.y * newVelocity.y)
    if (currentSpeed > maxSpeed) {
        newVelocity = newVelocity * (maxSpeed / currentSpeed)
    }

    // Гарантируем минимальную скорость
    val minSpeed = 2f
    if (currentSpeed < minSpeed && currentSpeed > 0) {
        newVelocity = newVelocity * (minSpeed / currentSpeed)
    }

    return newVelocity
}

// Функция для нормализации вектора (приведение к длине 1)
private fun Offset.normalize(): Offset {
    val length = sqrt(x * x + y * y)
    return if (length > 0) Offset(x / length, y / length) else Offset(0f, 0f)
}

// Создание игрока
private fun createPlayer(): GameObject {
    return GameObject(
        position = Offset(200f, 200f),
        velocity = Offset(3f, 3f),
        radius = 25f, // Немного увеличим радиус для лучшей видимости
        color = Color.Blue
    )
}

// Создание врагов
private fun createEnemies(count: Int, screenSize: Offset): List<GameObject> {
    return List(count) {
        val angle = Random.nextDouble(0.0, 2 * Math.PI).toFloat()
        val speed = Random.nextFloat() * 3 + 2
        GameObject(
            position = Offset(
                Random.nextFloat() * (screenSize.x - 40) + 20,
                Random.nextFloat() * (screenSize.y - 40) + 20
            ),
            velocity = Offset(
                cos(angle) * speed,
                sin(angle) * speed
            ),
            radius = Random.nextFloat() * 15 + 10,
            color = Color.Red
        )
    }
}

// Обновление позиции игрока
private fun updatePlayer(player: GameObject, screenSize: Offset): GameObject {
    var newPosition = player.position + player.velocity
    var newVelocity = player.velocity

    // Отскок от стен с корректной позицией
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

// Обновление позиции врагов
private fun updateEnemy(enemy: GameObject, screenSize: Offset): GameObject {
    var newPosition = enemy.position + enemy.velocity
    var newVelocity = enemy.velocity

    // Отскок от стен
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

// Проверка столкновений
private fun checkCollisions(player: GameObject, enemies: List<GameObject>): Boolean {
    return enemies.any { enemy ->
        val dx = player.position.x - enemy.position.x
        val dy = player.position.y - enemy.position.y
        val distance = sqrt(dx * dx + dy * dy)
        distance < player.radius + enemy.radius
    }
}

// Состояния игры
enum class GameState {
    WAITING, PLAYING, GAME_OVER
}