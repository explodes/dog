# DOG

![Dog Logo](https://github.com/explodes/dog/blob/main/assets/dog_small.png?raw=true)

A device-connection-finding library for Android.

| module      | artifact                                      | badges and stuff                                                                                                                                                  |  
|-------------|:----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|  
| dog         | `"com.github.explodes.dog:dog:0.0.3"`         | [![Dog Release](https://jitpack.io/v/com.github.explodes.dog/dog.svg?style=flat-square)](https://jitpack.io/#com.github.explodes.dog/dog)                         |  
| dog-compose | `"com.github.explodes.dog:dog-compose:0.0.3"` | [![Dog Compose Release](https://jitpack.io/v/com.github.explodes.dog/dog-compose.svg?style=flat-square)](https://jitpack.io/#com.github.explodes.dog/dog-compose) |  
| loggly      | `"com.github.explodes.dog:loggly:0.0.3"`      | [![Loggly Release](https://jitpack.io/v/com.github.explodes.dog/loggly.svg?style=flat-square)](https://jitpack.io/#com.github.explodes.dog/loggly)                |  

Dog is a project aimed to answer two questions:

- What does it look like when we do everything we can to find a nearby device?
- Can we make it easy for an app developer to "just get some devices"!? so they can create amazing
  multi-user multi-device experiences?

Turns out, (a) it's really cool, and (b) yes we can.

### Structure

The Dog code is split between the core module (`dog`) and optional UI code that leverages compose (
`dog-compose`.) Additionally, there is a test application, and a logging utility.

#### `dog`

`dog` is the core library. This provides the functionality for device discovery and advertising with
different [technologies](#technologies).

#### `dog-compose`

`dog-compose` is a companion library that provides a device picker UI and returns connections to the
application.

#### `dog-app`

`dog-app` is an app you can use to connect to nearby devices! The apps logs are drawn in the
background for easier debugging.

#### `loggly`

`loggly` is a bad abstraction for logging that was useful initially, but needs to be replaced with a
more robust mechanism.

### Technologies

Currently there are two "technologies" available for device connection. RFComm (BLE)
and [Android NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd).

The idea is to add more capabilities, such as Android Nearby, Cross Device SDK, stuff for UWB,
Chip/Matter, maybe Zigbee? You get the idea.

Now that the proof of concept has been established, the hardcoded technologies should be adapted to
a more plug and play style to allow 3rd party packages to plug in their connection strategies as
separate dependencies.

### Dog

The `dog` module itself includes a pattern that allows for returning control back to the user while
discovery or establishing a connection. This enables user interaction between any step, vital or
not, such as:

- Finding a client or server
- Prompting to begin Bluetooth pairing
- Prompting to join server
- Prompting to admit users into servers

Allowing users to decide if they want to advance a connection enables opportunities for privacy and
trust between nearby devices by preventing unwanted incoming and outgoing connections.

There is a simple protocol between client and server that performs an information exchange with data
such as name, device info, and an app-specific blob.

### LICENSE

MIT License.
