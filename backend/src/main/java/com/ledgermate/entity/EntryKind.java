package com.ledgermate.entity;

/** How a ledger entry came to exist. Posted entries are never deleted or overwritten. */
public enum EntryKind {
    /** Entered directly by the user. */
    NORMAL,
    /** Offsets an earlier entry (opposite type, same amount). */
    REVERSAL,
    /** Replacement entry created together with a reversal of the incorrect original. */
    CORRECTION
}
