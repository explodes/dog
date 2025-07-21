package io.explod.dog.conn

/** Interface for when a new link is received or updated. */
interface LinkListener {
    /** Called when a link is changed, or its identity. */
    fun onLinkChanged(connection: LinkedConnection, link: Link)

    /**
     * Called when a link's identities are changed. This not called when a Link advances, which will
     * trigger an onLinkChanged instead.
     */
    fun onLinkIdentityChanged(connection: LinkedConnection, link: Link)
}
