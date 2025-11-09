# KRegex

Lightweight (little code) regex implementation in Kotlin

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

