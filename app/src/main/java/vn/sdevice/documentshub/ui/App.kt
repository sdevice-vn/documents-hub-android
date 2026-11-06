package vn.sdevice.documentshub.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DocumentsHubApp(vm: MainViewModel = viewModel()) {
    val dark by vm.dark.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    // Hộp thoại chọn tài khoản / cấp quyền Drive của Google
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) vm.onConsentResult(res.data) else vm.consentCancelled()
    }
    val consent = vm.consent
    LaunchedEffect(consent) {
        if (consent != null) {
            consentLauncher.launch(IntentSenderRequest.Builder(consent).build())
            vm.consentLaunched()
        }
    }

    CompositionLocalProvider(LocalDocImageLoader provides vm.imageLoader) {
        DocumentsHubTheme(dark) {
            // Surface đặt màu chữ mặc định theo giao diện, nếu không chữ sẽ đen trên nền tối
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
              Box(Modifier.fillMaxSize()) {
                if (!signedIn) {
                    LoginScreen(vm)
                } else {
                    HomeScreen(vm)

                    val open = vm.openId?.let { id -> files.firstOrNull { it.id == id } }
                    LaunchedEffect(open, vm.openId) { if (vm.openId != null && open == null) vm.openId = null }
                    if (open != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .pointerInput(Unit) { detectTapGestures { } } // chặn chạm xuyên xuống danh sách
                        ) {
                            ViewerScreen(vm, open) { vm.openId = null }
                        }
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
              }
            }
        }
    }
}
