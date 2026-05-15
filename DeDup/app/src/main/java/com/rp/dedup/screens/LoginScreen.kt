package com.rp.dedup.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.notifications.ToastManager
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.R
import com.rp.dedup.Screen
import com.rp.dedup.UIConstants
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import com.rp.dedup.ui.theme.PrimaryBlue
import kotlinx.coroutines.CancellationException

@Composable
fun LoginScreen(
    navController: NavHostController,
    profileViewModel: UserProfileViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastManager = remember { ToastManager(context) }
    val authManager = remember { FirebaseAuthManager(toastManager) }
    
    var isLoading by remember { mutableStateOf(false) }

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
            Spacer(Modifier.height(48.dp)) // Reduced from 72dp

            // Logo with layered glow rings
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(PrimaryBlue.copy(alpha = 0.08f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(PrimaryBlue.copy(alpha = 0.14f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(PrimaryBlue.copy(alpha = 0.22f), CircleShape)
                )
                Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_dedup_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(24.dp)) // Reduced from 36dp

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

            Spacer(Modifier.height(64.dp))

            // Google sign-in button
            OutlinedButton(
                onClick = {
                    if (!isLoading) {
                        scope.launch {
                            isLoading = true
                            try {
                                val user = authManager.signInWithGoogle(context)
                                if (user != null) {
                                    profileViewModel.update(
                                        newName = user.displayName ?: "User",
                                        newEmail = user.email ?: "",
                                        newImageUrl = user.photoUrl?.toString()
                                    )
                                    onLoginSuccess()
                                }
                            } catch (e: Exception) {
                                if (e !is CancellationException) {
                                    toastManager.showLong("Error: ${e.localizedMessage}")
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    contentColor = Color.White
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_google),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun LoginScreenPreview() {
    DeDupTheme {
        LoginScreen(
            navController = rememberNavController(),
            profileViewModel = viewModel()
        )
    }
}
