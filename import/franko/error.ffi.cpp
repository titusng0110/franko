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
    const uint8_t* buffer;
    uint32_t length;

    if (PARAM_reason.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array =
                static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_reason.data);
        if (array == nullptr || array->data == nullptr) {
            franko_panic("std::panic: null reason");
        }
        buffer = array->data;
        length = array->count;
    } else if (PARAM_reason.mode == 2) {
        buffer = static_cast<const uint8_t*>(PARAM_reason.data);
        length = PARAM_reason.count;
    } else {
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
