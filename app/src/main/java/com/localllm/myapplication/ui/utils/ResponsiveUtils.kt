package com.localllm.myapplication.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ResponsiveUtils {
    
    @Composable
    fun getScreenWidth(): Dp {
        val configuration = LocalConfiguration.current
        return configuration.screenWidthDp.dp
    }
    
    @Composable
    fun getScreenHeight(): Dp {
        val configuration = LocalConfiguration.current
        return configuration.screenHeightDp.dp
    }
    
    @Composable
    fun isTablet(): Boolean {
        return getScreenWidth() >= 600.dp
    }
    
    @Composable
    fun isLandscape(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.screenWidthDp > configuration.screenHeightDp
    }
    
    @Composable
    fun getResponsivePadding(): Dp {
        return if (isTablet()) 24.dp else 16.dp
    }
    
    @Composable
    fun getChatMessageMaxWidth(): Dp {
        val screenWidth = getScreenWidth()
        return when {
            screenWidth >= 600.dp -> screenWidth * 0.6f // Tablet
            screenWidth >= 400.dp -> screenWidth * 0.8f // Large phone
            else -> screenWidth * 0.85f // Small phone
        }
    }
    
    @Composable
    fun getImagePreviewHeight(): Dp {
        return if (isTablet()) 150.dp else 100.dp
    }
    
    @Composable
    fun getMessageImageHeight(): Dp {
        val screenHeight = getScreenHeight()
        return when {
            screenHeight >= 800.dp -> 250.dp // Large screen
            screenHeight >= 600.dp -> 200.dp // Medium screen
            else -> 150.dp // Small screen
        }
    }
    
    @Composable
    fun getSystemBarsPadding() = WindowInsets.systemBars.asPaddingValues()
}