#include "FrankoModule_os.hpp"

#include <cerrno>
#include <climits>
#include <cstdlib>
#include <cstring>
#include <string>

#ifdef _WIN32

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#ifndef NOMINMAX
#define NOMINMAX
#endif

#include <windows.h>
#include <io.h>
#include <direct.h>
#include <process.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <time.h>

/*
 * Macro firewall.
 *
 * Windows/UCRT headers may define O_*, F_OK/R_OK/W_OK, SEEK_*,
 * STD*_FILENO, CLOCK_*, etc. as macros. Macros expand even inside qualified
 * names like os::O_RDONLY, producing invalid code such as os::0.
 */
#ifdef STDIN_FILENO
#undef STDIN_FILENO
#endif

#ifdef STDOUT_FILENO
#undef STDOUT_FILENO
#endif

#ifdef STDERR_FILENO
#undef STDERR_FILENO
#endif

#ifdef F_OK
#undef F_OK
#endif

#ifdef W_OK
#undef W_OK
#endif

#ifdef R_OK
#undef R_OK
#endif

#ifdef O_RDONLY
#undef O_RDONLY
#endif

#ifdef O_WRONLY
#undef O_WRONLY
#endif

#ifdef O_RDWR
#undef O_RDWR
#endif

#ifdef O_APPEND
#undef O_APPEND
#endif

#ifdef O_CREAT
#undef O_CREAT
#endif

#ifdef O_TRUNC
#undef O_TRUNC
#endif

#ifdef O_EXCL
#undef O_EXCL
#endif

#ifdef O_BINARY
#undef O_BINARY
#endif

#ifdef O_TEXT
#undef O_TEXT
#endif

#ifdef SEEK_SET
#undef SEEK_SET
#endif

#ifdef SEEK_CUR
#undef SEEK_CUR
#endif

#ifdef SEEK_END
#undef SEEK_END
#endif

#ifdef CLOCK_REALTIME
#undef CLOCK_REALTIME
#endif

#ifdef CLOCK_MONOTONIC
#undef CLOCK_MONOTONIC
#endif

namespace os {
namespace internal {

    bool utf8_to_utf16(const char* utf8_str, std::wstring& out) {
        if (!utf8_str) {
            errno = EINVAL;
            return false;
        }

        int size_needed = MultiByteToWideChar(
            CP_UTF8,
            MB_ERR_INVALID_CHARS,
            utf8_str,
            -1,
            nullptr,
            0
        );

        if (size_needed <= 0) {
            errno = EINVAL;
            return false;
        }

        std::wstring tmp(static_cast<size_t>(size_needed), L'\0');

        int written = MultiByteToWideChar(
            CP_UTF8,
            MB_ERR_INVALID_CHARS,
            utf8_str,
            -1,
            &tmp[0],
            size_needed
        );

        if (written <= 0) {
            errno = EINVAL;
            return false;
        }

        tmp.resize(static_cast<size_t>(size_needed - 1));
        out = tmp;
        return true;
    }

    std::string utf16_to_utf8(const wchar_t* utf16_str) {
        if (!utf16_str) {
            errno = EINVAL;
            return "";
        }

        if (!*utf16_str) {
            return "";
        }

        int size_needed = WideCharToMultiByte(
            CP_UTF8,
            0,
            utf16_str,
            -1,
            nullptr,
            0,
            nullptr,
            nullptr
        );

        if (size_needed <= 0) {
            errno = EINVAL;
            return "";
        }

        std::string tmp(static_cast<size_t>(size_needed), '\0');

        int written = WideCharToMultiByte(
            CP_UTF8,
            0,
            utf16_str,
            -1,
            &tmp[0],
            size_needed,
            nullptr,
            nullptr
        );

        if (written <= 0) {
            errno = EINVAL;
            return "";
        }

        tmp.resize(static_cast<size_t>(size_needed - 1));
        return tmp;
    }

