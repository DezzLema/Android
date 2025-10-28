package com.example.calculator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculatorScreen()
                }
            }
        }
    }
}

@Composable
fun CalculatorAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}

@Composable
fun CalculatorScreen() {
    var firstOperand by remember { mutableStateOf("") }
    var secondOperand by remember { mutableStateOf("") }
    var selectedOperation by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val operations = listOf(
        stringResource(id = R.string.addition),
        stringResource(id = R.string.subtraction),
        stringResource(id = R.string.multiplication),
        stringResource(id = R.string.division)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = stringResource(id = R.string.app_name),
            fontSize = 32.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // First Operand Input
        Text(
            text = stringResource(id = R.string.first_operand),
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = firstOperand,
            onValueChange = { firstOperand = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("0.0") },
            singleLine = true
        )

        // Second Operand Input
        Text(
            text = stringResource(id = R.string.second_operand),
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = secondOperand,
            onValueChange = { secondOperand = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("0.0") },
            singleLine = true
        )

        // Operation Selection
        Text(
            text = stringResource(id = R.string.select_operation),
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            operations.forEachIndexed { index, operation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOperation == index,
                        onClick = { selectedOperation = index }
                    )
                    Text(
                        text = operation,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    firstOperand = ""
                    secondOperand = ""
                    selectedOperation = 0
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(text = stringResource(id = R.string.clear))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    if (validateInput(firstOperand, secondOperand, selectedOperation, context)) {
                        val result = calculateResult(
                            firstOperand.toDouble(),
                            secondOperand.toDouble(),
                            selectedOperation
                        )

                        // Navigate to ResultActivity
                        val intent = Intent(context, ResultActivity::class.java)
                        intent.putExtra("firstOperand", firstOperand)
                        intent.putExtra("secondOperand", secondOperand)
                        intent.putExtra("operation", selectedOperation)
                        intent.putExtra("result", result)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(id = R.string.calculate))
            }
        }
    }
}

private fun validateInput(
    firstOperand: String,
    secondOperand: String,
    operation: Int,
    context: android.content.Context
): Boolean {
    return when {
        firstOperand.isBlank() || secondOperand.isBlank() -> {
            showToast(context, R.string.error_empty_field)
            false
        }
        !isValidDouble(firstOperand) || !isValidDouble(secondOperand) -> {
            showToast(context, R.string.error_invalid_number)
            false
        }
        operation == 3 && secondOperand.toDouble() == 0.0 -> {
            showToast(context, R.string.error_division_by_zero)
            false
        }
        else -> true
    }
}

private fun showToast(context: android.content.Context, messageResId: Int) {
    android.widget.Toast.makeText(context, context.getString(messageResId), android.widget.Toast.LENGTH_LONG).show()
}

private fun isValidDouble(str: String): Boolean {
    return try {
        str.toDouble()
        true
    } catch (e: NumberFormatException) {
        false
    }
}

private fun calculateResult(first: Double, second: Double, operation: Int): Double {
    return when (operation) {
        0 -> first + second
        1 -> first - second
        2 -> first * second
        3 -> first / second
        else -> 0.0
    }
}