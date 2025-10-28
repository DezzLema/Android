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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.functiongraph.ui.theme.FunctionGraphTheme
import kotlin.math.pow
import kotlin.math.cos

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FunctionGraphTheme {
                Scaffold(modifier = Modifier
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
    var str_max by rememberSaveable { mutableStateOf("10") }
    var str_min by rememberSaveable { mutableStateOf("-10") }

    var x_max by rememberSaveable { mutableStateOf(10f) }
    var x_min by rememberSaveable { mutableStateOf(-10f) }


    if (x_max < x_min){
        var temp = 0f
        var temp_str = " "

        temp = x_min
        x_min = x_max
        x_max = temp

        temp_str = str_min
        str_min = str_max
        str_max = temp_str
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
        Box(
            modifier = Modifier
                .size(400.dp)
                .background(color = Color(149, 149, 149))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Определяем диапазон y значений функции
                var y_min = Float.MAX_VALUE
                var y_max = Float.MIN_VALUE
                val step = (x_max - x_min) / 1000

                // Находим min и max значения y
                for (i in 0..1000) {
                    val x = x_min + i * step
                    val y = x.pow(2) * cos(x)
                    if (y < y_min) y_min = y.toFloat()
                    if (y > y_max) y_max = y.toFloat()
                }

                // Вычисляем коэффициенты масштабирования
                val scaleX = (width * 0.8f) / (x_max - x_min)  // 80% ширины для графика
                val scaleY = (height * 0.8f) / (y_max - y_min)   // 80% высоты для графика

                // Начальные и конечные точки осей с небольшими отступами для красоты
                val graphStartX = width * 0.1f
                val graphEndX = width * 0.9f
                val graphStartY = height * 0.1f
                val graphEndY = height * 0.9f

                // если видим ось х то рисуем
                if (y_min <= 0f && y_max >= 0f) {
                    val yAxisPosition = graphEndY + y_min * scaleY
                    drawLine(
                        color = Color.Black,
                        start = Offset(x = graphStartX, y = yAxisPosition),
                        end = Offset(x = graphEndX, y = yAxisPosition),
                        strokeWidth = 2.0f
                    )
                }

                // Если видим ось y то рисуем
                if (x_min <= 0f && x_max >= 0f) {
                    val xAxisPosition = graphStartX - x_min * scaleX
                    drawLine(
                        color = Color.Black,
                        start = Offset(x = xAxisPosition, y = graphStartY),
                        end = Offset(x = xAxisPosition, y = graphEndY),
                        strokeWidth = 2.0f
                    )
                }

                val xstep = (x_max - x_min) / 10
                for(i in 0..10){
                    val xvalue = x_min + i * xstep
                    val canvasX = graphStartX + (xvalue - x_min) * scaleX
                    drawLine(
                        color = Color.Black,
                        start = Offset(x = canvasX, y = graphEndY + y_min * scaleY - 5f),
                        end = Offset(x = canvasX, y = graphEndY + y_min * scaleY + 5f),
                        strokeWidth = 4f
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        "%d".format(xvalue.toInt()),
                        canvasX - 15f,
                        (graphEndY + y_min * scaleY) + 40f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 40f
                        }
                    )
                }

                val ystep = (y_max - y_min) / 10
                for (i in 0..10) {
                    val yvalue = y_min + i * ystep
                    val canvasY = graphEndY - (yvalue - y_min) * scaleY

                    drawLine(
                        color = Color.Black,
                        start = Offset(x = graphStartX - x_min * scaleX - 5f, y = canvasY),
                        end = Offset(x = graphStartX - x_min * scaleX + 5f, y = canvasY),
                        strokeWidth = 4f
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        "%d".format(yvalue.toInt()),
                        (graphStartX - x_min * scaleX) - 70f,
                        canvasY + 15f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 40f
                        }
                    )
                }

                // Рисуем график
                for (i in 0..2000) {
                    val x = x_min + i * step
                    val y = x.pow(2) * cos(x)

                    // Преобразуем математические координаты в координаты в так называемык канвы
                    val canvasX = graphStartX + (x - x_min) * scaleX
                    val canvasY = graphEndY - (y - y_min) * scaleY

                    // Проверяем, находится ли точка в пределах видимой области
                    if (canvasX in graphStartX..graphEndX && canvasY in graphStartY..graphEndY) {
                        drawCircle(
                            color = Color.Red,
                            center = Offset(x = canvasX, y = canvasY),
                            radius = 2f
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = str_max,
            onValueChange = { str_max = it },
            label = { Text(text = stringResource(R.string.default_max_x)) }
        )
        OutlinedTextField(
            value = str_min,
            onValueChange = { str_min = it },
            label = { Text(text = stringResource(R.string.default_min_x)) }
        )
        Button(
            onClick = {
                x_max = str_max.toFloatOrNull() ?: 0f
                x_min = str_min.toFloatOrNull() ?: 0f
            },
            enabled = str_max.isNotEmpty() && str_min.isNotEmpty()
        ) {
            Text(text = stringResource(R.string.draw_graph))
        }
    }
}