    void windows_error_to_errno(DWORD err) {
        switch (err) {
            case ERROR_FILE_NOT_FOUND:
            case ERROR_PATH_NOT_FOUND:
                errno = ENOENT;
                break;

            case ERROR_ACCESS_DENIED:
            case ERROR_SHARING_VIOLATION: // <-- ADDED
            case ERROR_LOCK_VIOLATION:    // <-- ADDED
                errno = EACCES;
                break;

            case ERROR_ALREADY_EXISTS:
            case ERROR_FILE_EXISTS:
                errno = EEXIST;
                break;

            case ERROR_INVALID_NAME:
            case ERROR_BAD_PATHNAME:
                errno = ENOENT;
                break;

            case ERROR_NOT_ENOUGH_MEMORY:
            case ERROR_OUTOFMEMORY:
                errno = ENOMEM;
                break;

            case ERROR_DIR_NOT_EMPTY:
                errno = ENOTEMPTY;
                break;

            case ERROR_NOT_SAME_DEVICE:
                errno = EXDEV;
                break;

            case ERROR_INVALID_PARAMETER:
                errno = EINVAL;
                break;

            default:
                errno = EINVAL;
                break;
        }
    }

    void stat64_to_stat_t(const struct __stat64& st, os::stat_t* out) {
        std::memset(out, 0, sizeof(*out));

        out->mode = static_cast<uint32_t>(st.st_mode);
        out->size = static_cast<int64_t>(st.st_size);
        out->atime_sec = static_cast<int64_t>(st.st_atime);
        out->mtime_sec = static_cast<int64_t>(st.st_mtime);
    }

    bool valid_env_name(const char* name) {
        if (!name || !name[0]) {
            return false;
        }

        for (const char* p = name; *p; ++p) {
            if (*p == '=') {
                return false;
            }
        }

        return true;
    }

    bool valid_access_mode(os::access_mode mode) {
        int raw = static_cast<int>(mode);
        return (raw & ~(static_cast<int>(os::R_OK) | static_cast<int>(os::W_OK))) == 0;
    }

    int translate_open_flags_to_ucrt(os::open_flags oflag) {
        int raw = static_cast<int>(oflag);
        int native = 0;

        switch (raw & 0x0003) {
            case static_cast<int>(os::O_RDONLY):
                native |= _O_RDONLY;
                break;

            case static_cast<int>(os::O_WRONLY):
                native |= _O_WRONLY;
                break;

            case static_cast<int>(os::O_RDWR):
                native |= _O_RDWR;
                break;

            default:
                native |= _O_RDONLY;
                break;
        }

        if (raw & static_cast<int>(os::O_APPEND)) native |= _O_APPEND;
        if (raw & static_cast<int>(os::O_CREAT))  native |= _O_CREAT;
        if (raw & static_cast<int>(os::O_TRUNC))  native |= _O_TRUNC;
        if (raw & static_cast<int>(os::O_EXCL))   native |= _O_EXCL;
        if (raw & static_cast<int>(os::O_BINARY)) native |= _O_BINARY;
        if (raw & static_cast<int>(os::O_TEXT))   native |= _O_TEXT;

        return native;
    }

