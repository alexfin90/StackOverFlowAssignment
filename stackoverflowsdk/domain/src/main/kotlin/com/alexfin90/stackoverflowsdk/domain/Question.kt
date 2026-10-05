package com.alexfin90.stackoverflowsdk.domain

/**
 * A StackOverflow question as seen by SDK consumers.
 *
 * Deliberately independent from the network DTOs: the wire format can change
 * without affecting this type.
 */
public class Question(
    public val id: Long,
    /** Plain text title, HTML entities already decoded. */
    public val title: String,
    /** Absolute URL of the question on stackoverflow.com. */
    public val link: String,
    /** Creation time in seconds since the Unix epoch (UTC). */
    public val creationDateEpochSeconds: Long,
    public val answerCount: Int,
    public val score: Int,
    public val ownerName: String?,
    public val tags: List<String>,
) {
    override fun equals(other: Any?): Boolean = other is Question && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "Question(id=$id, title=$title)"
}
