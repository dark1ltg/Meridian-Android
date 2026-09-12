package io.github.dark1ltg.meridian.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.R
import io.github.dark1ltg.meridian.data.MeridianSettings
import io.github.dark1ltg.meridian.data.SettingsStore

@Composable
fun SettingsScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by store.settings.collectAsState()
    val scroll = rememberScrollState()
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) store.setLibraryFolder(uri)
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MeridianTheme.colors.bg)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Text(
            text = stringResource(R.string.settings_library),
            color = MeridianTheme.colors.text,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
        )
        Text(
            text = settings.libraryFolderUri ?: stringResource(R.string.settings_folder_none),
            color = MeridianTheme.colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        AccentButton(
            text = stringResource(R.string.settings_choose_folder),
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.padding(top = 12.dp),
        )
        SettingsToggle(
            title = stringResource(R.string.settings_auto_scan),
            checked = settings.autoScanOnLaunch,
            onCheckedChange = { store.setAutoScanOnLaunch(it) },
        )
        SettingsToggle(
            title = stringResource(R.string.settings_wifi_only),
            checked = settings.wifiOnlyAnalysis,
            onCheckedChange = { store.setWifiOnlyAnalysis(it) },
        )
        SettingsToggle(
            title = stringResource(R.string.settings_haptics),
            checked = settings.hapticsEnabled,
            onCheckedChange = { store.setHapticsEnabled(it) },
        )
        SettingsSlider(
            title = stringResource(R.string.settings_crossfade),
            value = settings.crossfadeMs.toFloat(),
            range = 0f..8000f,
            valueLabel = stringResource(R.string.settings_ms, settings.crossfadeMs),
            onChange = { store.setCrossfadeMs(it.toInt()) },
        )
        SettingsSlider(
            title = stringResource(R.string.settings_gapless),
            value = settings.gaplessMs.toFloat(),
            range = 0f..80f,
            valueLabel = stringResource(R.string.settings_ms, settings.gaplessMs),
            onChange = { store.setGaplessMs(it.toInt()) },
        )
        SettingsSlider(
            title = stringResource(R.string.settings_silence),
            value = settings.silenceThresholdDb,
            range = -80f..-20f,
            valueLabel = stringResource(R.string.settings_db, settings.silenceThresholdDb.toInt()),
            onChange = store::setSilenceThresholdDb,
        )
        SettingsSlider(
            title = stringResource(R.string.settings_target_lufs),
            value = settings.targetLufs,
            range = -20f..-8f,
            valueLabel = stringResource(R.string.settings_lufs, settings.targetLufs.toInt()),
            onChange = store::setTargetLufs,
        )
        SettingsSlider(
            title = stringResource(R.string.settings_replay_gain),
            value = settings.replayGainDb,
            range = -12f..12f,
            valueLabel = stringResource(R.string.settings_db, settings.replayGainDb.toInt()),
            onChange = store::setReplayGainDb,
        )
        SettingsToggle(
            title = stringResource(R.string.settings_true_peak),
            checked = settings.truePeakLimiter,
            onCheckedChange = store::setTruePeakLimiter,
        )
        SettingsToggle(
            title = stringResource(R.string.settings_exclude_shorts),
            checked = settings.excludeShorts,
            onCheckedChange = store::setExcludeShorts,
        )
        SettingsSlider(
            title = stringResource(R.string.settings_short_seconds),
            value = settings.shortTrackSeconds.toFloat(),
            range = 10f..180f,
            valueLabel = stringResource(R.string.settings_seconds, settings.shortTrackSeconds),
            onChange = { store.setShortTrackSeconds(it.toInt()) },
        )
        SettingsSlider(
            title = stringResource(R.string.settings_skip_penalty),
            value = settings.skipPenalty,
            range = 0f..2f,
            valueLabel = "${"%.1f".format(settings.skipPenalty)}",
            onChange = store::setSkipPenalty,
        )
        SettingsToggle(
            title = stringResource(R.string.settings_reduce_motion),
            checked = settings.reduceMotion,
            onCheckedChange = store::setReduceMotion,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MeridianTheme.colors.panel)
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = MeridianTheme.colors.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MeridianTheme.colors.text,
                    checkedTrackColor = MeridianTheme.colors.accent,
                    uncheckedThumbColor = MeridianTheme.colors.muted,
                    uncheckedTrackColor = MeridianTheme.colors.line,
                ),
        )
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MeridianTheme.colors.panel)
                .border(1.dp, MeridianTheme.colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, color = MeridianTheme.colors.text)
            Text(text = valueLabel, color = MeridianTheme.colors.muted, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors =
                SliderDefaults.colors(
                    thumbColor = MeridianTheme.colors.accent,
                    activeTrackColor = MeridianTheme.colors.accent,
                    inactiveTrackColor = MeridianTheme.colors.line,
                ),
        )
    }
}
