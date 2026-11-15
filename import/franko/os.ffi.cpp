// ============================================================================
// FFI bridge for `import "franko/os"`.
// ============================================================================
//
// This file is NOT imported by the Franko compiler (imports only allow .fr
// files, so a .cpp next to the module is never picked up as a module). It is
// spliced verbatim into the generated C++ program by Cpp14Codegen when the
// program imports "franko/os" and calls one of the os intrinsics.
//
// The module sidecar `os.module.json` lists the bridged variables, structs,
// and functions. A bridge block is named after the Franko declaration:
//
//   - `// @@common` is always spliced first when any bridged entity is used.
//   - `// @@os::posix::read` is the block for `os::posix::read`.
//   - `// @@end` closes the current block.
//
// Every other block is spliced only when that declaration is actually used,
// so merely importing "franko/os" adds zero C++ to the output.
//
// Contract:
//
//   - Every function block defines its function under the exact mangled C++
//     name the backend emits for the Franko declaration in ../os.fr
//     (FUNC_<name>_<8hex>, the FNV-1a hash of the canonical signature
//     key). This is the same trick ProgramTemplateWithRawCArgs.cpp uses to
//     call FUNC_main_b3b049d8. The compiler checks that each used
//     mangled name is present here and fails loudly on drift.
//
//   - Signatures must match the prototypes Cpp14Codegen emits for the
//     declarations in ../os.fr, including the NS_os::NS_posix /
//     NS_os::NS_win32 namespace nesting and the PARAM_-prefixed parameter
//     names. The stub bodies in ../os.fr are never emitted when the bridge
//     is active; the bridge definitions are the only definitions.
//
//   - The bridge is spliced after the program template's runtime
//     `#include` (selected via the `__FRANKO_RUNTIME_HEADER__`
//     placeholder), so Franko_Thin_Pointer, Franko_Fat_Pointer,
//     franko_panic, and the fixed-width integer types are available.
//     Keep every block warning-clean under
//     -Wall -Wextra -Wpedantic -Wshadow.
//
// Platform notes:
//
//   - os::posix::read/write map to ::read/::write and are POSIX-only.
//     os.fr declares the posix namespace only for linux/macos, so this
//     block is never inserted into a Windows build. Fds 0, 1, and 2 are
//     reserved for franko/stdio and abort if passed to read or write.
//   - os::win32::ReadFile/WriteFile are the Win32 File API (fileapi.h,
//     via <windows.h>). os.fr declares the win32 namespace only for
//     Windows, so this block is never inserted into a POSIX build.
//     lpOverlapped is dropped: the bridge always passes NULL
//     (synchronous I/O). The standard input/output/error handles are
//     reserved for franko/stdio and abort if passed to either function.
//   - os::posix::clock_gettime and the os::win32 timing trio are thin
//     wrappers over the platform clock primitives: clock_gettime on
//     POSIX, and QueryPerformanceCounter/QueryPerformanceFrequency plus
//     GetSystemTimePreciseAsFileTime on Windows.
//   - Both FFI families check the array bound first: count > length aborts
//     via franko_panic, so the FFI cannot corrupt memory. A null or
//     uninitialized buffer returns the stub failure value instead of
//     crashing.

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

namespace {
inline bool os_is_standard_handle(uint64_t value) {
    HANDLE handle = reinterpret_cast<HANDLE>(static_cast<uintptr_t>(value));

    if (handle == nullptr || handle == INVALID_HANDLE_VALUE) {
        return false;
    }

    return handle == ::GetStdHandle(STD_INPUT_HANDLE)
            || handle == ::GetStdHandle(STD_OUTPUT_HANDLE)
            || handle == ::GetStdHandle(STD_ERROR_HANDLE);
}
}
#else
#  include <unistd.h>
#  include <time.h>

namespace {
inline bool os_is_standard_fd(int32_t fd) {
    return fd >= 0 && fd <= 2;
}
}
#endif
// @@end

// @@os::posix::read
namespace NS_os {
namespace NS_posix {
int64_t FUNC_read_bee7609e(int32_t PARAM_fd, Franko_Fat_Pointer<uint8_t> PARAM_buf, uint64_t PARAM_count)
{
    if (os_is_standard_fd(PARAM_fd)) {
        franko_panic("os::posix::read: standard streams are reserved for franko/stdio");
    }

    uint8_t* buffer;
    uint32_t length;

    if (PARAM_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data == nullptr) {
            return -1;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->count;
    } else if (PARAM_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(PARAM_buf.data);
        length = PARAM_buf.count;
    } else {
        return -1;
    }

    if (PARAM_count > static_cast<uint64_t>(length)) {
        franko_panic("os::posix::read: count exceeds buffer length");
    }
    return static_cast<int64_t>(::read(PARAM_fd, buffer, static_cast<size_t>(PARAM_count)));
}
}
}
// @@end

// @@os::posix::write
namespace NS_os {
namespace NS_posix {
int64_t FUNC_write_7c35a12f(int32_t PARAM_fd, Franko_Fat_Pointer<uint8_t> PARAM_buf, uint64_t PARAM_count)
{
    if (os_is_standard_fd(PARAM_fd)) {
        franko_panic("os::posix::write: standard streams are reserved for franko/stdio");
    }

    uint8_t* buffer;
    uint32_t length;

    if (PARAM_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data == nullptr) {
            return -1;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->count;
    } else if (PARAM_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(PARAM_buf.data);
        length = PARAM_buf.count;
    } else {
        return -1;
    }

    if (PARAM_count > static_cast<uint64_t>(length)) {
        franko_panic("os::posix::write: count exceeds buffer length");
    }
    return static_cast<int64_t>(::write(PARAM_fd, buffer, static_cast<size_t>(PARAM_count)));
}
}
}
// @@end

