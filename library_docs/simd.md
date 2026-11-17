# franko/simd

Platform-specific byte-array SIMD operations backed by C++ intrinsics.

```franko
import "franko/simd"
```

Module files:

```text
import/franko/simd.fr
import/franko/simd.module.json
import/franko/simd.ffi.cpp
```

The `.fr` surface is selected by `#if (#IS_X86_64)` / `#if (#IS_ARM64)`.
All functions are `#inline`; bounds are checked in the bridge unless
`-Omax` defines `FRANKO_BOUNDS_UNCHECKED`, which removes them like the
array runtime checks. Buffer operations process 16-byte chunks and a scalar tail. Non-crypto operations fall back to scalar loops
when the required instruction set is unavailable.

## x86_64

```franko
simd::x86::xor_si128(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
simd::x86::and_si128(...) -> void
simd::x86::or_si128(...) -> void
simd::x86::andnot_si128(...) -> void
simd::x86::adds_epu8(...) -> void
simd::x86::subs_epu8(...) -> void
simd::x86::max_epu8(...) -> void
simd::x86::min_epu8(...) -> void
simd::x86::shuffle_epi8(mview<[byte]> target, view<[byte]> indices, uint32 dst_start, uint32 src_start, uint32 count) -> void
simd::x86::aesenc_si128(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
simd::x86::aesdec_si128(...) -> int32
```

## ARM64

```franko
simd::arm::veorq_u8(mview<[byte]> target, view<[byte]> source, uint32 dst_start, uint32 src_start, uint32 count) -> void
simd::arm::vandq_u8(...) -> void
simd::arm::vorrq_u8(...) -> void
simd::arm::vbicq_u8(...) -> void
simd::arm::vqaddq_u8(...) -> void
simd::arm::vqsubq_u8(...) -> void
simd::arm::vmaxq_u8(...) -> void
simd::arm::vminq_u8(...) -> void
simd::arm::vqtbl1q_u8(mview<[byte]> target, view<[byte]> indices, uint32 dst_start, uint32 src_start, uint32 count) -> void
simd::arm::vaeseq_u8(mview<[byte]> target, view<[byte]> round_key, uint32 dst_start, uint32 src_start, uint32 count) -> int32
simd::arm::vaesdq_u8(...) -> int32
```

The four AES functions return `0` on success and a nonzero error code when
the corresponding AES hardware extension is unavailable. Bounds violations
still abort. All other operations have scalar fallbacks.

## Example

```franko
import "franko/stdio"
import "franko/simd"

func main() -> int32 {
    [byte, 16] a
    [byte, 16] b
    uint32 i = 0
    while (i < 16) {
        a[i] = unsafe_cast<byte>(i + 1)
        b[i] = unsafe_cast<byte>(i + 1)
        i = i + 1
    }

    #if (#IS_ARM64) {
        simd::arm::veorq_u8(mview(b), view(a), 0, 0, 16)
    } #else #if (#IS_X86_64) {
        simd::x86::xor_si128(mview(b), view(a), 0, 0, 16)
    }
    stdout(b[0])   // 0
    return 0
}
```
