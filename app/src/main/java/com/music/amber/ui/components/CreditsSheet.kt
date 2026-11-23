package com.music.amber.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.amber.R
import com.music.amber.data.credits.Credit
import com.music.amber.data.credits.CreditRole
import com.music.amber.data.credits.MusicBrainz
import com.music.amber.data.credits.TrackCredits
import com.music.amber.data.model.Song

private sealed interface CreditsState {
    data object Loading : CreditsState
    data object Failed : CreditsState
    data object None : CreditsState
    data class Found(val credits: TrackCredits) : CreditsState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSheet(song: Song, onDismiss: () -> Unit) {
    val state by produceState<CreditsState>(CreditsState.Loading, song.videoId) {
        val durationMs = song.durationText?.let(::parseDurationMs)
        value = MusicBrainz.credits(song.title, song.artist, durationMs).fold(
            onSuccess = { it?.let(CreditsState::Found) ?: CreditsState.None },
            onFailure = { CreditsState.Failed },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.credits), style = MaterialTheme.typography.titleLarge)
            Text(song.title, style = MaterialTheme.typography.titleMedium)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val current = state) {
                CreditsState.Loading -> CircularProgressIndicator()
                CreditsState.Failed -> Text(stringResource(R.string.credits_failed))
                CreditsState.None -> Text(stringResource(R.string.credits_none))
                is CreditsState.Found -> current.credits.credits
                    .groupBy { it.role }
                    .forEach { (role, people) -> CreditGroup(role, people) }
            }
            Text(
                stringResource(R.string.credits_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CreditGroup(role: CreditRole, people: List<Credit>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(roleLabel(role)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        people.forEach { credit ->
            Text(if (credit.detail != null) "${credit.name} (${credit.detail})" else credit.name)
        }
    }
}

private fun roleLabel(role: CreditRole): Int = when (role) {
    CreditRole.PERFORMER -> R.string.credit_performer
    CreditRole.VOCALS -> R.string.credit_vocals
    CreditRole.INSTRUMENT -> R.string.credit_instrument
    CreditRole.COMPOSER -> R.string.credit_composer
    CreditRole.LYRICIST -> R.string.credit_lyricist
    CreditRole.WRITER -> R.string.credit_writer
    CreditRole.ARRANGER -> R.string.credit_arranger
    CreditRole.PRODUCER -> R.string.credit_producer
    CreditRole.MIX -> R.string.credit_mix
    CreditRole.ENGINEER -> R.string.credit_engineer
    CreditRole.RECORDING -> R.string.credit_recording
    CreditRole.MASTERING -> R.string.credit_mastering
}

private fun parseDurationMs(text: String): Long? {
    val parts = text.trim().split(":").map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null
    return parts.fold(0L) { total, part -> total * 60 + part } * 1000
}
