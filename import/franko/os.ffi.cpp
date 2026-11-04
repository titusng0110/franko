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
//     NS_os::NS_win32 namespace nesting and the VAR_-prefixed parameter
//     names. The stub bodies in ../os.fr are never emitted when the bridge
//     is active; the bridge definitions are the only definitions.
//
//   - The bridge is spliced after the program template's runtime
//     `#include` (selected via the `__FRANKO_RUNTIME_HEADER__`
//     placeholder), so Franko_Fat_Pointer, franko_panic,
//     and the fixed-width integer types are available. Keep every block
//     warning-clean under -Wall -Wextra -Wpedantic -Wshadow.
//
// Platform notes:
//
//   - os::posix::read/write map to ::read/::write and are POSIX-only.
//     os.fr declares the posix namespace only for linux/macos, so this
//     block is never inserted into a Windows build.
//   - os::win32::* are the Win32 File API (fileapi.h: ReadFile/WriteFile;
//     processenv.h: GetStdHandle; both arrive via <windows.h>). os.fr
//     declares the win32 namespace only for Windows, so this block is
//     never inserted into a POSIX build. lpOverlapped is dropped: the
//     bridge always passes NULL (synchronous I/O).
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
#else
#  include <unistd.h>
#endif
// @@end

// @@os::posix::read
namespace NS_os {
namespace NS_posix {
int64_t FUNC_read_bee7609e(int32_t VAR_fd, Franko_Fat_Pointer<uint8_t> VAR_buf, uint64_t VAR_count)
{
    uint8_t* buffer;
    uint32_t length;

    if (VAR_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data == nullptr) {
            return -1;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->count;
    } else if (VAR_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(VAR_buf.data);
        length = VAR_buf.count;
    } else {
        return -1;
    }

    if (VAR_count > static_cast<uint64_t>(length)) {
        franko_panic("os::posix::read: count exceeds buffer length");
    }
    return static_cast<int64_t>(::read(VAR_fd, buffer, static_cast<size_t>(VAR_count)));
}
}
}
// @@end

// @@os::posix::write
namespace NS_os {
namespace NS_posix {
int64_t FUNC_write_7c35a12f(int32_t VAR_fd, Franko_Fat_Pointer<uint8_t> VAR_buf, uint64_t VAR_count)
{
    uint8_t* buffer;
    uint32_t length;

    if (VAR_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data == nullptr) {
            return -1;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->count;
    } else if (VAR_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(VAR_buf.data);
        length = VAR_buf.count;
    } else {
        return -1;
    }

    if (VAR_count > static_cast<uint64_t>(length)) {
        franko_panic("os::posix::write: count exceeds buffer length");
    }
    return static_cast<int64_t>(::write(VAR_fd, buffer, static_cast<size_t>(VAR_count)));
}
}
}
// @@end

// @@os::win32::GetStdHandle
namespace NS_os {
namespace NS_win32 {
uint64_t FUNC_GetStdHandle_882a7740(uint32_t VAR_nStdHandle)
{
    HANDLE h = ::GetStdHandle(static_cast<DWORD>(VAR_nStdHandle));
    return static_cast<uint64_t>(reinterpret_cast<uintptr_t>(h));
}
}
}
// @@end

// @@os::win32::ReadFile
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_ReadFile_e28eed45(uint64_t VAR_hFile, Franko_Fat_Pointer<uint8_t> VAR_buf, uint32_t VAR_count, uint32_t* VAR_bytesRead)
{
    uint8_t* buffer;
    uint32_t length;

    if (VAR_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data == nullptr) {
            return 0;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->count;
    } else if (VAR_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(VAR_buf.data);
        length = VAR_buf.count;
    } else {
        return 0;
    }

    if (static_cast<uint64_t>(VAR_count) > static_cast<uint64_t>(length)) {
        franko_panic("os::win32::ReadFile: count exceeds buffer length");
    }
    DWORD n = 0;
    BOOL ok = ::ReadFile(reinterpret_cast<HANDLE>(static_cast<uintptr_t>(VAR_hFile)), buffer, static_cast<DWORD>(VAR_count), &n, nullptr);
    if (VAR_bytesRead != nullptr) {
        *VAR_bytesRead = static_cast<uint32_t>(n);
    }
    return static_cast<int32_t>(ok);
}
}
}
// @@end

// @@os::win32::WriteFile
namespace NS_os {
namespace NS_win32 {
int32_t FUNC_WriteFile_0f393550(uint64_t VAR_hFile, Franko_Fat_Pointer<uint8_t> VAR_buf, uint32_t VAR_count, uint32_t* VAR_bytesWritten)
{
    uint8_t* buffer;
    uint32_t length;

    if (VAR_buf.mode == 3) {
        if (static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data == nullptr) {
            return 0;
        }
        buffer = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->data;
        length = static_cast<Franko_Dynamic_Array<uint8_t>*>(VAR_buf.data)->count;
    } else if (VAR_buf.mode == 2) {
        buffer = static_cast<uint8_t*>(VAR_buf.data);
        length = VAR_buf.count;
    } else {
        return 0;
    }

    if (static_cast<uint64_t>(VAR_count) > static_cast<uint64_t>(length)) {
        franko_panic("os::win32::WriteFile: count exceeds buffer length");
    }
    DWORD n = 0;
    BOOL ok = ::WriteFile(reinterpret_cast<HANDLE>(static_cast<uintptr_t>(VAR_hFile)), buffer, static_cast<DWORD>(VAR_count), &n, nullptr);
    if (VAR_bytesWritten != nullptr) {
        *VAR_bytesWritten = static_cast<uint32_t>(n);
    }
    return static_cast<int32_t>(ok);
}
}
}
// @@end
