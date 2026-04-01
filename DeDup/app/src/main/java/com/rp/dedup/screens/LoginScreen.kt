package com.rp.dedup.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.ui.theme.PrimaryBlue

@Composable
fun LoginScreen(
    navController: NavHostController
) {
    var emailOrPhone by remember { mutableStateOf("") }

    val onLoginSuccess = {
        navController.navigate(Screen.Dashboard.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(UIConstants.GradientDarkStart, UIConstants.GradientDarkEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo with layered glow rings
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(PrimaryBlue.copy(alpha = 0.08f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(PrimaryBlue.copy(alpha = 0.14f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .background(PrimaryBlue.copy(alpha = 0.22f), CircleShape)
                )
                Image(
                    painter = painterResource(R.drawable.ic_dedup_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(36.dp))

            // Headline
            Text(
                text = "Welcome back.",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sign in to continue cleaning your gallery",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.45f)
                )
            )

            Spacer(Modifier.height(52.dp))

            // Glass-style input
            OutlinedTextField(
                value = emailOrPhone,
                onValueChange = { emailOrPhone = it },
                label = { Text("Email or Phone") },
                placeholder = { Text("example@mail.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.White.copy(alpha = 0.07f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    cursorColor = PrimaryBlue,
                    focusedLabelColor = PrimaryBlue,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.25f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.2f)
                )
            )

            Spacer(Modifier.height(16.dp))

            // Gradient continue button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(UIConstants.LoginButtonStart, UIConstants.LoginButtonEnd)
                        )
                    )
                    .clickable { onLoginSuccess() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            Spacer(Modifier.height(44.dp))

            // OR divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.1f)
                )
                Text(
                    text = "  OR  ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.3f),
                        letterSpacing = 3.sp
                    )
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Social login buttons — compact icon row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SocialIconButton("G", UIConstants.ColorGoogle, Modifier.weight(1f)) {}
                SocialIconButton("A", Color.White, Modifier.weight(1f)) {}
                SocialIconButton("f", UIConstants.ColorFacebook, Modifier.weight(1f)) {}
                SocialIconButton("X", Color.White, Modifier.weight(1f)) {}
            }

            Spacer(Modifier.height(52.dp))

            // Sign up row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.4f)
                    )
                )
                TextButton(
                    onClick = {},
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Sign Up",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SocialIconButton(
    label: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = labelColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
        )
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun LoginScreenPreview() {
    DeDupTheme {
        LoginScreen(navController = rememberNavController())
    }
}
