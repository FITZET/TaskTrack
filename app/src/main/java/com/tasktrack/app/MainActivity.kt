package com.tasktrack.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tasktrack.app.ui.MainViewModel
import com.tasktrack.app.ui.TaskTrackApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val vm: MainViewModel = viewModel()
            var pendingBackup by remember { mutableStateOf<String?>(null) }
            val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                val data = pendingBackup
                if (uri != null && data != null) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(data) }
                pendingBackup = null
            }
            val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { selected ->
                    contentResolver.openInputStream(selected)?.bufferedReader()?.use { reader ->
                        vm.restoreBackup(reader.readText())
                    }
                }
            }
            TaskTrackApp(vm,
                requestRestore = { restore.launch(arrayOf("application/json", "text/plain")) },
                saveBackup = { title, data -> pendingBackup = data; saveBackup.launch(title) }
            ) { title, text ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "导出或发送"))
            }
        }
    }
}

