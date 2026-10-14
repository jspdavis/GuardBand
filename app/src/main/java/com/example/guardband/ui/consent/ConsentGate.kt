package com.example.guardband.ui.consent

import com.example.guardband.data.model.ConsentRecord

/**
 * Whether a signed-in account must agree before it may use the app. Pure, so
 * it is a plain JVM test.
 *
 * **Only a missing record triggers it.** Somebody who agreed to `1.0` keeps
 * `1.0` and is let in; comparing versions belongs to a re-consent flow, which
 * does not exist yet (`docs/privacy-and-consent.md` §2). That flow would add
 * its rule here, so the gate around it does not have to change.
 *
 * Takes the record, never a failed read: the caller handles "could not tell"
 * separately, because treating it as "no record" would put everybody on a bad
 * network in front of the consent screen.
 */
object ConsentGate {

    fun needsConsent(record: ConsentRecord?): Boolean = record == null
}