// @@os::posix::clock_gettime
namespace NS_os {
namespace NS_posix {
int32_t FUNC_clock_gettime_de6e7c37(int32_t PARAM_clock_id, Franko_Thin_Pointer<int64_t> PARAM_sec, Franko_Thin_Pointer<int64_t> PARAM_nanosec)
{
    struct timespec ts;
    if (::clock_gettime(static_cast<clockid_t>(PARAM_clock_id), &ts) != 0) {
        return -1;
    }
    if (PARAM_sec.data != nullptr) {
        *PARAM_sec.data = static_cast<int64_t>(ts.tv_sec);
    }
    if (PARAM_nanosec.data != nullptr) {
        *PARAM_nanosec.data = static_cast<int64_t>(ts.tv_nsec);
    }
    return 0;
}
}
}
// @@end

// @@os::win32::ReadFile
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_ReadFile_e28eed45(uint64_t PARAM_hFile, Franko_Fat_Pointer<uint8_t> PARAM_buf, uint32_t PARAM_count, Franko_Thin_Pointer<uint32_t> PARAM_bytesRead)
{
    if (os_is_standard_handle(PARAM_hFile)) {
        franko_panic("os::win32::ReadFile: standard handles are reserved for franko/stdio");
    }

    uint8_t* buffer;
    uint32_t length;

    if (PARAM_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data == nullptr) {
            return 0;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->count;
    } else if (PARAM_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(PARAM_buf.data);
        length = PARAM_buf.count;
    } else {
        return 0;
    }

    if (static_cast<uint64_t>(PARAM_count) > static_cast<uint64_t>(length)) {
        franko_panic("os::win32::ReadFile: count exceeds buffer length");
    }
    DWORD n = 0;
    BOOL ok = ::ReadFile(reinterpret_cast<HANDLE>(static_cast<uintptr_t>(PARAM_hFile)), buffer, static_cast<DWORD>(PARAM_count), &n, nullptr);
    if (PARAM_bytesRead.data != nullptr) {
        *PARAM_bytesRead.data = static_cast<uint32_t>(n);
    }
    return static_cast<int32_t>(ok);
}
}
}
// @@end

// @@os::win32::WriteFile
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_WriteFile_0f393550(uint64_t PARAM_hFile, Franko_Fat_Pointer<uint8_t> PARAM_buf, uint32_t PARAM_count, Franko_Thin_Pointer<uint32_t> PARAM_bytesWritten)
{
    if (os_is_standard_handle(PARAM_hFile)) {
        franko_panic("os::win32::WriteFile: standard handles are reserved for franko/stdio");
    }

    uint8_t* buffer;
    uint32_t length;

    if (PARAM_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data == nullptr) {
            return 0;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_buf.data)->count;
    } else if (PARAM_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(PARAM_buf.data);
        length = PARAM_buf.count;
    } else {
        return 0;
    }

    if (static_cast<uint64_t>(PARAM_count) > static_cast<uint64_t>(length)) {
        franko_panic("os::win32::WriteFile: count exceeds buffer length");
    }
    DWORD n = 0;
    BOOL ok = ::WriteFile(reinterpret_cast<HANDLE>(static_cast<uintptr_t>(PARAM_hFile)), buffer, static_cast<DWORD>(PARAM_count), &n, nullptr);
    if (PARAM_bytesWritten.data != nullptr) {
        *PARAM_bytesWritten.data = static_cast<uint32_t>(n);
    }
    return static_cast<int32_t>(ok);
}
}
}
// @@end

// @@os::win32::QueryPerformanceCounter
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_QueryPerformanceCounter_b452b59a(Franko_Thin_Pointer<int64_t> PARAM_counter)
{
    LARGE_INTEGER value;
    if (!::QueryPerformanceCounter(&value)) {
        return 0;
    }
    if (PARAM_counter.data != nullptr) {
        *PARAM_counter.data = static_cast<int64_t>(value.QuadPart);
    }
    return 1;
}
}
}
// @@end

// @@os::win32::QueryPerformanceFrequency
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_QueryPerformanceFrequency_b452b59a(Franko_Thin_Pointer<int64_t> PARAM_frequency)
{
    LARGE_INTEGER value;
    if (!::QueryPerformanceFrequency(&value)) {
        return 0;
    }
    if (PARAM_frequency.data != nullptr) {
        *PARAM_frequency.data = static_cast<int64_t>(value.QuadPart);
    }
    return 1;
}
}
}
// @@end

// @@os::win32::GetSystemTimePreciseAsFileTime
namespace NS_os {
namespace NS_win32 {
uint64_t FUNC_GetSystemTimePreciseAsFileTime_16e9a907()
{
    FILETIME ft;
    ::GetSystemTimePreciseAsFileTime(&ft);
    ULARGE_INTEGER value;
    value.LowPart = ft.dwLowDateTime;
    value.HighPart = ft.dwHighDateTime;
    return static_cast<uint64_t>(value.QuadPart);
}
}
}
// @@end
