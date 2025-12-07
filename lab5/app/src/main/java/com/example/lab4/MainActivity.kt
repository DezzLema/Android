package com.example.lab4

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.database.GameRecord
import com.example.database.GameRepository
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

// Модель для хранения статистики игры
data class GameStats(
    val score: Int,
    val level: Int,
    val timeSeconds: Int,
    val date: Date = Date()
)

// Модель для таблицы рекордов
data class HighScore(
    val playerName: String,
    val score: Int,
    val level: Int,
    val timeSeconds: Int,
    val date: Date = Date()
)

// Объект для управления статистикой
object GameStatistics {
    private const val PREFS_NAME = "game_stats"
    private const val KEY_TOTAL_GAMES = "total_games"
    private const val KEY_TOTAL_TIME = "total_time"
    private const val KEY_BEST_LEVEL = "best_level"

    // Сохранить рекорд в Room Database
    suspend fun saveHighScore(context: Context, highScore: HighScore) {
        val repository = GameRepository(context)

        // Сохраняем в Room
        repository.addGameResult(
            playerName = highScore.playerName,
            score = highScore.score,
            level = highScore.level,
            timeSeconds = highScore.timeSeconds
        )
    }

    // Получить топ-10 рекордов из Room Database
    suspend fun getHighScores(context: Context): List<HighScore> {
        val repository = GameRepository(context)
        val records = repository.getTop10Records()

        return records.map { record ->
            HighScore(
                playerName = record.playerName,
                score = record.score,
                level = record.level,
                timeSeconds = record.timeSeconds,
                date = record.date
            )
        }
    }

    // Получить все рекорды (для статистики)
    suspend fun getAllScores(context: Context): List<HighScore> {
        val repository = GameRepository(context)
        // Получаем Flow и преобразуем в List
        var allRecords: List<GameRecord> = emptyList()
        repository.getAllRecords().collect { records ->
            allRecords = records
        }

        return allRecords.map { record ->
            HighScore(
                playerName = record.playerName,
                score = record.score,
                level = record.level,
                timeSeconds = record.timeSeconds,
                date = record.date
            )
        }
    }

    // Получить статистику игрока
    suspend fun getPlayerStats(context: Context, playerName: String): List<HighScore> {
        val repository = GameRepository(context)
        val records = repository.getPlayerRecords(playerName)

        return records.map { record ->
            HighScore(
                playerName = record.playerName,
                score = record.score,
                level = record.level,
                timeSeconds = record.timeSeconds,
                date = record.date
            )
        }
    }

    // Обновить общую статистику
    fun updateTotalStats(context: Context, stats: GameStats) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalGames = prefs.getInt(KEY_TOTAL_GAMES, 0) + 1
        val totalTime = prefs.getInt(KEY_TOTAL_TIME, 0) + stats.timeSeconds
        val bestLevel = max(prefs.getInt(KEY_BEST_LEVEL, 1), stats.level)

        prefs.edit()
            .putInt(KEY_TOTAL_GAMES, totalGames)
            .putInt(KEY_TOTAL_TIME, totalTime)
            .putInt(KEY_BEST_LEVEL, bestLevel)
            .apply()
    }

    // Получить общую статистику
    fun getTotalStats(context: Context): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getInt(KEY_TOTAL_GAMES, 0),
            prefs.getInt(KEY_TOTAL_TIME, 0),
            prefs.getInt(KEY_BEST_LEVEL, 1)
        )
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

enum class GameState {
    WAITING, PLAYING, GAME_OVER, STATS, HIGH_SCORES
}

