# franko/time

Wall-clock and monotonic time for Franko programs. The module is a thin
wrapper over the platform clock primitives exposed by
[`franko/os`](os.md):

- Linux/macOS wall clock: `clock_gettime(CLOCK_REALTIME)`;
- Linux/macOS monotonic clock: `clock_gettime(CLOCK_MONOTONIC)`;
- Windows wall clock: `GetSystemTimePreciseAsFileTime`;
- Windows monotonic clock: `QueryPerformanceCounter` +
  `QueryPerformanceFrequency`.

```franko
import "franko/time"
```

Module files:

```text
import/franko/time.fr    // Franko implementation (on top of franko/os)
```

## API

```franko
func std::time() -> (int64 sec, int64 nanosec)
func std::mono_time() -> int64
```

`std::time()` returns the current Unix timestamp as a pair of whole seconds
and nanoseconds. `nanosec` is always in the range `0` to `999999999`.

```franko
import "franko/stdio"
import "franko/time"

func main() -> int32 {
    (int64 sec, int64 nanosec) now = std::time()
    stdout(now.sec)
    stdout(now.nanosec)
    return 0
}
```

`std::mono_time()` returns a monotonic clock reading in nanoseconds. The
absolute value is platform-defined and not a Unix timestamp; only differences
between two readings are meaningful. It is the right clock for measuring
elapsed time because it is unaffected by wall-clock adjustments:

```franko
int64 start = std::mono_time()
// ... work ...
int64 elapsed_ns = std::mono_time() - start
```

On error, `std::time()` returns `(0, 0)` and `std::mono_time()` returns `0`.

## Platform mapping

| Clock | Linux/macOS | Windows |
|---|---|---|
| Wall clock | `clock_gettime(CLOCK_REALTIME, ...)` | `GetSystemTimePreciseAsFileTime()` converted from 100-nanosecond intervals since 1601-01-01 UTC |
| Monotonic | `clock_gettime(CLOCK_MONOTONIC, ...)` | `QueryPerformanceCounter()` scaled by `QueryPerformanceFrequency()` |

The raw Windows performance counters are also available directly from
[`franko/os`](os.md#oswin32queryperformancecounter).

## Tests

```text
library_tests/time/free_functions/  // std::time and std::mono_time range checks
```
