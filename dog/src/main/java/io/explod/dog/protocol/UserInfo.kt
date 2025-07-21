package io.explod.dog.protocol

import io.explod.dog.util.ImmutableBytes

/** User-specific connection information. */
data class UserInfo(
    /**
     * A user name picked by the user. This will override the device name when this data becomes
     * part of the available Identity.
     */
    val userName: String? = null,

    /**
     * Application specific data. Serialize and deserialize anything you wish here.
     *
     * It is part of the Identity process. Any other kind of data transmissions should done through
     * a connected Connection.
     */
    val appBytes: ImmutableBytes? = null,
)
