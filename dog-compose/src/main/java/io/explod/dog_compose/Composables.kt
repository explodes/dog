@file:JvmName("ComposablesKt")

package io.explod.dog_compose

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.explod.dog.Dog
import io.explod.dog.conn.Connection
import io.explod.dog.protocol.DeviceType
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.CoroutinePackage
import io.explod.dog_compose.Device.Status
import io.explod.loggly.Logger
import io.explod.loggly.NullLogger
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import io.explod.dog_compose.ClientViewModelImpl.Companion.Factory as ClientViewModelFactory
import io.explod.dog_compose.ServerViewModelImpl.Companion.Factory as ServerViewModelFactory

@Composable
fun ClientPanel(
    modifier: Modifier = Modifier,
    onCancelled: () -> Unit,
    onSelection: Selection,
    dog: Dog,
    io: CoroutinePackage,
    logger: Logger,
    userInfo: UserInfo,
    /** When true, advances as quickly as possible unless bonding is required. */
    eagerMode: Boolean = true,
) {
    ClientSearchCard(
        modifier = modifier,
        dog = dog,
        io = io,
        logger = logger,
        userInfo = userInfo,
        onCancelled = onCancelled,
        onSelection = onSelection,
        eagerMode = eagerMode,
    )
}

@Composable
fun ServerPanel(
    modifier: Modifier = Modifier,
    onCancelled: () -> Unit,
    onSelection: Selection,
    dog: Dog,
    io: CoroutinePackage,
    logger: Logger,
    userInfo: UserInfo,
) {
    ServerSearchCard(
        modifier = modifier,
        dog = dog,
        io = io,
        logger = logger,
        userInfo = userInfo,
        onCancelled = onCancelled,
        onSelection = onSelection,
    )
}

@Composable
private fun ServerSearchCard(
    modifier: Modifier = Modifier,
    onCancelled: () -> Unit,
    onSelection: Selection,
    dog: Dog,
    io: CoroutinePackage,
    logger: Logger,
    userInfo: UserInfo,
    viewModel: ServerViewModelImpl =
        viewModel(
            factory =
                ServerViewModelFactory(dog = dog, io = io, logger = logger, userInfo = userInfo)
        ),
) {
    ServerSearchCardInternal(
        modifier = modifier,
        viewModel = viewModel,
        onCancelled = onCancelled,
        onSelection = onSelection,
        logger = logger,
    )
}

@Composable
private fun ServerSearchCardInternal(
    modifier: Modifier = Modifier,
    viewModel: ServerViewModelImpl,
    onCancelled: () -> Unit,
    onSelection: Selection,
    logger: Logger,
) {
    val uiState by viewModel.uiState.collectAsState()
    val devices = uiState.value.devices
    DeviceSearchCard(
        modifier = modifier,
        devices = devices,
        onCancelled = {
            viewModel.stopSearch()
            onCancelled.invoke()
        },
        onSelection = onSelection,
        logger = logger,
    )
    LaunchedEffect(key1 = viewModel) { viewModel.startSearch() }
}

@Composable
private fun ClientSearchCard(
    modifier: Modifier = Modifier,
    onCancelled: () -> Unit,
    onSelection: Selection,
    dog: Dog,
    io: CoroutinePackage,
    logger: Logger,
    userInfo: UserInfo,
    eagerMode: Boolean,
    viewModel: ClientViewModelImpl =
        viewModel(
            factory =
                ClientViewModelFactory(
                    dog = dog,
                    io = io,
                    logger = logger,
                    userInfo = userInfo,
                    eagerMode = eagerMode,
                )
        ),
) {
    ClientSearchCardInternal(
        modifier = modifier,
        viewModel = viewModel,
        onCancelled = onCancelled,
        onSelection = onSelection,
        logger = logger,
    )
}

@Composable
private fun ClientSearchCardInternal(
    modifier: Modifier = Modifier,
    viewModel: ClientViewModel,
    onCancelled: () -> Unit,
    onSelection: Selection,
    logger: Logger,
) {
    val uiState by viewModel.uiState.collectAsState()
    val devices = uiState.value.devices
    DeviceSearchCard(
        modifier = modifier,
        devices = devices,
        onCancelled = {
            viewModel.stopSearch()
            onCancelled.invoke()
        },
        onSelection = onSelection,
        logger = logger,
    )
    LaunchedEffect(key1 = viewModel) { viewModel.startSearch() }
}

