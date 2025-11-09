# KRegex

Lightweight (little code) regex implementation in Kotlin.

This project is mainly for myself for Porting my Kotlin-based game engine to WASM without needing tons of 
space for just Regex.
If you have a JVM or JS engine, use their Regex implementations instead. They should be much faster.

## Supports:
- Characters ofc
- ^$
- (|)
- [] for ranges
- +?*
- \w\W, \d\D, \s\S
- {min,max}, {min}, {min,}, but be careful, large numbers will create tons of objects

## Issues:
- Not allocation optimized yet, so any request potentially takes up lots of dynamic memory.
- forEachMatch() is not minimal regarding the start; use forEachNonOverlappingMatch for now, or use your own de-duplication logic
- {min,max} with large max-values will duplicate lots and lots of nodes
- There are pretty much no optimizations, so parsing is 50x slower than startsWith() / contains().

## Dependencies
- Kotlin standard library
- Java 8 or later (no Java specific stuff is used, so this should work for JS, too, but their native implementation is to be preferred)

## Performance
On a simple (start|end)-pattern, and contains-request,
- contains("start")||contains("end") is 6x faster than JVM-Regex,
- and JVM-Regex is 6x faster than this library.

(5.4 ms vs 34 ms vs 183 ms)