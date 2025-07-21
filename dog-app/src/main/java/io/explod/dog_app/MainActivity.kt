package io.explod.dog_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.explod.dog.Dog
import io.explod.dog.conn.Connection
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.CoroutinePackage
import io.explod.dog_app.ui.theme.ConnectionTestingTheme
import io.explod.dog_app.util.StateLogger
import io.explod.dog_compose.ClientPanel
import io.explod.dog_compose.Selection
import io.explod.dog_compose.ServerPanel
import io.explod.loggly.Logger
import io.explod.loggly.MultiLogger
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private lateinit var stateLogger: StateLogger
    private lateinit var logger: Logger
    private lateinit var io: CoroutinePackage
    private lateinit var dog: Dog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stateLogger =
            StateLogger("DogApp").apply {
                val message = "Hello, Dog!"
                repeat(3) {
                    this.never(message)
                    this.error(message)
                    this.warn(message)
                    this.info(message)
                    this.debug(message)
                }
            }
        logger = MultiLogger.create("DogApp", Logger.create("DogApp"), stateLogger)
        io = CoroutinePackage.create(Dispatchers.IO)
        dog =
            Dog(
                context = this,
                serviceInfo = SERVICE_INFO,
                logger = logger,
                ioDispatcher = io.dispatcher,
            )

        enableEdgeToEdge()
        setContent {
            ConnectionTestingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current
                    var permissionsGranted by remember {
                        mutableStateOf(checkAllPermissions(context, bluetoothPermissions()))
                    }
                    key(permissionsGranted) {
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            val logs by remember { mutableStateOf(stateLogger.logs) }
                            LogsUi(logs.value.logs)

                            if (permissionsGranted) {
                                var window by remember { mutableStateOf(Window.SELECTING) }
                                when (window) {
                                    Window.SELECTING -> {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Text("Pick a mode:")
                                            Button(
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = { window = Window.SERVER },
                                            ) {
                                                Text("Server")
                                            }
                                            Button(
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = { window = Window.CLIENT },
                                            ) {
                                                Text("Client")
                                            }
                                        }
                                    }

                                    Window.SERVER -> {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Text("Server")
                                            ServerPanel(
                                                dog = dog,
                                                io = io,
                                                logger = logger,
                                                userInfo = SERVER_USER_INFO,
                                                onCancelled = { window = Window.SELECTING },
                                                onSelection =
                                                    Selection.MultipleSelection { connections ->
                                                        onServer(connections)
                                                    },
                                            )
                                        }
                                    }

                                    Window.CLIENT -> {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Text("Client")
                                            ClientPanel(
                                                dog = dog,
                                                io = io,
                                                logger = logger,
                                                userInfo = CLIENT_USER_INFO,
                                                onCancelled = { window = Window.SELECTING },
                                                onSelection =
                                                    Selection.SingleSelection { connection ->
                                                        onClient(connection)
                                                    },
                                                eagerMode = true,
                                            )
                                        }
                                    }
                                }
                            } else {
                                PermissionsUi(modifier = Modifier.padding(innerPadding), logger) {
                                    permissionsGranted = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onClient(server: Connection) {}

    private fun onServer(clients: List<Connection>) {}

    enum class Window {
        SELECTING,
        SERVER,
        CLIENT,
    }

    companion object {

        val SERVICE_INFO =
            ServiceInfo.create(
                friendlyName = "Dog App",
                systemName = "dogapp",
                uuidString = "8a0bc526-15a0-47ab-accd-2ad8f58d037",
            )

        val CLIENT_USER_INFO = UserInfo(appBytes = null, userName = "Evan Client")
        val SERVER_USER_INFO = UserInfo(appBytes = null, userName = "Evan Server")
    }
}
