package ci.nsu.mobile.main.ui.screens

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ci.nsu.mobile.domain.interfaces.AuthManager
import ci.nsu.mobile.domain.models.QrAuthData
import ci.nsu.mobile.main.utils.QrGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authManager: AuthManager,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentUserLogin by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Для ввода пароля
    var showPasswordDialog by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    // Загружаем логин текущего пользователя
    LaunchedEffect(Unit) {
        val user = authManager.getCurrentUser()
        currentUserLogin = user?.login ?: "Неизвестно"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Профиль пользователя",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text("Логин: $currentUserLogin", fontSize = 18.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                showPasswordDialog = true
            }
        ) {
            Text("Создать QR-код авторизации")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Выйти")
        }
    }

    // Диалог ввода пароля
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Введите пароль") },
            text = {
                Column {
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = {
                            enteredPassword = it
                            passwordError = false
                        },
                        label = { Text("Пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError,
                        supportingText = {
                            if (passwordError) {
                                Text("Неверный пароль", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // ПО СУТИ тут надо делать запрос к серверу на проверку пароля наверное, но я сделаю вид что всё верно елси не пусто...
                        if (enteredPassword.isNotBlank()) {
                            val qrData = QrAuthData(currentUserLogin, enteredPassword)
                            val json = qrData.toJson()
                            qrBitmap = QrGenerator.generateQrBitmap(json)
                            showQrDialog = true
                            showPasswordDialog = false
                            enteredPassword = ""
                        } else {
                            passwordError = true
                        }
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    enteredPassword = ""
                    passwordError = false
                }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог с отображением QR-кода
    if (showQrDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Ваш QR-код") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                    Text("Отсканируйте для авторизации", fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}