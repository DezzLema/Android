package com.example.lab4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DodgeGame()
                }
            }
        }
    }
}

data class GameObject(
    val position: Offset,
    val velocity: Offset,
    val radius: Float,
    val imageId: Int
)

data class GameLevel(
    val levelNumber: Int,
    val enemyCount: Int,
    val baseEnemySpeed: Float,
    val enemySizeMultiplier: Float,
    val spawnRate: Int
)

@Composable
fun DodgeGame() {
    var gameState by remember { mutableStateOf(GameState.WAITING) }
    var player by remember { mutableStateOf(createPlayer()) }
    var enemies by remember { mutableStateOf(listOf<GameObject>()) }
    var score by remember { mutableStateOf(0) }
    var currentLevel by remember { mutableStateOf(1) }
    var levelProgress by remember { mutableStateOf(0f) }

    var screenSize by remember { mutableStateOf(Offset.Zero) }

    val playerImage = ImageBitmap.imageResource(id = R.drawable.player1)
    val enemyImage = ImageBitmap.imageResource(id = R.drawable.enemy1)
    val backgroundImage = ImageBitmap.imageResource(id = R.drawable.background)

    val currentLevelConfig = getLevelConfig(currentLevel)

    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            var frameCount = 0
            while (gameState == GameState.PLAYING) {
                delay(16)
                frameCount++

                if (screenSize != Offset.Zero) {
                    player = updatePlayer(player, screenSize)
                    enemies = enemies.map { updateEnemy(it, screenSize) }

                    levelProgress = (score % 1800).toFloat() / 1800f

                    // Переход на следующий уровень каждые 30 секунд
                    if (score > 0 && score % 1800 == 0) {
                        currentLevel++
                        // ДОБАВЛЯЕМ новых врагов при переходе на новый уровень
                        val newLevelConfig = getLevelConfig(currentLevel)
                        val additionalEnemies = newLevelConfig.enemyCount - enemies.size
                        if (additionalEnemies > 0) {
                            repeat(minOf(additionalEnemies, 5)) {
                                val newEnemy = createEnemy(screenSize, newLevelConfig)
                                enemies = enemies + newEnemy
                            }
                        }
                    }

                    // ЛОГИКА СПАУНА ВРАГОВ - только добавление
                    if (frameCount % currentLevelConfig.spawnRate == 0) {
                        // Просто добавляем нового врага, если не превышен лимит
                        if (enemies.size < currentLevelConfig.enemyCount) {
                            val newEnemy = createEnemy(screenSize, currentLevelConfig)
                            enemies = enemies + newEnemy
                        }
                    }

                    // Удаление только тех врагов, которые действительно далеко за пределами экрана
                    enemies = enemies.filter { enemy ->
                        val margin = 150f
                        enemy.position.x in -margin..screenSize.x + margin &&
                                enemy.position.y in -margin..screenSize.y + margin
                    }

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
                                enemies = createInitialEnemies(screenSize, getLevelConfig(1))
                            }
                            score = 0
                            currentLevel = 1
                            levelProgress = 0f
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
                    }
                }
        ) {
            if (screenSize != Offset.Zero && gameState == GameState.PLAYING) {
                // Рисуем фон
                drawImage(
                    image = backgroundImage,
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(screenSize.x.toInt(), screenSize.y.toInt())
                )

                // Рисуем игрока
                drawImage(
                    image = playerImage,
                    dstOffset = IntOffset(
                        (player.position.x - player.radius).toInt(),
                        (player.position.y - player.radius).toInt()
                    ),
                    dstSize = IntSize((player.radius * 2).toInt(), (player.radius * 2).toInt())
                )

                // Рисуем врагов
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
                        modifier = Modifier.padding(16.dp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Свайпайте чтобы начать и управлять",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Сложность растет с каждым уровнем!",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }

            GameState.PLAYING -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    // Полупрозрачный фон для текста
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Уровень: $currentLevel",
                                modifier = Modifier.padding(bottom = 4.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Прогресс: ${(levelProgress * 100).toInt()}%",
                                modifier = Modifier.padding(bottom = 6.dp),
                                color = Color.White
                            )
                            Text(
                                text = "Счет: ${score / 60}",
                                modifier = Modifier.padding(bottom = 4.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Врагов: ${enemies.size}/${currentLevelConfig.enemyCount}",
                                modifier = Modifier.padding(bottom = 4.dp),
                                color = Color.White
                            )
                            Text(
                                text = "Скорость: ${"%.1f".format(sqrt(player.velocity.x * player.velocity.x + player.velocity.y * player.velocity.y))}",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            GameState.GAME_OVER -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Игра окончена!",
                                modifier = Modifier.padding(bottom = 16.dp),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Достигнут уровень: $currentLevel",
                                modifier = Modifier.padding(bottom = 8.dp),
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Счет: ${score / 60}",
                                modifier = Modifier.padding(bottom = 24.dp),
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Button(
                                onClick = {
                                    gameState = GameState.WAITING
                                }
                            ) {
                                Text("Начать заново", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Конфигурация уровней
private fun getLevelConfig(level: Int): GameLevel {
    return when {
        level <= 5 -> {
            GameLevel(
                levelNumber = level,
                enemyCount = 8 + (level - 1) * 3, // 8, 11, 14, 17, 20 врагов
                baseEnemySpeed = 2f + level * 0.4f,
                enemySizeMultiplier = 1f,
                spawnRate = max(20, 60 - level * 8) // Частая генерация
            )
        }
        level <= 10 -> {
            GameLevel(
                levelNumber = level,
                enemyCount = 20 + (level - 5) * 5, // 25, 30, 35, 40, 45 врагов
                baseEnemySpeed = 4f + (level - 5) * 0.5f,
                enemySizeMultiplier = 0.8f,
                spawnRate = max(15, 40 - (level - 5) * 5)
            )
        }
        else -> {
            GameLevel(
                levelNumber = level,
                enemyCount = 45 + (level - 10) * 4,
                baseEnemySpeed = 6.5f + (level - 10) * 0.3f,
                enemySizeMultiplier = 0.6f,
                spawnRate = max(10, 20 - (level - 10))
            )
        }
    }
}

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

private fun createPlayer(): GameObject {
    return GameObject(
        position = Offset(200f, 200f),
        velocity = Offset(3f, 3f),
        radius = 40f,
        imageId = R.drawable.player1
    )
}

private fun createEnemy(screenSize: Offset, levelConfig: GameLevel): GameObject {
    val angle = Random.nextDouble(0.0, 2 * Math.PI).toFloat()
    val speedVariation = Random.nextFloat() * 1.5f - 0.75f
    val baseSpeed = levelConfig.baseEnemySpeed

    // Разные стратегии спауна для разнообразия
    val spawnType = Random.nextInt(0, 3)
    val position = when (spawnType) {
        0 -> {
            // Спаун с краев
            if (Random.nextBoolean()) {
                Offset(-30f, Random.nextFloat() * screenSize.y)
            } else {
                Offset(screenSize.x + 30f, Random.nextFloat() * screenSize.y)
            }
        }
        1 -> {
            // Спаун сверху/снизу
            if (Random.nextBoolean()) {
                Offset(Random.nextFloat() * screenSize.x, -30f)
            } else {
                Offset(Random.nextFloat() * screenSize.x, screenSize.y + 30f)
            }
        }
        else -> {
            // Обычный спаун в пределах экрана
            Offset(
                Random.nextFloat() * (screenSize.x - 80) + 40,
                Random.nextFloat() * (screenSize.y - 80) + 40
            )
        }
    }

    return GameObject(
        position = position,
        velocity = Offset(
            cos(angle) * (baseSpeed + speedVariation),
            sin(angle) * (baseSpeed + speedVariation)
        ),
        radius = (Random.nextFloat() * 15 + 20) * levelConfig.enemySizeMultiplier,
        imageId = R.drawable.enemy1
    )
}

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

private fun createInitialEnemies(screenSize: Offset, levelConfig: GameLevel): List<GameObject> {
    return List(minOf(5, levelConfig.enemyCount)) {
        createEnemy(screenSize, levelConfig)
    }
}

private fun minOf(a: Int, b: Int): Int {
    return if (a < b) a else b
}

enum class GameState {
    WAITING, PLAYING, GAME_OVER
}