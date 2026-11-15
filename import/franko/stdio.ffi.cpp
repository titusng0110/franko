// ============================================================================
// FFI bridge for `import "franko/stdio"`.
// ============================================================================
//
// The bridge owns the 8192-byte stdin read-ahead buffer, the platform
// read call, integer formatting, and byte-view writing. The public
// overload set and the String/width adapters stay in stdio.fr. Every
// block defines the exact mangled name the compiler emits for the
// declaration in stdio.fr.
//
// @@common
#include "FrankoRuntime.hpp"

#ifdef _WIN32
#  ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN
#  endif
#  ifndef NOMINMAX
#    define NOMINMAX
#  endif
#  include <windows.h>
#else
#  include <unistd.h>
#endif

namespace {
struct Franko_Stdio_State {
    uint8_t input[8192];
    uint32_t pos;
    uint32_t len;
};

inline Franko_Stdio_State& stdio_state() {
    static Franko_Stdio_State state;
    return state;
}

inline bool stdio_view(
        Franko_Fat_Pointer<uint8_t> view,
        const uint8_t** data,
        uint32_t* length
) {
    if (view.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array =
                static_cast<Franko_Dynamic_Array<uint8_t>*>(view.data);

        if (array == nullptr || array->data == nullptr) {
            return false;
        }

        *data = array->data;
        *length = array->count;
        return true;
    }

    if (view.mode == 2) {
        *data = static_cast<const uint8_t*>(view.data);
        *length = view.count;
        return true;
    }

    return false;
}

inline uint8_t* stdio_mutable_view(
        Franko_Fat_Pointer<uint8_t> view,
        uint32_t* length
) {
    if (view.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array =
                static_cast<Franko_Dynamic_Array<uint8_t>*>(view.data);

        if (array == nullptr || array->data == nullptr) {
            return nullptr;
        }

        *length = array->count;
        return array->data;
    }

    if (view.mode == 2) {
        *length = view.count;
        return static_cast<uint8_t*>(view.data);
    }

    return nullptr;
}

inline void stdio_write_bytes(
        const uint8_t* buffer,
        uint32_t count,
        bool error_stream
) {
#if defined(_WIN32)
    HANDLE handle = ::GetStdHandle(error_stream
            ? STD_ERROR_HANDLE
            : STD_OUTPUT_HANDLE);

    if (handle == nullptr || handle == INVALID_HANDLE_VALUE) {
        return;
    }

    DWORD written = 0;
    ::WriteFile(handle, buffer, static_cast<DWORD>(count), &written, nullptr);
#else
    const int fd = error_stream ? 2 : 1;
    const ssize_t ignored = ::write(fd, buffer, static_cast<size_t>(count));
    (void)ignored;
#endif
}

inline void stdio_write_view(
        Franko_Fat_Pointer<uint8_t> view,
        bool error_stream
) {
    const uint8_t* buffer;
    uint32_t length;

    if (stdio_view(view, &buffer, &length) && length != 0) {
        stdio_write_bytes(buffer, length, error_stream);
    }
}

inline void stdio_write_number(
        uint8_t* buffer,
        uint32_t length,
        Franko_Fat_Pointer<uint8_t> end,
        bool error_stream
) {
    const uint8_t* end_bytes = nullptr;
    uint32_t end_length = 0;

    if (stdio_view(end, &end_bytes, &end_length)
            && end_length != 0
            && end_length <= 25 - length) {
        std::memcpy(buffer + length, end_bytes, end_length);
        length = length + end_length;
        end_length = 0;
    }

    stdio_write_bytes(buffer, length, error_stream);

    if (end_length != 0) {
        stdio_write_bytes(end_bytes, end_length, error_stream);
    }
}

inline void stdio_write_uint64(
        uint64_t number,
        Franko_Fat_Pointer<uint8_t> end,
        bool error_stream
) {
    uint8_t buffer[25];
    uint32_t length = 0;

    if (number == 0) {
        buffer[length] = 48;
        length = 1;
    } else {
        uint64_t value = number;

        while (value != 0) {
            uint64_t digit = value % 10;
            buffer[length] = static_cast<uint8_t>(digit + 48);
            length = length + 1;
            value = value / 10;
        }
    }

    uint32_t a = 0;
    uint32_t b = length - 1;

    while (a < b) {
        uint8_t tmp = buffer[a];
        buffer[a] = buffer[b];
        buffer[b] = tmp;
        a = a + 1;
        b = b - 1;
    }

    stdio_write_number(buffer, length, end, error_stream);
}

inline void stdio_write_int64(
        int64_t number,
        Franko_Fat_Pointer<uint8_t> end,
        bool error_stream
) {
    if (number >= 0) {
        stdio_write_uint64(static_cast<uint64_t>(number), end, error_stream);
        return;
    }

    uint8_t buffer[25];
    uint32_t length = 0;

    for (int64_t value = number; value != 0; value = value / 10) {
        int64_t remainder = value % 10;
        int64_t digit = 0 - remainder;
        buffer[length] = static_cast<uint8_t>(digit + 48);
        length = length + 1;
    }

    buffer[length] = 45;
    length = length + 1;

    uint32_t a = 0;
    uint32_t b = length - 1;

    while (a < b) {
        uint8_t tmp = buffer[a];
        buffer[a] = buffer[b];
        buffer[b] = tmp;
        a = a + 1;
        b = b - 1;
    }

    stdio_write_number(buffer, length, end, error_stream);
}
}
// @@end

// @@__io_stdout_int64
void FUNC___io_stdout_int64_596fcc5d(
        int64_t PARAM_num,
        Franko_Fat_Pointer<uint8_t> PARAM_end
) {
    stdio_write_int64(PARAM_num, PARAM_end, false);
}
// @@end

