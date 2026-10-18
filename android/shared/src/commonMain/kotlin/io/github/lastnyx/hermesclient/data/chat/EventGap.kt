package io.github.lastnyx.hermesclient.data.chat

/** True when [seq] skips past the next expected number, so at least one event never arrived. */
fun seqGap(lastSeen: Long?, seq: Long?): Boolean = lastSeen != null && seq != null && seq > lastSeen + 1
