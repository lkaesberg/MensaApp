package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lkaesberg.mensaapp.Meal
import com.lkaesberg.mensaapp.SupabaseConfig
import com.lkaesberg.mensaapp.data.MenuStructure
import com.lkaesberg.mensaapp.ui.MensaTheme
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource

/**
 * Async-loaded meal photo, square-cropped with rounded corners.
 * Falls back to a tinted placeholder while loading or on error.
 */
@Composable
fun Plate(
    meal: Meal?,
    size: Dp,
    radius: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val palette = MensaTheme.palette
    val url = remember(meal?.id, meal?.imagePath, meal?.imagePathGeneric) {
        plateUrl(meal)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(palette.moss)
            .border(1.dp, Color.Black.copy(alpha = 0.04f), RoundedCornerShape(radius))
    ) {
        KamelImage(
            resource = asyncPainterResource(data = url),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onLoading = { PlatePlaceholder(palette.moss, palette.forest, modifier = Modifier.fillMaxSize()) },
            onFailure = { PlatePlaceholder(palette.moss, palette.forest, modifier = Modifier.fillMaxSize()) },
        )
    }
}

/** Variant where the plate fills its parent, used for full-bleed hero images. */
@Composable
fun PlateFill(
    meal: Meal?,
    radius: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val palette = MensaTheme.palette
    val url = remember(meal?.id, meal?.imagePath, meal?.imagePathGeneric) {
        plateUrl(meal)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(palette.moss)
    ) {
        KamelImage(
            resource = asyncPainterResource(data = url),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onLoading = { PlatePlaceholder(palette.moss, palette.forest, modifier = Modifier.fillMaxSize()) },
            onFailure = { PlatePlaceholder(palette.moss, palette.forest, modifier = Modifier.fillMaxSize()) },
        )
    }
}

/**
 * Photo of a single side dish, by name (smooth-endpoint writes one per side
 * to mensa-food/sides/). Same placeholder as [Plate] until it exists.
 */
@Composable
fun SidePhoto(
    name: String,
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp,
) {
    val palette = MensaTheme.palette
    val url = remember(name) {
        "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/mensa-food/" + MenuStructure.sideImagePath(name)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(palette.moss)
    ) {
        KamelImage(
            resource = asyncPainterResource(data = url),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onLoading = { PlatePlaceholder(palette.moss, palette.forest, modifier = Modifier.fillMaxSize()) },
            onFailure = { PlatePlaceholder(palette.moss, palette.forest, modifier = Modifier.fillMaxSize()) },
        )
    }
}

@Composable
private fun PlatePlaceholder(
    bg: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(bg))
}

internal fun plateUrl(meal: Meal?): String {
    val base = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/"
    val img = meal?.imagePath?.replace(".png", ".jpg")
    val generic = meal?.imagePathGeneric?.replace(".png", ".jpg")
    val path = "mensa-food/" + (img ?: generic ?: "mensa.png")
    return base + path
}
