package com.vknxworks.wrtctrl.data.api

/**
 * Asked before every request that could change something on a router (any ubus call that writes), after the
 * read-only check. The app lock sets [check] to ask for the fingerprint or screen lock when "Before
 * changes" is on; it returns false when the user cancels, and the request fails with
 * [RouterError.Cancelled] without being sent. By default everything is allowed.
 */
object WriteGuard {
    @Volatile
    var check: suspend () -> Boolean = { true }
}
