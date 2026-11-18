// ============================================================================
// FFI bridge for `import "franko/error"`.
// ============================================================================
//
// `std::panic(reason)` copies the reason bytes into a fixed 512-byte
// NUL-terminated buffer and calls franko_panic. A Franko byte view is
// not NUL-terminated, so the copy is required. Long reasons are
// truncated to 511 bytes.
//
// @@common
#include "FrankoRuntime.hpp"
// @@end

// @@std::panic
namespace NS_std {
void FUNC_panic_fc5b5574(Franko_Fat_Pointer<uint8_t> PARAM_reason)
{
    const uint8_t* buffer = PARAM_reason.data;
    uint32_t length = PARAM_reason.count;

    if (buffer == nullptr) {
        franko_panic("std::panic: null reason");
    }

    char message[512];
    uint32_t copy = length;
    if (copy >= sizeof(message)) {
        copy = static_cast<uint32_t>(sizeof(message) - 1);
    }
    if (copy != 0) {
        std::memcpy(message, buffer, static_cast<size_t>(copy));
    }
    message[copy] = '\0';
    franko_panic(message);
}
}
// @@end