@Composable
fun DodgeGame() {
    var gameState by remember { mutableStateOf(GameState.WAITING) }
    var player by remember { mutableStateOf(createPlayer()) }
    var enemies by remember { mutableStateOf(listOf<GameObject>()) }
    var score by remember { mutableStateOf(0) }
    var currentLevel by remember { mutableStateOf(1) }
    var levelProgress by remember { mutableStateOf(0f) }
    var gameStartTime by remember { mutableStateOf(0L) }
    var finalStats by remember { mutableStateOf<GameStats?>(null) }
    var playerName by remember { mutableStateOf("Игрок") }

    val context = LocalContext.current
    var screenSize by remember { mutableStateOf(Offset.Zero) }

    val playerImage = ImageBitmap.imageResource(id = R.drawable.player1)
    val enemyImage = ImageBitmap.imageResource(id = R.drawable.enemy1)
    val backgroundImage = ImageBitmap.imageResource(id = R.drawable.background)

    val currentLevelConfig = getLevelConfig(currentLevel)

    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            gameStartTime = System.currentTimeMillis()
            var frameCount = 0
            while (gameState == GameState.PLAYING) {
                delay(16)
                frameCount++

                if (screenSize != Offset.Zero) {
                    player = updatePlayer(player, screenSize)
                    enemies = enemies.map { updateEnemy(it, screenSize) }

                    levelProgress = (score % 1800).toFloat() / 1800f

                    if (score > 0 && score % 1800 == 0) {
                        currentLevel++
                        val newLevelConfig = getLevelConfig(currentLevel)
                        val additionalEnemies = newLevelConfig.enemyCount - enemies.size
                        if (additionalEnemies > 0) {
                            repeat(minOf(additionalEnemies, 5)) {
                                val newEnemy = createEnemy(screenSize, newLevelConfig)
                                enemies = enemies + newEnemy
                            }
                        }
                    }

                    if (frameCount % currentLevelConfig.spawnRate == 0) {
                        if (enemies.size < currentLevelConfig.enemyCount) {
                            val newEnemy = createEnemy(screenSize, currentLevelConfig)
                            enemies = enemies + newEnemy
                        }
                    }

                    enemies = enemies.filter { enemy ->
                        val margin = 150f
                        enemy.position.x in -margin..screenSize.x + margin &&
                                enemy.position.y in -margin..screenSize.y + margin
                    }

                    if (checkCollisions(player, enemies)) {
                        val gameTimeSeconds =
                            ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                        finalStats = GameStats(
                            score = score / 60,
                            level = currentLevel,
                            timeSeconds = gameTimeSeconds
                        )
                        GameStatistics.updateTotalStats(context, finalStats!!)
                        gameState = GameState.STATS
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
                        GameState.WAITING, GameState.STATS -> {
                            // Обработка свайпов только в режиме ожидания и статистики
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
        when (gameState) {
            GameState.WAITING -> {
                MainMenu(
                    onStartGame = {
                        gameState = GameState.PLAYING
                        player = createPlayer()
                        if (screenSize != Offset.Zero) {
                            enemies = createInitialEnemies(screenSize, getLevelConfig(1))
                        }
                        score = 0
                        currentLevel = 1
                        levelProgress = 0f
                    },
                    onShowHighScores = {
                        gameState = GameState.HIGH_SCORES
                    },
                    onShowStats = {
                        gameState = GameState.STATS
                    }
                )
            }

            GameState.PLAYING -> {
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
                    if (screenSize != Offset.Zero) {
                        drawImage(
                            image = backgroundImage,
                            dstOffset = IntOffset(0, 0),
                            dstSize = IntSize(screenSize.x.toInt(), screenSize.y.toInt())
                        )

                        drawImage(
                            image = playerImage,
                            dstOffset = IntOffset(
                                (player.position.x - player.radius).toInt(),
                                (player.position.y - player.radius).toInt()
                            ),
                            dstSize = IntSize(
                                (player.radius * 2).toInt(),
                                (player.radius * 2).toInt()
                            )
                        )

                        enemies.forEach { enemy ->
                            drawImage(
                                image = enemyImage,
                                dstOffset = IntOffset(
                                    (enemy.position.x - enemy.radius).toInt(),
                                    (enemy.position.y - enemy.radius).toInt()
                                ),
                                dstSize = IntSize(
                                    (enemy.radius * 2).toInt(),
                                    (enemy.radius * 2).toInt()
                                )
                            )
                        }
                    }
                }

                // Интерфейс во время игры
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
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
                                text = "Время: ${((System.currentTimeMillis() - gameStartTime) / 1000)}с",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            GameState.STATS -> {
                StatisticsScreen(
                    stats = finalStats,
                    playerName = playerName,
                    onNameChange = { playerName = it },
                    onSaveScore = {
                        // Только переход в меню, сохранение будет внутри StatisticsScreen
                        gameState = GameState.WAITING
                    },
                    onBackToMenu = {
                        gameState = GameState.WAITING
                    }
                )
            }

            GameState.HIGH_SCORES -> {
                HighScoresScreen(
                    onBack = { gameState = GameState.WAITING }
                )
            }

            GameState.GAME_OVER -> {
                // Резервный экран (не должен появляться)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text("Игра окончена!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { gameState = GameState.WAITING }) {
                        Text("В меню")
                    }
                }
            }
        }
    }
}

@Composable
fun MainMenu(
    onStartGame: () -> Unit,
    onShowHighScores: () -> Unit,
    onShowStats: () -> Unit
) {
    val (totalGames, totalTime, bestLevel) = GameStatistics.getTotalStats(LocalContext.current)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Увернись от врагов!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Свайпайте для управления",
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Общая статистика
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Общая статистика", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Игр сыграно: $totalGames", color = Color.White)
                Text("Лучший уровень: $bestLevel", color = Color.White)
                Text("Общее время: ${totalTime / 60}м ${totalTime % 60}с", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(60.dp)
        ) {
            Text("Начать игру", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onShowHighScores,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
        ) {
            Text("Таблица рекордов", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onShowStats,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
        ) {
            Text("Статистика", fontSize = 16.sp)
        }
    }
}

@Composable
fun StatisticsScreen(
    stats: GameStats?,
    playerName: String,
    onNameChange: (String) -> Unit,
    onSaveScore: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Результаты игры",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        stats?.let { gameStats ->
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Счет: ${gameStats.score}",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Достигнут уровень: ${gameStats.level}",
                        color = Color.White, fontSize = 18.sp
                    )
                    Text(
                        "Время выживания: ${gameStats.timeSeconds / 60}м ${gameStats.timeSeconds % 60}с",
                        color = Color.White, fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Поле для имени игрока
            OutlinedTextField(
                value = playerName,
                onValueChange = onNameChange,
                label = { Text("Ваше имя") },
                modifier = Modifier.fillMaxWidth(0.8f),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = {
                    // Сохраняем результат в Room
                    scope.launch {
                        GameStatistics.saveHighScore(
                            context, HighScore(
                                playerName = if (playerName.isBlank()) "Игрок" else playerName,
                                score = gameStats.score,
                                level = gameStats.level,
                                timeSeconds = gameStats.timeSeconds
                            )
                        )
                    }
                    onSaveScore() // Вызываем callback для перехода в меню
                }) {
                    Text("Сохранить результат")
                }

                OutlinedButton(onClick = onBackToMenu) {
                    Text("В меню")
                }
            }
        } ?: run {
            Text("Нет данных о статистике", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBackToMenu) {
                Text("В меню")
            }
        }
    }
}