// @@__io_stdout_uint64
void FUNC___io_stdout_uint64_c31eaf68(
        uint64_t PARAM_num,
        Franko_Fat_Pointer<uint8_t> PARAM_end
) {
    stdio_write_uint64(PARAM_num, PARAM_end, false);
}
// @@end

// @@__io_stdout_bytes
void FUNC___io_stdout_bytes_f05d0279(
        Franko_Fat_Pointer<uint8_t> PARAM_text,
        Franko_Fat_Pointer<uint8_t> PARAM_end
) {
    stdio_write_view(PARAM_text, false);
    stdio_write_view(PARAM_end, false);
}
// @@end

// @@__io_stderr_int64
void FUNC___io_stderr_int64_596fcc5d(
        int64_t PARAM_num,
        Franko_Fat_Pointer<uint8_t> PARAM_end
) {
    stdio_write_int64(PARAM_num, PARAM_end, true);
}
// @@end

// @@__io_stderr_uint64
void FUNC___io_stderr_uint64_c31eaf68(
        uint64_t PARAM_num,
        Franko_Fat_Pointer<uint8_t> PARAM_end
) {
    stdio_write_uint64(PARAM_num, PARAM_end, true);
}
// @@end

// @@__io_stderr_bytes
void FUNC___io_stderr_bytes_f05d0279(
        Franko_Fat_Pointer<uint8_t> PARAM_text,
        Franko_Fat_Pointer<uint8_t> PARAM_end
) {
    stdio_write_view(PARAM_text, true);
    stdio_write_view(PARAM_end, true);
}
// @@end

// @@__io_fill
int32_t FUNC___io_fill_72623263() {
    if (stdio_state().pos < stdio_state().len) {
        return 1;
    }

    stdio_state().pos = 0;
    stdio_state().len = 0;

#if defined(_WIN32)
    HANDLE handle = ::GetStdHandle(STD_INPUT_HANDLE);

    if (handle == nullptr || handle == INVALID_HANDLE_VALUE) {
        return -1;
    }

    DWORD count = 0;
    BOOL ok = ::ReadFile(
            handle,
            stdio_state().input,
            static_cast<DWORD>(sizeof(stdio_state().input)),
            &count,
            nullptr
    );

    if (ok == 0 && count == 0) {
        return 0;
    }

    stdio_state().len = static_cast<uint32_t>(count);
    return 1;
#else
    int64_t count = ::read(
            0,
            stdio_state().input,
            sizeof(stdio_state().input)
    );

    if (count < 0) {
        return -1;
    }

    if (count == 0) {
        return 0;
    }

    stdio_state().len = static_cast<uint32_t>(count);
    return 1;
#endif
}
// @@end

// @@__io_input_pos
uint32_t FUNC___io_input_pos_16f2b510() {
    return stdio_state().pos;
}
// @@end

// @@__io_input_len
uint32_t FUNC___io_input_len_16f2b510() {
    return stdio_state().len;
}
// @@end

// @@__io_set_input_pos
void FUNC___io_set_input_pos_458f2348(uint32_t PARAM_value) {
    stdio_state().pos = PARAM_value;
}
// @@end

// @@__io_set_input_len
void FUNC___io_set_input_len_458f2348(uint32_t PARAM_value) {
    stdio_state().len = PARAM_value;
}
// @@end

// @@__io_input_byte
uint8_t FUNC___io_input_byte_7b4f7af6(uint32_t PARAM_index) {
    if (PARAM_index >= sizeof(stdio_state().input)) {
        franko_panic("franko/stdio: input byte index out of bounds");
    }

    return stdio_state().input[PARAM_index];
}
// @@end

// @@__io_input_memchr
uint32_t FUNC___io_input_memchr_6d601844(
        uint8_t PARAM_value,
        uint32_t PARAM_start,
        uint32_t PARAM_count
) {
    if (PARAM_start > sizeof(stdio_state().input)
            || PARAM_count > sizeof(stdio_state().input) - PARAM_start) {
        franko_panic("franko/stdio: input search range out of bounds");
    }

    const void* found = std::memchr(
            stdio_state().input + PARAM_start,
            PARAM_value,
            PARAM_count
    );

    if (found == nullptr) {
        return 0xFFFFFFFF;
    }

    return static_cast<uint32_t>(
            static_cast<const uint8_t*>(found) - stdio_state().input
    );
}
// @@end

// @@__io_input_copy_to
void FUNC___io_input_copy_to_bab22282(
        Franko_Fat_Pointer<uint8_t> PARAM_dst,
        uint32_t PARAM_dst_start,
        uint32_t PARAM_src_start,
        uint32_t PARAM_count
) {
    uint32_t dst_length = 0;
    uint8_t* dst = stdio_mutable_view(PARAM_dst, &dst_length);

    if (dst == nullptr) {
        franko_panic("franko/stdio: input copy destination is null");
    }

    if (PARAM_src_start > sizeof(stdio_state().input)
            || PARAM_count > sizeof(stdio_state().input) - PARAM_src_start) {
        franko_panic("franko/stdio: input copy source range out of bounds");
    }

    if (PARAM_dst_start > dst_length
            || PARAM_count > dst_length - PARAM_dst_start) {
        franko_panic("franko/stdio: input copy destination range out of bounds");
    }

    if (PARAM_count != 0) {
        std::memcpy(
                dst + PARAM_dst_start,
                stdio_state().input + PARAM_src_start,
                PARAM_count
        );
    }
}
// @@end
