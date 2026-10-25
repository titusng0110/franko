#pragma once

/*
 * Minimal POSIX-shaped OS portability layer.
 *
 * Philosophy:
 *   1. POSIX semantics are the ground truth.
 *   2. Only expose low-level, non-derived primitives.
 *   3. Stay within the greatest common factor of POSIX and Windows.
 *
 * Design consequence:
 *   If a POSIX concept has no honest Windows equivalent, it is not exposed.
 *
 * This header intentionally avoids including platform-heavy headers such as
 * windows.h, dirent.h, unistd.h, fcntl.h, and sys/stat.h.
 *
 * Platform-specific implementation details live in os.cpp.
 *
 * Public constants intentionally live only inside namespace os.
 * Users should write os::O_RDONLY, os::R_OK, os::SEEK_SET,
 * os::CLOCK_MONOTONIC_ID, os::STDIN_FILENO, etc.
 */

#ifndef _FILE_OFFSET_BITS
#define _FILE_OFFSET_BITS 64
#endif

#include <stdint.h>
#include <stddef.h>
#include <string>

#ifdef _WIN32
    #include <stdint.h>
#else
    #include <sys/types.h>
#endif

/*
 * Macro firewall for public header.
 *
 * We must undefine common POSIX/CRT macros here because users may include
 * headers like <cstdio> before including this header. Those headers define
 * SEEK_*, STD*_FILENO, etc. as macros, which break our namespace constants.
 */

#ifdef SEEK_SET
#undef SEEK_SET
#endif

#ifdef SEEK_CUR
#undef SEEK_CUR
#endif

#ifdef SEEK_END
#undef SEEK_END
#endif

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

namespace os {

#ifdef _WIN32
    using ssize_t = intptr_t;
#else
    using ssize_t = ::ssize_t;
#endif

    /*
     * Strong-but-bitwise-friendly integer wrapper for flag constants.
     *
     * This prevents accidental use of global POSIX/CRT macros such as O_RDONLY,
     * R_OK, CLOCK_REALTIME, SEEK_SET, etc. in this public API while still
     * allowing normal bitwise expressions:
     *
     *   os::open("x", os::O_CREAT | os::O_WRONLY | os::O_TRUNC);
     *   os::access("x", os::R_OK | os::W_OK);
     */
    template <typename Tag>
    struct flag_value {
        int value;

        constexpr explicit flag_value(int v = 0) : value(v) {}
        constexpr explicit operator int() const { return value; }
    };

    template <typename Tag>
    constexpr flag_value<Tag> operator|(flag_value<Tag> a, flag_value<Tag> b) {
        return flag_value<Tag>(a.value | b.value);
    }

    template <typename Tag>
    constexpr flag_value<Tag> operator&(flag_value<Tag> a, flag_value<Tag> b) {
        return flag_value<Tag>(a.value & b.value);
    }

    template <typename Tag>
    constexpr flag_value<Tag> operator~(flag_value<Tag> a) {
        return flag_value<Tag>(~a.value);
    }

    template <typename Tag>
    constexpr flag_value<Tag>& operator|=(flag_value<Tag>& a, flag_value<Tag> b) {
        a.value |= b.value;
        return a;
    }

    template <typename Tag>
    constexpr flag_value<Tag>& operator&=(flag_value<Tag>& a, flag_value<Tag> b) {
        a.value &= b.value;
        return a;
    }

    struct access_mode_tag;
    struct open_flags_tag;
    struct seek_origin_tag;
    struct clock_id_tag;

    using access_mode = flag_value<access_mode_tag>;
    using open_flags  = flag_value<open_flags_tag>;
    using seek_origin = flag_value<seek_origin_tag>;
    using clock_id    = flag_value<clock_id_tag>;

    /*
     * Portable standard file descriptors.
     *
     * These are file descriptor numbers, not flags.
     *
     * Users should write:
     *
     *   os::STDIN_FILENO
     *   os::STDOUT_FILENO
     *   os::STDERR_FILENO
     *
     * They intentionally remain int because this API is POSIX-shaped and file
     * descriptors are represented as int throughout the interface.
     */
    constexpr int STDIN_FILENO  = 0;
    constexpr int STDOUT_FILENO = 1;
    constexpr int STDERR_FILENO = 2;

    /*
     * Portable access() modes.
     *
     * X_OK is intentionally not exposed. Windows does not provide a meaningful
     * executable-permission check equivalent to POSIX X_OK.
     */
    constexpr access_mode F_OK{0};
    constexpr access_mode W_OK{2};
    constexpr access_mode R_OK{4};

