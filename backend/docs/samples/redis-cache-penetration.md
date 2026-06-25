# Redis Cache Penetration

## Problem

When many requests query a key that does not exist, the request may bypass Redis and hit MySQL repeatedly.

## Root Cause

The system only caches existing data. Missing data is not cached, so every request becomes a cache miss.

## Solutions

- Cache empty values with a short TTL.
- Validate illegal parameters early.
- Add rate limiting for abnormal traffic.
- Monitor cache miss rate and database pressure.

## Interview Talking Point

Cache penetration is different from cache breakdown and cache avalanche. The key idea is to protect the database from repeated misses for non-existing data.