    int translate_seek_origin_to_ucrt(os::seek_origin origin) {
        int raw = static_cast<int>(origin);

        if (raw == static_cast<int>(os::SEEK_SET)) {
            return 0;
        }

        if (raw == static_cast<int>(os::SEEK_CUR)) {
            return 1;
        }

        if (raw == static_cast<int>(os::SEEK_END)) {
            return 2;
        }

        errno = EINVAL;
        return -1;
    }

} // namespace internal

struct DIR {
    HANDLE handle;
    WIN32_FIND_DATAW data;
    bool first;
    bool valid;
    os::dirent entry;
};

os::ssize_t read(int fd, void* buf, size_t count) {
    unsigned int clamped =
        (count > static_cast<size_t>(INT_MAX))
            ? static_cast<unsigned int>(INT_MAX)
            : static_cast<unsigned int>(count);

    return static_cast<os::ssize_t>(_read(fd, buf, clamped));
}

os::ssize_t write(int fd, const void* buf, size_t count) {
    unsigned int clamped =
        (count > static_cast<size_t>(INT_MAX))
            ? static_cast<unsigned int>(INT_MAX)
            : static_cast<unsigned int>(count);

    return static_cast<os::ssize_t>(_write(fd, buf, clamped));
}

int close(int fd) {
    return _close(fd);
}

int open(const char* path, os::open_flags oflag, int mode) {
    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    int native_flags = internal::translate_open_flags_to_ucrt(oflag);
    return _wopen(wpath.c_str(), native_flags, mode);
}

int64_t lseek(int fd, int64_t offset, os::seek_origin origin) {
    int native_origin = internal::translate_seek_origin_to_ucrt(origin);

    if (native_origin == -1) {
        return -1;
    }

    return static_cast<int64_t>(_lseeki64(fd, offset, native_origin));
}

int unlink(const char* path) {
    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    return _wunlink(wpath.c_str());
}

int rename(const char* old_path, const char* new_path) {
    std::wstring wold;
    std::wstring wnew;

    if (!internal::utf8_to_utf16(old_path, wold)) {
        return -1;
    }

    if (!internal::utf8_to_utf16(new_path, wnew)) {
        return -1;
    }

    BOOL ok = MoveFileExW(
        wold.c_str(),
        wnew.c_str(),
        MOVEFILE_REPLACE_EXISTING
    );

    if (!ok) {
        internal::windows_error_to_errno(GetLastError());
        return -1;
    }

    return 0;
}

int stat(const char* path, os::stat_t* out) {
    if (!out) {
        errno = EINVAL;
        return -1;
    }

    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    struct __stat64 st;

    if (_wstat64(wpath.c_str(), &st) == -1) {
        return -1;
    }

    internal::stat64_to_stat_t(st, out);
    return 0;
}

int fstat(int fd, os::stat_t* out) {
    if (!out) {
        errno = EINVAL;
        return -1;
    }

    struct __stat64 st;

    if (_fstat64(fd, &st) == -1) {
        return -1;
    }

    internal::stat64_to_stat_t(st, out);
    return 0;
}

int access(const char* path, os::access_mode mode) {
    if (!internal::valid_access_mode(mode)) {
        errno = EINVAL;
        return -1;
    }

    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    return _waccess(wpath.c_str(), static_cast<int>(mode));
}

char* getcwd(char* buf, size_t maxlen) {
    wchar_t* wres = _wgetcwd(nullptr, 0);

    if (!wres) {
        return nullptr;
    }

    std::string utf8_path = internal::utf16_to_utf8(wres);
    std::free(wres);

    for (size_t i = 0; i < utf8_path.length(); ++i) {
        if (utf8_path[i] == '\\') {
            utf8_path[i] = '/';
        }
    }

    size_t required_size = utf8_path.length() + 1;

    if (buf) {
        if (required_size > maxlen) {
            errno = ERANGE;
            return nullptr;
        }

        std::memcpy(buf, utf8_path.c_str(), required_size);
        return buf;
    }

    char* alloc_buf = static_cast<char*>(std::malloc(required_size));

    if (alloc_buf) {
        std::memcpy(alloc_buf, utf8_path.c_str(), required_size);
    }

    return alloc_buf;
}

int chdir(const char* path) {
    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    return _wchdir(wpath.c_str());
}

int mkdir(const char* path) {
    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    return _wmkdir(wpath.c_str());
}

int rmdir(const char* path) {
    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return -1;
    }

    return _wrmdir(wpath.c_str());
}

