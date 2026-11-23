package id.whitesecurity.antivirus.scanner

import id.whitesecurity.antivirus.util.Severity

/** Shared score-to-severity thresholds, used by every heuristic signal source. */
object HeuristicScoring {
    fun severityFor(score: Int): Severity = when {
        score >= 60 -> Severity.CRITICAL
        score >= 35 -> Severity.HIGH
        score >= 15 -> Severity.MEDIUM
        else -> Severity.LOW
    }
}
