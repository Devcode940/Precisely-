package com.eastweblite.browser.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SiteInfoDialog(
    url: String,
    onDismiss: () -> Unit,
    onClearData: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (url.startsWith("https://")) Icons.Default.Lock else Icons.Default.Public,
                    contentDescription = null,
                    tint = if (url.startsWith("https://")) Color(0xFF34D399) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Site Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text("URL: $url", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))
                
                val isHttps = url.startsWith("https://")
                Text(
                    text = if (isHttps) "Connection is secure (HTTPS)" else "Connection is NOT secure",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isHttps) Color(0xFF34D399) else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "JavaScript: Allowed",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Mixed Content: Blocked by default",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Cookies: Enabled in standard mode",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
            }
        },
        confirmButton = {
            TextButton(onClick = onClearData) {
                Text("Clear Site Data", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