DIR* opendir(const char* path) {
    std::wstring wpath;

    if (!internal::utf8_to_utf16(path, wpath)) {
        return nullptr;
    }

    if (!wpath.empty()) {
        wchar_t last = wpath[wpath.size() - 1];

        // ADDED: Check for L':' so "C:" becomes "C:*" instead of "C:\*"
        if (last != L'\\' && last != L'/' && last != L':') {
            wpath += L'\\';
        }
    }

    wpath += L'*';

    DIR* dir = static_cast<DIR*>(std::calloc(1, sizeof(DIR)));

    if (!dir) {
        errno = ENOMEM;
        return nullptr;
    }

    dir->handle = FindFirstFileW(wpath.c_str(), &dir->data);

    if (dir->handle == INVALID_HANDLE_VALUE) {
        DWORD err = GetLastError();
        std::free(dir);
        internal::windows_error_to_errno(err);
        return nullptr;
    }

    dir->first = true;
    dir->valid = true;

    return dir;
}

os::dirent* readdir(DIR* dir) {
    if (!dir || !dir->valid) {
        errno = EBADF;
        return nullptr;
    }

    WIN32_FIND_DATAW* data = &dir->data;

    if (dir->first) {
        dir->first = false;
    } else {
        BOOL ok = FindNextFileW(dir->handle, data);

        if (!ok) {
            DWORD err = GetLastError();

            if (err == ERROR_NO_MORE_FILES) {
                return nullptr;
            }

            internal::windows_error_to_errno(err);
            return nullptr;
        }
    }

    std::string name = internal::utf16_to_utf8(data->cFileName);

    if (name.size() >= sizeof(dir->entry.d_name)) {
        errno = ENAMETOOLONG;
        return nullptr;
    }

    std::memset(&dir->entry, 0, sizeof(dir->entry));
    std::memcpy(dir->entry.d_name, name.c_str(), name.size() + 1);

    return &dir->entry;
}

int closedir(DIR* dir) {
    if (!dir) {
        errno = EBADF;
        return -1;
    }

    int result = 0;

    if (dir->valid && dir->handle != INVALID_HANDLE_VALUE) {
        if (!FindClose(dir->handle)) {
            internal::windows_error_to_errno(GetLastError());
            result = -1;
        }
    }

    dir->valid = false;
    std::free(dir);

    return result;
}

int getpid() {
    return _getpid();
}

int isatty(int fd) {
    return _isatty(fd);
}

int dup(int fd) {
    return _dup(fd);
}

int dup2(int fd1, int fd2) {
    return _dup2(fd1, fd2);
}

int pipe(int fds[2]) {
    return _pipe(fds, 4096, _O_BINARY);
}

int fsync(int fd) {
    return _commit(fd);
}

int ftruncate(int fd, int64_t length) {
    if (length < 0) {
        errno = EINVAL;
        return -1;
    }

    errno_t err = _chsize_s(fd, static_cast<int64_t>(length));

    if (err != 0) {
        errno = err;
        return -1;
    }

    return 0;
}

int nanosleep(int64_t seconds, int64_t nanoseconds) {
    if (seconds < 0 || nanoseconds < 0 || nanoseconds >= 1000000000LL) {
        errno = EINVAL;
        return -1;
    }

    uint64_t milliseconds =
        (static_cast<uint64_t>(seconds) * 1000ull)
        + ((static_cast<uint64_t>(nanoseconds) + 999999ull) / 1000000ull);

    if (milliseconds > 0xFFFFFFFFull) {
        milliseconds = 0xFFFFFFFFull;
    }

    Sleep(static_cast<DWORD>(milliseconds));

    return 0;
}

