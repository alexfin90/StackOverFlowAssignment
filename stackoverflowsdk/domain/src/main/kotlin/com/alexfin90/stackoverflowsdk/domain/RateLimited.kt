package com.alexfin90.stackoverflowsdk.domain

public class RateLimited (
    public val retryAfterSeconds: Int? = null){
    override fun toString(): String = "RateLimited(retryAfterSeconds=$retryAfterSeconds)"
}