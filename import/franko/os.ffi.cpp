// ============================================================================
// FFI bridge for `import "franko/os"`.
// ============================================================================
//
// This file is NOT imported by the Franko compiler (imports only allow .fr
// files, so a .cpp next to the module is never picked up as a module). It is
// spliced verbatim into the generated C++ program by Cpp14Codegen when the
// program imports "franko/os" and calls one of the os intrinsics.
//
// Contract:
//
//   - Each `// @@franko-ffi:<block>` ... `// @@franko-ffi:end` section is one
//     splice unit. The `common` block is always spliced first when any
//     intrinsic is used; every other block is spliced only when that
//     intrinsic is actually called, so merely importing "franko/os" adds
//     zero C++ to the output.
//
//   - Every intrinsic block defines its function under the exact mangled C++
//     name the backend emits for the Franko declaration in ../os.fr
//     (FUNC_<name>_HASH_<8hex>, the FNV-1a hash of the canonical signature
//     key). This is the same trick ProgramTemplateWithRawCArgs.cpp uses to
//     call FUNC_main_HASH_db73581e. Main sanity-checks that each used
//     mangled name is present here and fails the compile loudly on drift.
//
//   - Signatures must match the prototypes Cpp14Codegen emits for the
//     declarations in ../os.fr, including the NS_os::NS_posix /
//     NS_os::NS_win32 namespace nesting and the VAR_-prefixed parameter
//     names. The stub bodies in ../os.fr are never emitted when the bridge
//     is active; the bridge definitions are the only definitions.
//
//   - The bridge is spliced after the program template's
//     `#include "FrankoRuntime.hpp"`, so Franko_Dynamic_Array, franko_panic,
//     and the fixed-width integer types are available. Keep every block
//     warning-clean under -Wall -Wextra -Wpedantic -Wshadow.
//
// Platform notes:
//
//   - os::posix::read/write map to ::read/::write and are POSIX-only.
//     On Windows the block is a #error, mirroring the win32 blocks
//     below: there is no _read/_write fallback. The Franko compiler
//     likewise rejects a live posix call when the target is Windows,
//     so portable programs must guard it with os::IS_WINDOWS (the
//     untaken branch is pruned before splicing, which is why such
//     programs still compile on both targets). The buffer bound is
//     checked against the array length first: count > length aborts
//     via franko_panic, so the FFI cannot corrupt memory. A null or
//     uninitialized buffer returns the stub failure value (-1) instead
//     of crashing.
//   - os::win32::* are the Win32 File API (fileapi.h: ReadFile/WriteFile;
//     processenv.h: GetStdHandle; both arrive via <windows.h>, which is the
//     only portable way to include them). lpOverlapped is dropped: the
//     bridge always passes NULL (synchronous I/O). Off Windows the block is
//     a #error, so a win32 call compiled for the wrong target fails with a
//     clear message at the C++ stage instead of a link error.

// @@franko-ffi:common
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
// @@franko-ffi:end

// @@franko-ffi:posix.read
namespace NS_os {
namespace NS_posix {
int64_t FUNC_read_HASH_be149563(int32_t VAR_fd, Franko_Dynamic_Array<uint8_t>* VAR_buf, uint64_t VAR_count)
{
    if (VAR_buf == nullptr || VAR_buf->data == nullptr) {
        return -1;
    }
    if (VAR_count > static_cast<uint64_t>(VAR_buf->length())) {
        franko_panic("os::posix::read: count exceeds buffer length");
    }
#ifdef _WIN32
#error "os::posix::read is only available when compiling for a non-Windows target (linux, macos)"
#else
    return static_cast<int64_t>(::read(VAR_fd, VAR_buf->data, static_cast<size_t>(VAR_count)));
#endif
}
}
}
// @@franko-ffi:end

// @@franko-ffi:posix.write
namespace NS_os {
namespace NS_posix {
int64_t FUNC_write_HASH_731d748e(int32_t VAR_fd, Franko_Dynamic_Array<uint8_t> const* VAR_buf, uint64_t VAR_count)
{
    if (VAR_buf == nullptr || VAR_buf->data == nullptr) {
        return -1;
    }
    if (VAR_count > static_cast<uint64_t>(VAR_buf->length())) {
        franko_panic("os::posix::write: count exceeds buffer length");
    }
#ifdef _WIN32
#error "os::posix::write is only available when compiling for a non-Windows target (linux, macos)"
#else
    return static_cast<int64_t>(::write(VAR_fd, VAR_buf->data, static_cast<size_t>(VAR_count)));
#endif
}
}
}
// @@franko-ffi:end

// @@franko-ffi:win32.GetStdHandle
namespace NS_os {
namespace NS_win32 {
uint64_t FUNC_GetStdHandle_HASH_493245c2(uint32_t VAR_nStdHandle)
{
#ifdef _WIN32
    HANDLE h = ::GetStdHandle(static_cast<DWORD>(VAR_nStdHandle));
    return static_cast<uint64_t>(reinterpret_cast<uintptr_t>(h));
#else
#error "os::win32::GetStdHandle is only available when compiling for Windows"
#endif
}
}
}
// @@franko-ffi:end

// @@franko-ffi:win32.ReadFile
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_ReadFile_HASH_50f286c1(uint64_t VAR_hFile, Franko_Dynamic_Array<uint8_t>* VAR_buf, uint32_t VAR_count, uint32_t* VAR_bytesRead)
{
#ifdef _WIN32
    if (VAR_buf == nullptr || VAR_buf->data == nullptr) {
        return 0;
    }
    if (static_cast<uint64_t>(VAR_count) > static_cast<uint64_t>(VAR_buf->length())) {
        franko_panic("os::win32::ReadFile: count exceeds buffer length");
    }
    DWORD n = 0;
    BOOL ok = ::ReadFile(reinterpret_cast<HANDLE>(static_cast<uintptr_t>(VAR_hFile)), VAR_buf->data, static_cast<DWORD>(VAR_count), &n, nullptr);
    if (VAR_bytesRead != nullptr) {
        *VAR_bytesRead = static_cast<uint32_t>(n);
    }
    return static_cast<int32_t>(ok);
#else
#error "os::win32::ReadFile is only available when compiling for Windows"
#endif
}
}
}
// @@franko-ffi:end

// @@franko-ffi:win32.WriteFile
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_WriteFile_HASH_c811d200(uint64_t VAR_hFile, Franko_Dynamic_Array<uint8_t> const* VAR_buf, uint32_t VAR_count, uint32_t* VAR_bytesWritten)
{
#ifdef _WIN32
    if (VAR_buf == nullptr || VAR_buf->data == nullptr) {
        return 0;
    }
    if (static_cast<uint64_t>(VAR_count) > static_cast<uint64_t>(VAR_buf->length())) {
        franko_panic("os::win32::WriteFile: count exceeds buffer length");
    }
    DWORD n = 0;
    BOOL ok = ::WriteFile(reinterpret_cast<HANDLE>(static_cast<uintptr_t>(VAR_hFile)), VAR_buf->data, static_cast<DWORD>(VAR_count), &n, nullptr);
    if (VAR_bytesWritten != nullptr) {
        *VAR_bytesWritten = static_cast<uint32_t>(n);
    }
    return static_cast<int32_t>(ok);
#else
#error "os::win32::WriteFile is only available when compiling for Windows"
#endif
}
}
}
// @@franko-ffi:end
