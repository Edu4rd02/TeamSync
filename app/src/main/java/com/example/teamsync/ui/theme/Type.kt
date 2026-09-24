package com.example.teamsync.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.teamsync.R

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    resId  = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight)
    )
)
val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold)
)

private fun inter(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    headlineLarge = inter(30, 42, FontWeight.Bold),
    titleLarge = inter(22, 31, FontWeight.Bold),
    titleMedium = inter(17, 24, FontWeight.SemiBold),
    labelLarge = inter(16, 22, FontWeight.SemiBold),
    labelMedium = inter(15, 21, FontWeight.SemiBold),
    labelSmall = inter(11, 15, FontWeight.SemiBold),
    bodyLarge = inter(15, 21, FontWeight.Normal),
    bodyMedium = inter(13, 18, FontWeight.Normal)
)
