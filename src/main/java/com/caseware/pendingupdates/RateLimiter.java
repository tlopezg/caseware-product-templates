package com.caseware.pendingupdates;

public interface RateLimiter {
    boolean tryAcquire();
}