@Composable
private fun DeviceSearchCard(
    modifier: Modifier = Modifier,
    devices: List<Device>,
    onCancelled: () -> Unit,
    onSelection: Selection,
    logger: Logger,
) {
    val connections = devices.filter { it.status == Status.CONNECTED }.map { it.connection }
    val numConnectedDevices = connections.size
    val allowMultipleSelection = onSelection is Selection.MultipleSelection
    Card(modifier = modifier.fillMaxWidth().wrapContentHeight()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tick(
                rate = integerResource(android.R.integer.config_mediumAnimTime).milliseconds,
                modulo = 4,
            ) { frame ->
                val imageResource =
                    when (frame) {
                        0 -> R.drawable.searching_1
                        1 -> R.drawable.searching_2
                        2 -> R.drawable.searching_3
                        3 -> R.drawable.searching_4
                        else -> R.drawable.searching_3 // image with both bars
                    }
                Icon(
                    modifier = Modifier.padding(16.dp).size(24.dp, 24.dp),
                    painter = painterResource(imageResource),
                    contentDescription = stringResource(R.string.dog_searching),
                )
            }
            Text(text = stringResource(R.string.dog_searching), fontSize = 16.sp)
        }
        LazyRow(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            if (devices.isEmpty()) {
                item {
                    DeviceItem(
                        modifier = Modifier.padding(8.dp),
                        device =
                            Device(
                                id = 0L,
                                connection = NullConnection(),
                                status = Status.CONNECTING,
                                name = stringResource(R.string.dog_waiting_for_devices),
                                deviceType = DeviceType.PHONE,
                                onClick = null,
                            ),
                        selectable = false,
                        logger = logger,
                    )
                }
            } else {
                items(devices, key = { it.id }) { device ->
                    DeviceItem(
                        modifier = Modifier.padding(8.dp),
                        device = device,
                        selectable = allowMultipleSelection || numConnectedDevices == 0,
                        logger = logger,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Row(
                modifier = Modifier.wrapContentWidth().padding(16.dp, 0.dp, 16.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.width(142.dp),
                    shape = CircleShape.copy(CornerSize(4.dp)),
                    onClick = onCancelled,
                ) {
                    Text(text = stringResource(R.string.dog_cancel_search))
                }
                Button(
                    modifier = Modifier.width(142.dp),
                    shape = CircleShape.copy(CornerSize(4.dp)),
                    onClick = {
                        when (onSelection) {
                            is Selection.SingleSelection -> {
                                // This button isn't enabled unless there is a connection.
                                onSelection.onSelected(connections.first())
                            }

                            is Selection.MultipleSelection -> onSelection.onSelected(connections)
                        }
                    },
                    enabled = numConnectedDevices > 0,
                ) {
                    Text(
                        text =
                            pluralStringResource(R.plurals.dog_select_devices, numConnectedDevices)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    modifier: Modifier = Modifier,
    device: Device,
    /** False if this device is no longer selectable (trying to select too many). */
    selectable: Boolean,
    logger: Logger,
) {
    val deviceIcon =
        when (device.deviceType) {
            DeviceType.PHONE -> painterResource(R.drawable.phone_24)
            DeviceType.TABLET -> painterResource(R.drawable.tablet_24)
            DeviceType.TV -> painterResource(R.drawable.tv_24)
            DeviceType.CAR -> painterResource(R.drawable.car_24)
            DeviceType.DESKTOP -> painterResource(R.drawable.desktop_24)
            DeviceType.WATCH -> painterResource(R.drawable.watch_24)
        }
    val actionText =
        when (device.status) {
            Status.WAITING_FOR_BOND -> stringResource(R.string.dog_perform_bond)
            Status.WAITING_FOR_ADMIT -> stringResource(R.string.dog_perform_admit)
            Status.WAITING_FOR_JOIN -> stringResource(R.string.dog_perform_join)
            Status.CONNECTING -> stringResource(R.string.dog_state_connecting)
            Status.CONNECTED -> stringResource(R.string.dog_state_connected)
            Status.ERROR -> stringResource(R.string.dog_state_error)
            Status.DISCONNECTED -> stringResource(R.string.dog_state_disconnected)
            Status.REJECTED -> stringResource(R.string.dog_state_rejected)
        }
    val actionColor =
        when (device.status) {
            Status.WAITING_FOR_BOND -> colorResource(R.color.dog_tag_bond)
            Status.WAITING_FOR_ADMIT -> colorResource(R.color.dog_tag_admit)
            Status.WAITING_FOR_JOIN -> colorResource(R.color.dog_tag_join)
            Status.CONNECTING -> colorResource(R.color.dog_tag_connecting)
            Status.CONNECTED -> colorResource(R.color.dog_tag_connected)
            Status.ERROR -> colorResource(R.color.dog_tag_error)
            Status.DISCONNECTED -> colorResource(R.color.dog_tag_disconnected)
            Status.REJECTED -> colorResource(R.color.dog_tag_rejected)
        }
    Card(
        modifier =
            modifier
                .width(116.dp)
                .height(144.dp)
                .then(
                    if (selectable && device.onClick != null) {
                        Modifier.clickable {
                            logger.debug("Clicked device $device")
                            device.onClick.invoke()
                        }
                    } else {
                        Modifier
                    }
                ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(5.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(64.dp)
                        .background(colorResource(R.color.dog_device_circle), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    painter = deviceIcon,
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = device.name,
                minLines = 2,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // TODO: indicate (maybe fade out) if this is not selectable.
            ActionTag(actionText = actionText, actionColor = actionColor)
        }
    }
}

@Composable
private fun ActionTag(modifier: Modifier = Modifier, actionText: String, actionColor: Color) {
    Text(
        modifier =
            Modifier.fillMaxWidth()
                .background(actionColor.copy(alpha = 0.5f))
                .border(
                    2.dp,
                    actionColor,
                    CircleShape.copy(
                        CornerSize(0.dp),
                        CornerSize(0.dp),
                        CornerSize(4.dp),
                        CornerSize(4.dp),
                    ),
                ),
        text = actionText,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
    )
}

internal data class Device(
    val id: Long,
    val connection: Connection,
    val status: Status,
    val name: String,
    val deviceType: DeviceType,
    val onClick: (() -> Unit)?,
) {

    enum class Status {
        WAITING_FOR_BOND,
        WAITING_FOR_ADMIT,
        WAITING_FOR_JOIN,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        REJECTED,
        ERROR,
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "DeviceSearchCardPreviewLight")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "DeviceSearchCardPreviewDark")
private fun DeviceSearchCardPreview() {

    fun fake(
        id: Long,
        status: Status,
        deviceType: DeviceType,
        name: String = "Device $id",
    ): Device {
        return Device(id, NullConnection(), status, name, deviceType, onClick = null)
    }

    PreviewFrame {
        val longName = "Device 3 Has A Really Long Device Name"
        DeviceSearchCard(
            modifier = Modifier,
            devices =
                listOf(
                    fake(1, Status.WAITING_FOR_BOND, DeviceType.TV),
                    fake(2, Status.WAITING_FOR_ADMIT, DeviceType.PHONE),
                    fake(3, Status.WAITING_FOR_JOIN, DeviceType.DESKTOP, name = longName),
                    fake(4, Status.CONNECTING, DeviceType.CAR),
                    fake(5, Status.ERROR, DeviceType.WATCH),
                    fake(6, Status.CONNECTED, DeviceType.DESKTOP),
                ),
            onSelection = Selection.SingleSelection {},
            onCancelled = {},
            logger = NullLogger,
        )
    }
}

@Composable
private fun Tick(
    rate: Duration,
    modulo: Int = Int.MAX_VALUE,
    content: @Composable (tick: Int) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(rate)
            tick = (tick + 1) % modulo
        }
    }
    content(tick)
}