int clock_gettime(os::clock_id clock_id_value, os::timespec_t* ts) {
    if (!ts) {
        errno = EINVAL;
        return -1;
    }

    if (static_cast<int>(clock_id_value) == static_cast<int>(os::CLOCK_REALTIME_ID)) {
        FILETIME ft;

        using GetSystemTimePreciseAsFileTimeFn = VOID (WINAPI*)(LPFILETIME);

        static GetSystemTimePreciseAsFileTimeFn precise_fn = []() -> GetSystemTimePreciseAsFileTimeFn {
            HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");

            if (!kernel32) {
                return nullptr;
            }

            FARPROC proc = GetProcAddress(
                kernel32,
                "GetSystemTimePreciseAsFileTime"
            );

            if (!proc) {
                return nullptr;
            }

            GetSystemTimePreciseAsFileTimeFn fn = nullptr;

            static_assert(
                sizeof(fn) == sizeof(proc),
                "Function pointer size mismatch"
            );

            std::memcpy(&fn, &proc, sizeof(fn));

            return fn;
        }();

        if (precise_fn) {
            precise_fn(&ft);
        } else {
            GetSystemTimeAsFileTime(&ft);
        }

        ULARGE_INTEGER uli;
        uli.LowPart = ft.dwLowDateTime;
        uli.HighPart = ft.dwHighDateTime;

        constexpr uint64_t WINDOWS_TO_UNIX_EPOCH_100NS =
            116444736000000000ULL;

        if (uli.QuadPart < WINDOWS_TO_UNIX_EPOCH_100NS) {
            ts->tv_sec = 0;
            ts->tv_nsec = 0;
            return 0;
        }

        uint64_t unix_100ns = uli.QuadPart - WINDOWS_TO_UNIX_EPOCH_100NS;

        ts->tv_sec = static_cast<int64_t>(unix_100ns / 10000000ULL);
        ts->tv_nsec = static_cast<long>((unix_100ns % 10000000ULL) * 100ULL);

        return 0;
    }

    if (static_cast<int>(clock_id_value) == static_cast<int>(os::CLOCK_MONOTONIC_ID)) {
        LARGE_INTEGER freq;
        LARGE_INTEGER counter;

        if (!QueryPerformanceFrequency(&freq) ||
            !QueryPerformanceCounter(&counter)) {
            errno = EINVAL;
            return -1;
        }

        ts->tv_sec = static_cast<int64_t>(counter.QuadPart / freq.QuadPart);

        int64_t remainder = counter.QuadPart % freq.QuadPart;
        ts->tv_nsec =
            static_cast<long>((remainder * 1000000000LL) / freq.QuadPart);

        return 0;
    }

    errno = EINVAL;
    return -1;
}

[[noreturn]] void abort() {
    std::abort();
}

[[noreturn]] void exit(int status) {
    std::exit(status);
}

int system(const char* command) {
    if (!command) {
        return _wsystem(nullptr);
    }

    std::wstring wcmd;

    if (!internal::utf8_to_utf16(command, wcmd)) {
        return -1;
    }

    return _wsystem(wcmd.c_str());
}

bool getenv(const char* name, std::string& out_value) {
    if (!name) {
        return false;
    }

    std::wstring wname;

    if (!internal::utf8_to_utf16(name, wname)) {
        return false;
    }

    const wchar_t* wval = _wgetenv(wname.c_str());

    if (!wval) {
        return false;
    }

    out_value = internal::utf16_to_utf8(wval);
    return true;
}

int setenv(const char* name, const char* value, int overwrite) {
    if (!internal::valid_env_name(name) || !value) {
        errno = EINVAL;
        return -1;
    }

    if (!overwrite) {
        std::string existing;

        if (os::getenv(name, existing)) {
            return 0;
        }
    }

    std::wstring wname;
    std::wstring wvalue;

    if (!internal::utf8_to_utf16(name, wname)) {
        return -1;
    }

    if (!internal::utf8_to_utf16(value, wvalue)) {
        return -1;
    }

    errno_t err = _wputenv_s(wname.c_str(), wvalue.c_str());

    if (err != 0) {
        errno = err;
        return -1;
    }

    return 0;
}

int unsetenv(const char* name) {
    if (!internal::valid_env_name(name)) {
        errno = EINVAL;
        return -1;
    }

    std::wstring wname;

    if (!internal::utf8_to_utf16(name, wname)) {
        return -1;
    }

    errno_t err = _wputenv_s(wname.c_str(), L"");

    if (err != 0) {
        errno = err;
        return -1;
    }

    return 0;
}

} // namespace os

