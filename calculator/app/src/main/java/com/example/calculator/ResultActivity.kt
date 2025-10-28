package com.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firstOperand = intent.getStringExtra("firstOperand") ?: ""
        val secondOperand = intent.getStringExtra("secondOperand") ?: ""
        val operation = intent.getIntExtra("operation", 0)
        val result = intent.getDoubleExtra("result", 0.0)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ResultScreen(
                        firstOperand = firstOperand,
                        secondOperand = secondOperand,
                        operation = operation,
                        result = result,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ResultScreen(
    firstOperand: String,
    secondOperand: String,
    operation: Int,
    result: Double,
    onBackClick: () -> Unit
) {
    val operationSymbol = when (operation) {
        0 -> "+"
        1 -> "-"
        2 -> "×"
        3 -> "÷"
        else -> "?"
    }

    val formattedResult = removeTrailingZeros(result.toString())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = stringResource(id = R.string.result_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Calculation Formula
        Text(
            text = stringResource(
                id = R.string.calculation_formula,
                removeTrailingZeros(firstOperand),
                operationSymbol,
                removeTrailingZeros(secondOperand)
            ),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Result
        Text(
            text = formattedResult,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Back Button
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(id = R.string.back_button),
                fontSize = 18.sp
            )
        }
    }
}

private fun removeTrailingZeros(number: String): String {
    return if (number.contains(".")) {
        number.replace("\\.0+$", "").replace("(\\..*?)0+$", "$1")
    } else {
        number
    }
}