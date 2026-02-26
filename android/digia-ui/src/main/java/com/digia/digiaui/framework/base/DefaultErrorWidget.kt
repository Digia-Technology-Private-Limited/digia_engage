package com.digia.digiaui.framework.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon

@Composable
fun DefaultErrorWidget(
    errorMessage: String,
    refName: String? = null,
    errorDetails: Any? = null,
) {
    Column(
        modifier = Modifier
            .background(Color(0xFFF9E6EB))
            .padding(16.dp)
            .wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Error",
                tint = Color.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Error Rendering Widget ${refName ?: ""}",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF424242) // Equivalent to Colors.black87
            ),
            textAlign = TextAlign.Center
        )
        if (errorDetails != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Details: $errorDetails",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color.Gray
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}