#else

#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <time.h>

/*
 * Capture native POSIX constants before undefining macro names.
 */
static constexpr int FRANKO_NATIVE_O_RDONLY = O_RDONLY;
static constexpr int FRANKO_NATIVE_O_WRONLY = O_WRONLY;
static constexpr int FRANKO_NATIVE_O_RDWR   = O_RDWR;
static constexpr int FRANKO_NATIVE_O_APPEND = O_APPEND;
static constexpr int FRANKO_NATIVE_O_CREAT  = O_CREAT;
static constexpr int FRANKO_NATIVE_O_TRUNC  = O_TRUNC;
static constexpr int FRANKO_NATIVE_O_EXCL   = O_EXCL;

static constexpr int FRANKO_NATIVE_SEEK_SET = SEEK_SET;
static constexpr int FRANKO_NATIVE_SEEK_CUR = SEEK_CUR;
static constexpr int FRANKO_NATIVE_SEEK_END = SEEK_END;

static constexpr int FRANKO_NATIVE_CLOCK_REALTIME  = CLOCK_REALTIME;
static constexpr int FRANKO_NATIVE_CLOCK_MONOTONIC = CLOCK_MONOTONIC;

/*
 * Macro firewall.
 */
#ifdef STDIN_FILENO
#undef STDIN_FILENO
#endif

#ifdef STDOUT_FILENO
#undef STDOUT_FILENO
#endif

#ifdef STDERR_FILENO
#undef STDERR_FILENO
#endif

#ifdef F_OK
#undef F_OK
#endif

#ifdef W_OK
#undef W_OK
#endif

#ifdef R_OK
#undef R_OK
#endif

#ifdef O_RDONLY
#undef O_RDONLY
#endif

#ifdef O_WRONLY
#undef O_WRONLY
#endif

#ifdef O_RDWR
#undef O_RDWR
#endif

#ifdef O_APPEND
#undef O_APPEND
#endif

#ifdef O_CREAT
#undef O_CREAT
#endif

#ifdef O_TRUNC
#undef O_TRUNC
#endif

#ifdef O_EXCL
#undef O_EXCL
#endif

#ifdef O_BINARY
#undef O_BINARY
#endif

#ifdef O_TEXT
#undef O_TEXT
#endif

#ifdef SEEK_SET
#undef SEEK_SET
#endif

#ifdef SEEK_CUR
#undef SEEK_CUR
#endif

#ifdef SEEK_END
#undef SEEK_END
#endif

#ifdef CLOCK_REALTIME
#undef CLOCK_REALTIME
#endif

#ifdef CLOCK_MONOTONIC
#undef CLOCK_MONOTONIC
#endif

namespace os {
namespace internal {

    void stat_to_stat_t(const struct stat& st, os::stat_t* out) {
        std::memset(out, 0, sizeof(*out));

        out->mode = static_cast<uint32_t>(st.st_mode);
        out->size = static_cast<int64_t>(st.st_size);
        out->atime_sec = static_cast<int64_t>(st.st_atime);
        out->mtime_sec = static_cast<int64_t>(st.st_mtime);
    }

    bool valid_env_name(const char* name) {
        if (!name || !name[0]) {
            return false;
        }

        for (const char* p = name; *p; ++p) {
            if (*p == '=') {
                return false;
            }
        }

        return true;
    }

