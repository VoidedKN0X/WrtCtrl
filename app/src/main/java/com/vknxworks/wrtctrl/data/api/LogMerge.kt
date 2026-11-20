package com.vknxworks.wrtctrl.data.api

/**
 * Joins the log lines already shown ([previous], oldest first) with a freshly read [tail] (the newest
 * lines, oldest first), so a refresh only has to download the tail. Returns at most the newest [max]
 * lines, [previous] itself when the tail holds nothing new, or null when the two can't be joined
 * safely, in which case the caller reads the whole log again.
 *
 * logd numbers every line (`id`, counting up by one). The join is only made when both lists carry
 * increasing ids, the tail starts inside [previous] and every line they share is the same line (same
 * id, time, priority and text). That rules out a gap (more new lines than the tail holds) and a
 * router or logd restart (the ids start again), so no line is shown twice or left out.
 */
internal fun mergeLogTail(previous: List<LogLine>, tail: List<LogLine>, max: Int): List<LogLine>? {
    if (previous.isEmpty() || tail.isEmpty() || max <= 0) return null
    if (!hasIncreasingIds(previous) || !hasIncreasingIds(tail)) return null
    val firstId = tail.first().id
    if (tail.last().id < previous.last().id) return null
    val start = previous.binarySearch { it.id.compareTo(firstId) }
    // Not found: the tail starts after the last known line (lines in between were missed), or before the first.
    if (start < 0) return null
    var i = start
    var j = 0
    while (i < previous.size && j < tail.size) {
        if (previous[i] != tail[j]) return null
        i++
        j++
    }
    if (i < previous.size) return null
    if (j == tail.size) return previous
    val merged = ArrayList<LogLine>(previous.size + tail.size - j)
    merged.addAll(previous)
    merged.addAll(tail.subList(j, tail.size))
    return if (merged.size > max) ArrayList(merged.subList(merged.size - max, merged.size)) else merged
}

private fun hasIncreasingIds(lines: List<LogLine>): Boolean {
    var last = -1L
    for (line in lines) {
        if (line.id <= last) return false
        last = line.id
    }
    return true
}
