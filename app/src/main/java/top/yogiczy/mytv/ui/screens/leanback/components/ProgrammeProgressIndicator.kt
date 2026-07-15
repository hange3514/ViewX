package top.yogiczy.mytv.ui.screens.leanback.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgramme.Companion.progress
import top.yogiczy.mytv.ui.utils.CurrentTime

@Composable
fun ProgrammeProgressIndicator(
    programme: EpgProgramme,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
) {
    val time by CurrentTime.ms
    val progress = remember(programme, time) { programme.progress(time) }
    Box(
        modifier = modifier
            .fillMaxWidth(progress)
            .height(3.dp)
            .background(color),
    )
}
