package vn.sdevice.documentshub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun LoginScreen(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp)) }

        Spacer(Modifier.height(24.dp))
        Text("Documents Hub", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Quản lý, xem và phát mọi loại tệp. Tệp được lưu ngay trong Google Drive của bạn.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Bullet(Icons.Default.PictureAsPdf, "PDF, ảnh, video, âm thanh, văn bản và mọi định dạng khác.")
        Bullet(Icons.Default.Cloud, "Tệp nằm trong thư mục \"Documents Hub\" trên Drive, dùng chung dung lượng 15 GB của tài khoản.")
        Bullet(Icons.Default.Lock, "App chỉ truy cập được các tệp do chính nó tạo, không đọc phần còn lại của Drive.")

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { vm.signIn() },
            enabled = !vm.signingIn,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (vm.signingIn) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Đăng nhập bằng Google", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Bullet(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