    /*
     * Portable open() flags.
     *
     * These are intentionally exposed inside namespace os instead of defining
     * global O_* macros in the public header.
     *
     * Their concrete values are chosen to match the usual POSIX/UCRT values.
     * os.cpp validates/translates where necessary.
     */
    constexpr open_flags O_RDONLY{0x0000};
    constexpr open_flags O_WRONLY{0x0001};
    constexpr open_flags O_RDWR  {0x0002};
    constexpr open_flags O_APPEND{0x0008};
    constexpr open_flags O_CREAT {0x0100};
    constexpr open_flags O_TRUNC {0x0200};
    constexpr open_flags O_EXCL  {0x0400};

    /*
     * Text/binary mode.
     *
     * On POSIX these have no effect. On Windows they map to _O_BINARY/_O_TEXT
     * in os.cpp.
     */
    constexpr open_flags O_BINARY{0x8000};
    constexpr open_flags O_TEXT  {0x4000};

    /*
     * Portable lseek() origins.
     *
     * These avoid requiring users to include unistd.h/stdio.h or use global
     * SEEK_* macros.
     */
    constexpr seek_origin SEEK_SET{0};
    constexpr seek_origin SEEK_CUR{1};
    constexpr seek_origin SEEK_END{2};

    /*
     * Portable clock identifiers.
     *
     * These avoid defining or relying on CLOCK_REALTIME/CLOCK_MONOTONIC
     * globally.
     */
    constexpr clock_id CLOCK_REALTIME_ID {0};
    constexpr clock_id CLOCK_MONOTONIC_ID{1};

    /*
     * Portable file metadata.
     *
     * This is intentionally smaller than POSIX struct stat.
     *
     * Included because they are meaningful on both POSIX and Windows:
     *   - mode: file type plus basic read/write permission bits
     *   - size: file size in bytes
     *   - atime_sec: last access time, seconds since Unix epoch
     *   - mtime_sec: last modification time, seconds since Unix epoch
     *
     * Intentionally excluded:
     *   - uid/gid: POSIX ownership has no simple Windows GCD equivalent
     *   - ino/dev/rdev: POSIX inode/device identity does not map cleanly
     *   - nlink: hard-link count semantics are not a portable primitive here
     *   - ctime: POSIX status-change time differs from Windows creation time
     */
    struct stat_t {
        uint32_t mode;
        int64_t  size;
        int64_t  atime_sec;
        int64_t  mtime_sec;
    };

    /*
     * Portable time specification.
     */
    struct timespec_t {
        int64_t tv_sec;
        long    tv_nsec;
    };

    /*
     * Portable directory entry.
     *
     * The greatest-common-factor directory primitive is entry-name iteration.
     * Type information is intentionally omitted.
     */
    struct dirent {
        char d_name[4096];
    };

    /*
     * Opaque directory stream.
     *
     * The concrete platform-specific layout is private to os.cpp.
     */
    struct DIR;

    ssize_t read(int fd, void* buf, size_t count);
    ssize_t write(int fd, const void* buf, size_t count);
    int close(int fd);

    int open(const char* path, open_flags oflag, int mode = 0666);
    int64_t lseek(int fd, int64_t offset, seek_origin origin);

    int unlink(const char* path);
    int rename(const char* old_path, const char* new_path);

    int stat(const char* path, os::stat_t* out);
    int fstat(int fd, os::stat_t* out);

    int access(const char* path, access_mode mode);

    char* getcwd(char* buf, size_t maxlen);
    int chdir(const char* path);

    int mkdir(const char* path);
    int rmdir(const char* path);

    DIR* opendir(const char* path);
    os::dirent* readdir(DIR* dir);
    int closedir(DIR* dir);

    int getpid();
    int isatty(int fd);

    int dup(int fd);
    int dup2(int fd1, int fd2);
    int pipe(int fds[2]);

    int fsync(int fd);
    int ftruncate(int fd, int64_t length);

    int nanosleep(int64_t seconds, int64_t nanoseconds);
    int clock_gettime(clock_id clock_id_value, os::timespec_t* ts);

    [[noreturn]] void abort();
    [[noreturn]] void exit(int status);

    int system(const char* command);

    bool getenv(const char* name, std::string& out_value);
    int setenv(const char* name, const char* value, int overwrite);
    int unsetenv(const char* name);

} // namespace os