@Composable
fun HighScoresScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var highScores by remember { mutableStateOf<List<HighScore>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Загружаем данные при открытии экрана
    LaunchedEffect(Unit) {
        highScores = GameStatistics.getHighScores(context)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Таблица рекордов",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (highScores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Пока нет рекордов!", color = Color.White, fontSize = 18.sp)
            }
        } else {
            // Отображаем средний и максимальный счет
            var averageScore by remember { mutableStateOf<Float?>(null) }
            var maxScore by remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(highScores) {
                val repository = GameRepository(context)
                averageScore = repository.getAverageScore()
                maxScore = repository.getMaxScore()
            }

            if (averageScore != null && maxScore != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Blue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Средний счет", color = Color.White, fontSize = 12.sp)
                            Text(
                                "${String.format("%.1f", averageScore)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Рекорд", color = Color.White, fontSize = 12.sp)
                            Text("$maxScore", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                items(highScores) { score ->
                    HighScoreItem(score = score)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Назад в меню")
        }

        // Кнопка для очистки всех записей (для тестирования)
        if (!isLoading && highScores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    // Очищаем базу данных
                    CoroutineScope(Dispatchers.IO).launch {
                        val repository = GameRepository(context)
                        repository.clearAll()
                        // Обновляем список
                        highScores = emptyList() // Просто очищаем список на экране
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Очистить все записи")
            }
        }
    }
}

@Composable
fun HighScoreItem(score: HighScore) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = score.playerName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Уровень ${score.level} • ${score.timeSeconds / 60}м ${score.timeSeconds % 60}с",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }

        Text(
            text = "${score.score} очков",
            color = Color.Yellow,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }

    Divider(color = Color.White.copy(alpha = 0.3f), thickness = 1.dp)
}


private fun getLevelConfig(level: Int): GameLevel {
    return when {
        level <= 5 -> {
            GameLevel(
                levelNumber = level,
                enemyCount = 8 + (level - 1) * 3,
                baseEnemySpeed = 2f + level * 0.4f,
                enemySizeMultiplier = 1f,
                spawnRate = max(20, 60 - level * 8)
            )
        }

        level <= 10 -> {
            GameLevel(
                levelNumber = level,
                enemyCount = 20 + (level - 5) * 5,
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
    val spawnType = Random.nextInt(0, 3)
    val position = when (spawnType) {
        0 -> {
            if (Random.nextBoolean()) {
                Offset(-30f, Random.nextFloat() * screenSize.y)
            } else {
                Offset(screenSize.x + 30f, Random.nextFloat() * screenSize.y)
            }
        }

        1 -> {
            if (Random.nextBoolean()) {
                Offset(Random.nextFloat() * screenSize.x, -30f)
            } else {
                Offset(Random.nextFloat() * screenSize.x, screenSize.y + 30f)
            }
        }

        else -> {
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
    return player.copy(position = newPosition, velocity = newVelocity)
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
    return enemy.copy(position = newPosition, velocity = newVelocity)
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