    int translate_open_flags_to_native(os::open_flags oflag) {
        int raw = static_cast<int>(oflag);
        int native = 0;

        switch (raw & 0x0003) {
            case static_cast<int>(os::O_RDONLY):
                native |= FRANKO_NATIVE_O_RDONLY;
                break;

            case static_cast<int>(os::O_WRONLY):
                native |= FRANKO_NATIVE_O_WRONLY;
                break;

            case static_cast<int>(os::O_RDWR):
                native |= FRANKO_NATIVE_O_RDWR;
                break;

            default:
                native |= FRANKO_NATIVE_O_RDONLY;
                break;
        }

        if (raw & static_cast<int>(os::O_APPEND)) native |= FRANKO_NATIVE_O_APPEND;
        if (raw & static_cast<int>(os::O_CREAT))  native |= FRANKO_NATIVE_O_CREAT;
        if (raw & static_cast<int>(os::O_TRUNC))  native |= FRANKO_NATIVE_O_TRUNC;
        if (raw & static_cast<int>(os::O_EXCL))   native |= FRANKO_NATIVE_O_EXCL;

        /*
         * os::O_BINARY and os::O_TEXT intentionally have no POSIX effect.
         */

        return native;
    }

    int translate_seek_origin_to_native(os::seek_origin origin) {
        int raw = static_cast<int>(origin);

        if (raw == static_cast<int>(os::SEEK_SET)) {
            return FRANKO_NATIVE_SEEK_SET;
        }

        if (raw == static_cast<int>(os::SEEK_CUR)) {
            return FRANKO_NATIVE_SEEK_CUR;
        }

        if (raw == static_cast<int>(os::SEEK_END)) {
            return FRANKO_NATIVE_SEEK_END;
        }

        errno = EINVAL;
        return -1;
    }

