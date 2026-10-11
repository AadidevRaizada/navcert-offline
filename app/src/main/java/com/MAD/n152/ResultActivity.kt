package com.MAD.n152

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.MAD.n152.ui.theme.N152Theme

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // read the value passed via the Explicit Intent
        val n = intent.getIntExtra("N_VALUE", 0)
        var sum = 0.0
        for (i in 1..n) {
            sum += 1.0 / i
        }

        setContent {
            N152Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ResultScreen(n = n, sum = sum, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ResultScreen(n: Int, sum: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Result", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
                Text("n = $n", fontSize = 16.sp)
                Text("Sum of series = %.4f".format(sum), fontSize = 18.sp)
            }
        }
    }
}
