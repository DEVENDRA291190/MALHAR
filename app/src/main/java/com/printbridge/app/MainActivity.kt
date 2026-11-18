package com.printbridge.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                startActivity(
                    Intent(this, PrintActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/pdf")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) {
            permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
        setContent {
            PrintBridgeApp(
                vm,
                onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onPrintSettings = { startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun IntField(value: Int, label: String, modifier: Modifier = Modifier, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val f = t.filter(Char::isDigit).take(4)
            text = f
            f.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ValueSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    steps: Int = max - min - 1,
    onValue: (Int) -> Unit,
) {
    Text(label)
    Slider(
        value = value.toFloat(),
        onValueChange = { onValue(it.toInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = steps,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintBridgeApp(vm: MainViewModel, onPickPdf: () -> Unit, onPrintSettings: () -> Unit) {
    val st by vm.ui.collectAsStateWithLifecycle()
    val s = st.settings

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { pad ->
            Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- Printer
                Section(stringResource(R.string.printer)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.transport == Transport.USB,
                            onClick = { vm.setTransport(Transport.USB) },
                            label = { Text(stringResource(R.string.usb)) })
                        FilterChip(
                            selected = s.transport == Transport.BLUETOOTH,
                            onClick = { vm.setTransport(Transport.BLUETOOTH) },
                            label = { Text(stringResource(R.string.bluetooth)) })
                    }
                    if (st.printers.isEmpty()) Text(stringResource(R.string.no_printers))
                    st.printers.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.select(p) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.printerId == p.id, onClick = { vm.select(p) })
                            Text(p.name)
                        }
                    }
                    OutlinedButton(onClick = vm::refresh) { Text(stringResource(R.string.refresh)) }
                }

                // ---- Paper type
                Section(stringResource(R.string.paper_type)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.paper == PaperType.RECEIPT,
                            onClick = { vm.update { it.copy(paper = PaperType.RECEIPT) } },
                            label = { Text(stringResource(R.string.paper_receipt)) })
                        FilterChip(
                            selected = s.paper == PaperType.GAP,
                            onClick = { vm.update { it.copy(paper = PaperType.GAP) } },
                            label = { Text(stringResource(R.string.paper_gap)) })
                        FilterChip(
                            selected = s.paper == PaperType.BLACK_MARK,
                            onClick = { vm.update { it.copy(paper = PaperType.BLACK_MARK) } },
                            label = { Text(stringResource(R.string.paper_mark)) })
                    }
                    Text(
                        stringResource(
                            when (s.paper) {
                                PaperType.RECEIPT -> R.string.paper_receipt_desc
                                PaperType.GAP -> R.string.paper_gap_desc
                                PaperType.BLACK_MARK -> R.string.paper_mark_desc
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // ---- Roll size
                Section(stringResource(R.string.roll_size)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.widthMm == 75 && s.heightMm == 75,
                            onClick = { vm.update { it.copy(widthMm = 75, heightMm = 75, pageMm = 75) } },
                            label = { Text(stringResource(R.string.preset_75)) })
                        FilterChip(
                            selected = s.widthMm == 80 && s.heightMm == 160,
                            onClick = { vm.update { it.copy(widthMm = 80, heightMm = 160, pageMm = 160) } },
                            label = { Text(stringResource(R.string.preset_80)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntField(s.widthMm, stringResource(R.string.width_mm), Modifier.weight(1f)) { v ->
                            vm.update { it.copy(widthMm = v) }
                        }
                        if (s.paper != PaperType.RECEIPT) {
                            IntField(s.heightMm, stringResource(R.string.height_mm), Modifier.weight(1f)) { v ->
                                vm.update { it.copy(heightMm = v) }
                            }
                        }
                    }
                    Text(stringResource(R.string.max_width_note), style = MaterialTheme.typography.bodySmall)
                    if (s.paper == PaperType.GAP) {
                        IntField(s.gapMm, stringResource(R.string.gap_mm), Modifier.fillMaxWidth()) { v ->
                            vm.update { it.copy(gapMm = v) }
                        }
                    }
                    if (s.paper == PaperType.BLACK_MARK) {
                        IntField(s.markMm, stringResource(R.string.mark_mm), Modifier.fillMaxWidth()) { v ->
                            vm.update { it.copy(markMm = v) }
                        }
                    }
                    IntField(s.pageMm, stringResource(R.string.page_mm), Modifier.fillMaxWidth()) { v ->
                        vm.update { it.copy(pageMm = v) }
                    }
                    Text(stringResource(R.string.page_mm_note), style = MaterialTheme.typography.bodySmall)
                }

                // ---- Print quality
                Section(stringResource(R.string.quality)) {
                    ValueSlider(stringResource(R.string.darkness, s.density), s.density, 1, 15) { v ->
                        vm.update { it.copy(density = v) }
                    }
                    ValueSlider(stringResource(R.string.speed, s.speed), s.speed, 1, 5) { v ->
                        vm.update { it.copy(speed = v) }
                    }
                    ValueSlider(stringResource(R.string.threshold, s.threshold), s.threshold, 32, 224, steps = 0) { v ->
                        vm.update { it.copy(threshold = v) }
                    }
                    SwitchRow(stringResource(R.string.dither), s.dither) { v ->
                        vm.update { it.copy(dither = v) }
                    }
                    SwitchRow(stringResource(R.string.invert), s.invertBits) { v ->
                        vm.update { it.copy(invertBits = v) }
                    }
                    SwitchRow(stringResource(R.string.rotate180), s.rotate180) { v ->
                        vm.update { it.copy(rotate180 = v) }
                    }
                    IntField(s.offsetXDots, stringResource(R.string.offset_x), Modifier.fillMaxWidth()) { v ->
                        vm.update { it.copy(offsetXDots = v) }
                    }
                }

                // ---- Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::testPrint) { Text(stringResource(R.string.test_print)) }
                    OutlinedButton(onClick = vm::calibrate) { Text(stringResource(R.string.calibrate)) }
                }
                Button(onClick = onPickPdf, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.print_pdf))
                }
                OutlinedButton(onClick = onPrintSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enable_service))
                }
                if (st.status.isNotEmpty()) Text(st.status)
            }
        }
    }
}