    int translate_clock_id_to_native(os::clock_id clock_id_value) {
        if (static_cast<int>(clock_id_value) == static_cast<int>(os::CLOCK_REALTIME_ID)) {
            return FRANKO_NATIVE_CLOCK_REALTIME;
        }

        if (static_cast<int>(clock_id_value) == static_cast<int>(os::CLOCK_MONOTONIC_ID)) {
            return FRANKO_NATIVE_CLOCK_MONOTONIC;
        }

        errno = EINVAL;
        return -1;
    }

} // namespace internal

struct DIR {
    ::DIR* native_dir;
    os::dirent entry;
};

os::ssize_t read(int fd, void* buf, size_t count) {
    return ::read(fd, buf, count);
}

os::ssize_t write(int fd, const void* buf, size_t count) {
    return ::write(fd, buf, count);
}

int close(int fd) {
    return ::close(fd);
}

int open(const char* path, os::open_flags oflag, int mode) {
    int native_flags = internal::translate_open_flags_to_native(oflag);
    return ::open(path, native_flags, mode);
}

int64_t lseek(int fd, int64_t offset, os::seek_origin origin) {
    int native_origin = internal::translate_seek_origin_to_native(origin);

    if (native_origin == -1) {
        return -1;
    }

    return static_cast<int64_t>(
        ::lseek(fd, static_cast<off_t>(offset), native_origin)
    );
}

int unlink(const char* path) {
    return ::unlink(path);
}

int rename(const char* old_path, const char* new_path) {
    return ::rename(old_path, new_path);
}

int stat(const char* path, os::stat_t* out) {
    if (!out) {
        errno = EINVAL;
        return -1;
    }

    struct stat st;

    if (::stat(path, &st) == -1) {
        return -1;
    }

    internal::stat_to_stat_t(st, out);
    return 0;
}

int fstat(int fd, os::stat_t* out) {
    if (!out) {
        errno = EINVAL;
        return -1;
    }

    struct stat st;

    if (::fstat(fd, &st) == -1) {
        return -1;
    }

    internal::stat_to_stat_t(st, out);
    return 0;
}

int access(const char* path, os::access_mode mode) {
    return ::access(path, static_cast<int>(mode));
}

char* getcwd(char* buf, size_t size) {
    return ::getcwd(buf, size);
}

int chdir(const char* path) {
    return ::chdir(path);
}

int mkdir(const char* path) {
    return ::mkdir(path, 0777);
}

int rmdir(const char* path) {
    return ::rmdir(path);
}

DIR* opendir(const char* path) {
    ::DIR* nd = ::opendir(path);

    if (!nd) {
        return nullptr;
    }

    DIR* dir = static_cast<DIR*>(std::calloc(1, sizeof(DIR)));

    if (!dir) {
        ::closedir(nd);
        errno = ENOMEM;
        return nullptr;
    }

    dir->native_dir = nd;
    return dir;
}

os::dirent* readdir(DIR* dir) {
    if (!dir || !dir->native_dir) {
        errno = EBADF;
        return nullptr;
    }

    errno = 0;

    struct ::dirent* ent = ::readdir(dir->native_dir);

    if (!ent) {
        return nullptr;
    }

    size_t len = std::strlen(ent->d_name);

    if (len >= sizeof(dir->entry.d_name)) {
        errno = ENAMETOOLONG;
        return nullptr;
    }

    std::memset(&dir->entry, 0, sizeof(dir->entry));
    std::memcpy(dir->entry.d_name, ent->d_name, len + 1);

    return &dir->entry;
}

int closedir(DIR* dir) {
    if (!dir || !dir->native_dir) {
        errno = EBADF;
        return -1;
    }

    int result = ::closedir(dir->native_dir);
    std::free(dir);

    return result;
}

int getpid() {
    return ::getpid();
}

int isatty(int fd) {
    return ::isatty(fd);
}

int dup(int fd) {
    return ::dup(fd);
}

int dup2(int fd1, int fd2) {
    return ::dup2(fd1, fd2);
}

int pipe(int fds[2]) {
    return ::pipe(fds);
}

int fsync(int fd) {
#ifdef __APPLE__
    int r = ::fcntl(fd, F_FULLFSYNC);

    if (r != -1) {
        return r;
    }
#endif

    return ::fsync(fd);
}

int ftruncate(int fd, int64_t length) {
    if (length < 0) {
        errno = EINVAL;
        return -1;
    }

    return ::ftruncate(fd, static_cast<off_t>(length));
}

int nanosleep(int64_t seconds, int64_t nanoseconds) {
    if (seconds < 0 || nanoseconds < 0 || nanoseconds >= 1000000000LL) {
        errno = EINVAL;
        return -1;
    }

    struct ::timespec req;
    struct ::timespec rem;

    req.tv_sec = static_cast<time_t>(seconds);
    req.tv_nsec = static_cast<long>(nanoseconds);

    while (::nanosleep(&req, &rem) == -1) {
        if (errno != EINTR) {
            return -1;
        }

        req = rem;
    }

    return 0;
}

int clock_gettime(os::clock_id clock_id_value, os::timespec_t* ts) {
    if (!ts) {
        errno = EINVAL;
        return -1;
    }

    int native_clock_id = internal::translate_clock_id_to_native(clock_id_value);

    if (native_clock_id == -1) {
        return -1;
    }

    struct ::timespec native_ts;

    int result = ::clock_gettime(native_clock_id, &native_ts);

    if (result == 0) {
        ts->tv_sec = static_cast<int64_t>(native_ts.tv_sec);
        ts->tv_nsec = static_cast<long>(native_ts.tv_nsec);
    }

    return result;
}

[[noreturn]] void abort() {
    std::abort();
}

[[noreturn]] void exit(int status) {
    std::exit(status);
}

int system(const char* command) {
    return std::system(command);
}

bool getenv(const char* name, std::string& out_value) {
    if (!name) {
        return false;
    }

    const char* val = std::getenv(name);

    if (!val) {
        return false;
    }

    out_value = val;
    return true;
}

int setenv(const char* name, const char* value, int overwrite) {
    if (!internal::valid_env_name(name) || !value) {
        errno = EINVAL;
        return -1;
    }

    return ::setenv(name, value, overwrite);
}

int unsetenv(const char* name) {
    if (!internal::valid_env_name(name)) {
        errno = EINVAL;
        return -1;
    }

    return ::unsetenv(name);
}

} // namespace os

#endif