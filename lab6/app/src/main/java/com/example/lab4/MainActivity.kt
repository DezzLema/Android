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
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.random.Random
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

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
    val id: String = "",
    val playerName: String,
    val score: Int,
    val level: Int,
    val timeSeconds: Int,
    val date: Date = Date(),
    val deviceId: String = ""
)

// Объект для управления статистикой с Firebase
object GameStatistics {
    private const val PREFS_NAME = "game_stats"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_HIGH_SCORES = "high_scores"
    private const val KEY_TOTAL_GAMES = "total_games"
    private const val KEY_TOTAL_TIME = "total_time"
    private const val KEY_BEST_LEVEL = "best_level"
    private const val KEY_BEST_SCORE = "best_score"

    // Инициализируем Firebase Firestore
    private val db = Firebase.firestore
    private const val HIGH_SCORES_COLLECTION = "high_scores"
    private const val TOTAL_STATS_DOCUMENT = "global_stats"

    // Генерируем или получаем уникальный ID устройства
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, "")

        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }

    // Сохранить рекорд в Firebase и локально
    suspend fun saveHighScore(context: Context, highScore: HighScore) {
        val deviceId = getDeviceId(context)
        val highScoreWithDevice = highScore.copy(deviceId = deviceId)

        // Сохраняем локально
        saveHighScoreLocally(context, highScoreWithDevice)

        // Сохраняем в Firebase
        try {
            val highScoreData = hashMapOf<String, Any>(
                "playerName" to highScore.playerName,
                "score" to highScore.score,
                "level" to highScore.level,
                "timeSeconds" to highScore.timeSeconds,
                "date" to highScore.date.time,
                "deviceId" to deviceId
            )

            db.collection(HIGH_SCORES_COLLECTION)
                .add(highScoreData)
                .await()

        } catch (e: Exception) {
            // Если нет интернета, сохраняем только локально
            e.printStackTrace()
        }
    }

    // Сохранить рекорд локально
    private fun saveHighScoreLocally(context: Context, highScore: HighScore) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val highScores = getHighScoresLocally(context).toMutableList()

        highScores.add(highScore)
        // Сортируем по очкам (по убыванию) и оставляем топ-10
        val sortedScores = highScores.sortedByDescending { it.score }.take(10)

        val jsonScores = sortedScores.joinToString("|") {
            "${it.playerName},${it.score},${it.level},${it.timeSeconds},${it.date.time},${it.deviceId}"
        }

        prefs.edit()
            .putString(KEY_HIGH_SCORES, jsonScores)
            .apply()
    }

    // Получить таблицу рекордов
    suspend fun getHighScores(context: Context): List<HighScore> {
        val localScores = getHighScoresLocally(context)

        try {
            // Пытаемся получить из Firebase
            val snapshot = db.collection(HIGH_SCORES_COLLECTION)
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val firebaseScores = snapshot.documents.mapNotNull { doc ->
                try {
                    HighScore(
                        id = doc.id,
                        playerName = doc.getString("playerName") ?: "Игрок",
                        score = doc.getLong("score")?.toInt() ?: 0,
                        level = doc.getLong("level")?.toInt() ?: 1,
                        timeSeconds = doc.getLong("timeSeconds")?.toInt() ?: 0,
                        date = Date(doc.getLong("date") ?: System.currentTimeMillis()),
                        deviceId = doc.getString("deviceId") ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            }

            // Объединяем и сортируем
            val allScores = (localScores + firebaseScores)
                .distinctBy { "${it.playerName}-${it.score}-${it.deviceId}" }
                .sortedByDescending { it.score }
                .take(20)

            return allScores
        } catch (e: Exception) {
            // Если нет интернета, возвращаем локальные
            e.printStackTrace()
            return localScores
        }
    }

    // Получить локальные рекорды
    private fun getHighScoresLocally(context: Context): List<HighScore> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonScores = prefs.getString(KEY_HIGH_SCORES, "") ?: ""

        if (jsonScores.isEmpty()) return emptyList()

        return jsonScores.split("|").mapNotNull { data ->
            val parts = data.split(",")
            try {
                when (parts.size) {
                    6 -> HighScore(
                        playerName = parts[0],
                        score = parts[1].toInt(),
                        level = parts[2].toInt(),
                        timeSeconds = parts[3].toInt(),
                        date = Date(parts[4].toLong()),
                        deviceId = parts[5]
                    )
                    5 -> HighScore(
                        playerName = parts[0],
                        score = parts[1].toInt(),
                        level = parts[2].toInt(),
                        timeSeconds = parts[3].toInt(),
                        date = Date(parts[4].toLong())
                    )
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Обновить общую статистику
    suspend fun updateTotalStats(context: Context, stats: GameStats) {
        // Обновляем локально
        updateTotalStatsLocally(context, stats)

        // Обновляем в Firebase (если есть интернет)
        try {
            val statsRef = db.collection(TOTAL_STATS_DOCUMENT).document("global")

            // Получаем текущие данные
            val snapshot = statsRef.get().await()

            val currentTotalGames = if (snapshot.exists()) {
                snapshot.getLong("totalGames")?.toInt() ?: 0
            } else 0

            val currentTotalTime = if (snapshot.exists()) {
                snapshot.getLong("totalTimeSeconds")?.toInt() ?: 0
            } else 0

            val currentBestLevel = if (snapshot.exists()) {
                snapshot.getLong("bestLevel")?.toInt() ?: 1
            } else 1

            val currentBestScore = if (snapshot.exists()) {
                snapshot.getLong("bestScore")?.toInt() ?: 0
            } else 0

            // Обновляем данные
            val updatedData = hashMapOf<String, Any>(
                "totalGames" to (currentTotalGames + 1),
                "totalTimeSeconds" to (currentTotalTime + stats.timeSeconds),
                "bestLevel" to max(currentBestLevel, stats.level),
                "bestScore" to max(currentBestScore, stats.score),
                "lastUpdated" to System.currentTimeMillis()
            )

            statsRef.set(updatedData).await()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Обновить локальную статистику
    private fun updateTotalStatsLocally(context: Context, stats: GameStats) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalGames = prefs.getInt(KEY_TOTAL_GAMES, 0) + 1
        val totalTime = prefs.getInt(KEY_TOTAL_TIME, 0) + stats.timeSeconds
        val bestLevel = max(prefs.getInt(KEY_BEST_LEVEL, 1), stats.level)
        val bestScore = max(prefs.getInt(KEY_BEST_SCORE, 0), stats.score)

        prefs.edit()
            .putInt(KEY_TOTAL_GAMES, totalGames)
            .putInt(KEY_TOTAL_TIME, totalTime)
            .putInt(KEY_BEST_LEVEL, bestLevel)
            .putInt(KEY_BEST_SCORE, bestScore)
            .apply()
    }

    // Получить общую статистику
    suspend fun getTotalStats(context: Context): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Сначала возвращаем локальные данные (быстрее)
        val localTotalGames = prefs.getInt(KEY_TOTAL_GAMES, 0)
        val localTotalTime = prefs.getInt(KEY_TOTAL_TIME, 0)
        val localBestLevel = prefs.getInt(KEY_BEST_LEVEL, 1)
        val localBestScore = prefs.getInt(KEY_BEST_SCORE, 0)

        // В фоне пытаемся обновить из Firebase
        try {
            val snapshot = db.collection(TOTAL_STATS_DOCUMENT)
                .document("global")
                .get()
                .await()

            if (snapshot.exists()) {
                val firebaseTotalGames = snapshot.getLong("totalGames")?.toInt() ?: 0
                val firebaseTotalTime = snapshot.getLong("totalTimeSeconds")?.toInt() ?: 0
                val firebaseBestLevel = snapshot.getLong("bestLevel")?.toInt() ?: 1
                val firebaseBestScore = snapshot.getLong("bestScore")?.toInt() ?: 0

                // Обновляем локальные данные, если Firebase вернул больше значения
                val newTotalGames = max(localTotalGames, firebaseTotalGames)
                val newTotalTime = max(localTotalTime, firebaseTotalTime)
                val newBestLevel = max(localBestLevel, firebaseBestLevel)
                val newBestScore = max(localBestScore, firebaseBestScore)

                if (newTotalGames != localTotalGames ||
                    newBestLevel != localBestLevel ||
                    newBestScore != localBestScore) {

                    prefs.edit()
                        .putInt(KEY_TOTAL_GAMES, newTotalGames)
                        .putInt(KEY_TOTAL_TIME, newTotalTime)
                        .putInt(KEY_BEST_LEVEL, newBestLevel)
                        .putInt(KEY_BEST_SCORE, newBestScore)
                        .apply()
                }

                return Triple(newTotalGames, newTotalTime, newBestLevel)
            }
        } catch (e: Exception) {
            // Просто игнорируем ошибку, используем локальные данные
            e.printStackTrace()
        }

        return Triple(localTotalGames, localTotalTime, localBestLevel)
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

    // Добавляем CoroutineScope
    val scope = rememberCoroutineScope()

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
                        val gameTimeSeconds = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                        finalStats = GameStats(
                            score = score / 60,
                            level = currentLevel,
                            timeSeconds = gameTimeSeconds
                        )
                        // Вызываем в корутине
                        scope.launch {
                            GameStatistics.updateTotalStats(context, finalStats!!)
                        }
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
                            dstSize = IntSize((player.radius * 2).toInt(), (player.radius * 2).toInt())
                        )

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
                                color = Color.White)
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
                        scope.launch {  // Добавляем корутину здесь
                            finalStats?.let { stats ->
                                GameStatistics.saveHighScore(context, HighScore(
                                    playerName = playerName,
                                    score = stats.score,
                                    level = stats.level,
                                    timeSeconds = stats.timeSeconds
                                ))
                            }
                        }
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
                // Резервный экран
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
    var totalStats by remember { mutableStateOf(Triple(0, 0, 1)) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Загружаем статистику при первом отображении
    LaunchedEffect(Unit) {
        totalStats = GameStatistics.getTotalStats(context)
        isLoading = false
    }

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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val (totalGames, totalTime, bestLevel) = totalStats
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Общая статистика", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Игр сыграно: $totalGames", color = Color.White)
                    Text("Лучший уровень: $bestLevel", color = Color.White)
                    Text("Общее время: ${totalTime / 60}м ${totalTime % 60}с",
                        color = Color.White)
                }
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
                    Text("Счет: ${gameStats.score}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Достигнут уровень: ${gameStats.level}", color = Color.White, fontSize = 18.sp)
                    Text("Время выживания: ${gameStats.timeSeconds / 60}м ${gameStats.timeSeconds % 60}с",
                        color = Color.White, fontSize = 18.sp)
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
                Button(onClick = onSaveScore) {
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
    var highScores by remember { mutableStateOf<List<HighScore>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Загружаем рекорды при открытии экрана
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
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