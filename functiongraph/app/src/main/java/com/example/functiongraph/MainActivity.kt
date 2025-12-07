package com.example.functiongraph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.functiongraph.ui.theme.FunctionGraphTheme
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FunctionGraphTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray)
                ) { innerPadding ->
                    Main(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Main(modifier: Modifier = Modifier) {
    var strMax by rememberSaveable { mutableStateOf("2.0") }
    var strMin by rememberSaveable { mutableStateOf("-2.0") }

    var xMax by rememberSaveable { mutableStateOf(2.0f) }
    var xMin by rememberSaveable { mutableStateOf(-2.0f) }

    // Функция для вычисления y = 1 - x²
    val function: (Float) -> Float = { x -> 1 - x.pow(2) }

    // Обработка неправильного порядка границ
    if (xMax < xMin) {
        val temp = xMin
        xMin = xMax
        xMax = temp

        val tempStr = strMin
        strMin = strMax
        strMax = tempStr
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.graph),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0, 0, 255)
        )

        Text(
            text = "y = 1 - x²",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0, 100, 0),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .size(400.dp)
                .background(color = Color.White)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Определяем диапазон y значений функции
                var yMin = Float.MAX_VALUE
                var yMax = Float.MIN_VALUE
                val step = (xMax - xMin) / 1000

                // Находим min и max значения y
                for (i in 0..1000) {
                    val x = xMin + i * step
                    val y = function(x)
                    if (y < yMin) yMin = y
                    if (y > yMax) yMax = y
                }

                // Добавляем небольшой отступ для лучшего отображения
                val yRange = yMax - yMin
                yMin -= yRange * 0.1f
                yMax += yRange * 0.1f

                // Вычисляем коэффициенты масштабирования
                val scaleX = (width * 0.8f) / (xMax - xMin)
                val scaleY = (height * 0.8f) / (yMax - yMin)

                // Границы графика с отступами
                val graphStartX = width * 0.1f
                val graphEndX = width * 0.9f
                val graphStartY = height * 0.1f
                val graphEndY = height * 0.9f

                // Рисуем оси координат
                // Ось X (если видна)
                if (yMin <= 0f && yMax >= 0f) {
                    val yAxisPosition = graphEndY - (0f - yMin) * scaleY
                    drawLine(
                        color = Color.Black,
                        start = Offset(x = graphStartX, y = yAxisPosition),
                        end = Offset(x = graphEndX, y = yAxisPosition),
                        strokeWidth = 2.0f
                    )
                }

                // Ось Y (если видна)
                if (xMin <= 0f && xMax >= 0f) {
                    val xAxisPosition = graphStartX + (0f - xMin) * scaleX
                    drawLine(
                        color = Color.Black,
                        start = Offset(x = xAxisPosition, y = graphStartY),
                        end = Offset(x = xAxisPosition, y = graphEndY),
                        strokeWidth = 2.0f
                    )
                }

                // Разметка оси X
                val xStep = (xMax - xMin) / 10
                for (i in 0..10) {
                    val xValue = xMin + i * xStep
                    val canvasX = graphStartX + (xValue - xMin) * scaleX

                    // Линия разметки
                    if (yMin <= 0f && yMax >= 0f) {
                        val yAxisPos = graphEndY - (0f - yMin) * scaleY
                        drawLine(
                            color = Color.Gray,
                            start = Offset(x = canvasX, y = yAxisPos - 5f),
                            end = Offset(x = canvasX, y = yAxisPos + 5f),
                            strokeWidth = 2f
                        )
                    }

                    // Подпись
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.1f".format(xValue),
                        canvasX - 15f,
                        graphEndY + 20f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 30f
                        }
                    )
                }

                // Разметка оси Y
                val yStep = (yMax - yMin) / 10
                for (i in 0..10) {
                    val yValue = yMin + i * yStep
                    val canvasY = graphEndY - (yValue - yMin) * scaleY

                    // Линия разметки
                    if (xMin <= 0f && xMax >= 0f) {
                        val xAxisPos = graphStartX + (0f - xMin) * scaleX
                        drawLine(
                            color = Color.Gray,
                            start = Offset(x = xAxisPos - 5f, y = canvasY),
                            end = Offset(x = xAxisPos + 5f, y = canvasY),
                            strokeWidth = 2f
                        )
                    }

                    // Подпись
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.1f".format(yValue),
                        graphStartX - 50f,
                        canvasY + 10f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 30f
                        }
                    )
                }

                // Рисуем график плавной линией
                val path = Path()
                var isFirstPoint = true

                for (i in 0..1000) {
                    val x = xMin + i * step
                    val y = function(x)

                    val canvasX = graphStartX + (x - xMin) * scaleX
                    val canvasY = graphEndY - (y - yMin) * scaleY

                    if (isFirstPoint) {
                        path.moveTo(canvasX, canvasY)
                        isFirstPoint = false
                    } else {
                        path.lineTo(canvasX, canvasY)
                    }
                }

                // Рисуем плавную линию графика
                drawPath(
                    path = path,
                    color = Color.Blue,
                    alpha = 1.0f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = strMin,
                onValueChange = { strMin = it },
                label = { Text(text = "Минимальное значение X") }
            )
            OutlinedTextField(
                value = strMax,
                onValueChange = { strMax = it },
                label = { Text(text = "Максимальное значение X") }
            )
            Button(
                onClick = {
                    xMax = strMax.toFloatOrNull() ?: 2.0f
                    xMin = strMin.toFloatOrNull() ?: -2.0f
                },
                enabled = strMax.isNotEmpty() && strMin.isNotEmpty()
            ) {
                Text(text = "Построить график")
            }
        }
    }
}