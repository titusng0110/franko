# Franko OS API Reference

`FrankoModule_os` is a minimal POSIX-shaped operating-system portability layer for C++.

It exposes a small set of low-level OS primitives through the `os` namespace, with portable behavior across POSIX-like systems and Windows where a reasonably honest equivalent exists.

The public header intentionally avoids including platform-heavy headers such as:

```cpp
<windows.h>
<unistd.h>
<fcntl.h>
<dirent.h>
<sys/stat.h>
```

Platform-specific implementation details live in `FrankoModule_os.cpp`.

***

## Design Philosophy

Franko OS follows three core principles:

1. **POSIX semantics are the ground truth.**  
   The API is intentionally POSIX-shaped: file descriptors are `int`, functions return `0`, `-1`, pointers, or byte counts in the usual POSIX style, and failures generally report details through `errno`.

2. **Only low-level primitives are exposed.**  
   The library exposes foundational OS operations such as file I/O, directory iteration, process/environment helpers, clocks, and sleep. Higher-level derived abstractions are intentionally left to users or higher layers.

3. **Only the greatest common factor of POSIX and Windows is exposed.**  
   If a POSIX concept has no honest Windows equivalent, it is omitted rather than emulated misleadingly.

Examples of intentionally omitted concepts include:

* POSIX user/group ownership fields
* inode/device identity
* hard-link count semantics
* `X_OK` executable permission checks
* full POSIX `struct stat`
* POSIX `ctime` semantics

All public constants live inside namespace `os`.

Users should write:

```cpp
os::O_RDONLY
os::R_OK
os::SEEK_SET
os::CLOCK_MONOTONIC_ID
os::STDIN_FILENO
```

rather than relying on global POSIX or CRT macros.

***

# Header

```cpp
#include "FrankoModule_os.hpp"
```

All public API symbols are inside:

```cpp
namespace os
```

***

# Error Handling

Most functions follow POSIX-style error handling:

* Return `0` or a non-negative value on success.
* Return `-1` or `nullptr` on failure.
* Set `errno` where meaningful.

Example:

```cpp
int fd = os::open("file.txt", os::O_RDONLY);

if (fd < 0) {
    std::perror("os::open");
}
```

Some functions return `bool`, such as `os::getenv()`.

***

# Character Encoding

On Windows, path and environment APIs accept UTF-8 `const char*` strings publicly and internally convert them to UTF-16 before calling wide-character Windows APIs.

On POSIX systems, strings are passed directly to the native APIs.

***

# Constants

## Standard File Descriptors

These constants identify the standard input, output, and error file descriptors.

```cpp
constexpr int STDIN_FILENO  = 0;
constexpr int STDOUT_FILENO = 1;
constexpr int STDERR_FILENO = 2;
```

### `os::STDIN_FILENO`

Standard input file descriptor.

```cpp
os::STDIN_FILENO
```

Value:

```cpp
0
```

***

### `os::STDOUT_FILENO`

Standard output file descriptor.

```cpp
os::STDOUT_FILENO
```

Value:

```cpp
1
```

***

### `os::STDERR_FILENO`

Standard error file descriptor.

```cpp
os::STDERR_FILENO
```

Value:

```cpp
2
```

***

## Access Mode Constants

Used with:

```cpp
os::access()
```

Type:

```cpp
os::access_mode
```

### `os::F_OK`

Checks whether a path exists.

```cpp
constexpr access_mode F_OK{0};
```

Example:

```cpp
if (os::access("file.txt", os::F_OK) == 0) {
    // file exists
}
```

***

### `os::R_OK`

Checks whether a path is readable.

```cpp
constexpr access_mode R_OK{4};
```

Example:

```cpp
if (os::access("file.txt", os::R_OK) == 0) {
    // file is readable
}
```

***

### `os::W_OK`

Checks whether a path is writable.

```cpp
constexpr access_mode W_OK{2};
```

Example:

```cpp
if (os::access("file.txt", os::W_OK) == 0) {
    // file is writable
}
```

***

### Why there is no `os::X_OK`

`X_OK` is intentionally not exposed.

Windows does not provide a clean, portable equivalent to POSIX executable-permission checks. Rather than expose a misleading abstraction, Franko OS omits it.

***

## Open Flag Constants

Used with:

```cpp
os::open()
```

Type:

```cpp
os::open_flags
```

Open flags may be combined with `operator|`.

Example:

```cpp
int fd = os::open(
    "file.txt",
    os::O_WRONLY | os::O_CREAT | os::O_TRUNC,
    0644
);
```

***

### `os::O_RDONLY`

Open file for reading only.

```cpp
constexpr open_flags O_RDONLY{0x0000};
```

Example:

```cpp
int fd = os::open("file.txt", os::O_RDONLY);
```

***

### `os::O_WRONLY`

Open file for writing only.

```cpp
constexpr open_flags O_WRONLY{0x0001};
```

Example:

```cpp
int fd = os::open("file.txt", os::O_WRONLY);
```

***

### `os::O_RDWR`

Open file for both reading and writing.

```cpp
constexpr open_flags O_RDWR{0x0002};
```

Example:

```cpp
int fd = os::open("file.txt", os::O_RDWR);
```

***

### `os::O_APPEND`

Writes append to the end of the file.

```cpp
constexpr open_flags O_APPEND{0x0008};
```

Example:

```cpp
int fd = os::open("log.txt", os::O_WRONLY | os::O_APPEND);
```

***

### `os::O_CREAT`

Create the file if it does not already exist.

```cpp
constexpr open_flags O_CREAT{0x0100};
```

When using `O_CREAT`, the `mode` argument to `os::open()` is relevant.

Example:

```cpp
int fd = os::open("new.txt", os::O_WRONLY | os::O_CREAT, 0644);
```

***

### `os::O_TRUNC`

Truncate the file to zero length if it already exists.

```cpp
constexpr open_flags O_TRUNC{0x0200};
```

Example:

```cpp
int fd = os::open("file.txt", os::O_WRONLY | os::O_TRUNC);
```

***

### `os::O_EXCL`

Used with `O_CREAT` to fail if the file already exists.

```cpp
constexpr open_flags O_EXCL{0x0400};
```

Example:

```cpp
int fd = os::open(
    "lockfile",
    os::O_WRONLY | os::O_CREAT | os::O_EXCL,
    0644
);
```

***

### `os::O_BINARY`

Open file in binary mode.

```cpp
constexpr open_flags O_BINARY{0x8000};
```

On POSIX systems, this has no effect.

On Windows, this maps to binary-mode file I/O.

Example:

```cpp
int fd = os::open("image.bin", os::O_RDONLY | os::O_BINARY);
```

***

### `os::O_TEXT`

Open file in text mode.

```cpp
constexpr open_flags O_TEXT{0x4000};
```

On POSIX systems, this has no effect.

On Windows, this maps to text-mode file I/O.

Example:

```cpp
int fd = os::open("notes.txt", os::O_RDONLY | os::O_TEXT);
```

***

## Seek Origin Constants

Used with:

```cpp
os::lseek()
```

Type:

```cpp
os::seek_origin
```

***

### `os::SEEK_SET`

Seek relative to the beginning of the file.

```cpp
constexpr seek_origin SEEK_SET{0};
```

Example:

```cpp
os::lseek(fd, 0, os::SEEK_SET);
```

***

### `os::SEEK_CUR`

Seek relative to the current file position.

```cpp
constexpr seek_origin SEEK_CUR{1};
```

Example:

```cpp
os::lseek(fd, 10, os::SEEK_CUR);
```

***

### `os::SEEK_END`

Seek relative to the end of the file.

```cpp
constexpr seek_origin SEEK_END{2};
```

Example:

```cpp
os::lseek(fd, 0, os::SEEK_END);
```

***

## Clock ID Constants

Used with:

```cpp
os::clock_gettime()
```

Type:

```cpp
os::clock_id
```

***

### `os::CLOCK_REALTIME_ID`

Wall-clock time.

```cpp
constexpr clock_id CLOCK_REALTIME_ID{0};
```

This represents real calendar time, expressed as seconds and nanoseconds since the Unix epoch.

Example:

```cpp
os::timespec_t ts;

if (os::clock_gettime(os::CLOCK_REALTIME_ID, &ts) == 0) {
    // ts contains wall-clock time
}
```

***

### `os::CLOCK_MONOTONIC_ID`

Monotonic time.

```cpp
constexpr clock_id CLOCK_MONOTONIC_ID{1};
```

This represents a monotonic clock suitable for measuring elapsed time.

Example:

```cpp
os::timespec_t start;
os::timespec_t end;

os::clock_gettime(os::CLOCK_MONOTONIC_ID, &start);
// do work
os::clock_gettime(os::CLOCK_MONOTONIC_ID, &end);
```

***

# Data Types

## `os::ssize_t`

Signed byte-count type.

```cpp
#ifdef _WIN32
using ssize_t = intptr_t;
#else
using ssize_t = ::ssize_t;
#endif
```

Used by:

```cpp
os::read()
os::write()
```

A non-negative return value indicates the number of bytes read or written.

A negative value indicates failure.

***

## `os::flag_value<Tag>`

Strong integer wrapper used for flag constants.

```cpp
template <typename Tag>
struct flag_value {
    int value;

    constexpr explicit flag_value(int v = 0);
    constexpr explicit operator int() const;
};
```

This type prevents accidental mixing of unrelated flag domains while still allowing bitwise operations.

For example, this is valid:

```cpp
os::open_flags flags = os::O_CREAT | os::O_WRONLY | os::O_TRUNC;
```

But this should not type-check cleanly:

```cpp
os::open("file.txt", os::R_OK);
```

because `os::R_OK` is an `os::access_mode`, not an `os::open_flags`.

***

## Flag Bitwise Operators

The following bitwise operators are defined for `flag_value<Tag>`.

### `operator|`

```cpp
template <typename Tag>
constexpr flag_value<Tag> operator|(
    flag_value<Tag> a,
    flag_value<Tag> b
);
```

Combines two flags of the same flag type.

Example:

```cpp
auto flags = os::O_WRONLY | os::O_CREAT;
```

***

### `operator&`

```cpp
template <typename Tag>
constexpr flag_value<Tag> operator&(
    flag_value<Tag> a,
    flag_value<Tag> b
);
```

Computes the bitwise intersection of two flags of the same flag type.

***

### `operator~`

```cpp
template <typename Tag>
constexpr flag_value<Tag> operator~(
    flag_value<Tag> a
);
```

Computes the bitwise complement of a flag value.

***

### `operator|=`

```cpp
template <typename Tag>
constexpr flag_value<Tag>& operator|=(
    flag_value<Tag>& a,
    flag_value<Tag> b
);
```

Adds flags to an existing flag value.

Example:

```cpp
os::open_flags flags = os::O_WRONLY;
flags |= os::O_CREAT;
```

***

### `operator&=`

```cpp
template <typename Tag>
constexpr flag_value<Tag>& operator&=(
    flag_value<Tag>& a,
    flag_value<Tag> b
);
```

Applies a bitwise mask to an existing flag value.

***

## Flag Domain Types

These are internal tag types used to distinguish different classes of flags.

```cpp
struct access_mode_tag;
struct open_flags_tag;
struct seek_origin_tag;
struct clock_id_tag;
```

Users generally do not need to use these directly.

***

## `os::access_mode`

Flag type used by `os::access()`.

```cpp
using access_mode = flag_value<access_mode_tag>;
```

Associated constants:

```cpp
os::F_OK
os::R_OK
os::W_OK
```

***

## `os::open_flags`

Flag type used by `os::open()`.

```cpp
using open_flags = flag_value<open_flags_tag>;
```

Associated constants:

```cpp
os::O_RDONLY
os::O_WRONLY
os::O_RDWR
os::O_APPEND
os::O_CREAT
os::O_TRUNC
os::O_EXCL
os::O_BINARY
os::O_TEXT
```

***

## `os::seek_origin`

Flag type used by `os::lseek()`.

```cpp
using seek_origin = flag_value<seek_origin_tag>;
```

Associated constants:

```cpp
os::SEEK_SET
os::SEEK_CUR
os::SEEK_END
```

***

## `os::clock_id`

Clock identifier type used by `os::clock_gettime()`.

```cpp
using clock_id = flag_value<clock_id_tag>;
```

Associated constants:

```cpp
os::CLOCK_REALTIME_ID
os::CLOCK_MONOTONIC_ID
```

***

## `os::stat_t`

Portable file metadata structure.

```cpp
struct stat_t {
    uint32_t mode;
    int64_t  size;
    int64_t  atime_sec;
    int64_t  mtime_sec;
};
```

This is intentionally smaller than POSIX `struct stat`.

### Fields

#### `mode`

```cpp
uint32_t mode;
```

File type and basic permission bits.

On POSIX, this is derived from `struct stat::st_mode`.

On Windows, this is derived from the CRT `__stat64` mode field.

***

#### `size`

```cpp
int64_t size;
```

File size in bytes.

***

#### `atime_sec`

```cpp
int64_t atime_sec;
```

Last access time, in seconds since the Unix epoch.

***

#### `mtime_sec`

```cpp
int64_t mtime_sec;
```

Last modification time, in seconds since the Unix epoch.

***

### Intentionally Excluded Metadata

`os::stat_t` intentionally excludes:

* user ID
* group ID
* inode number
* device ID
* hard-link count
* status-change time
* Windows creation time

These fields are excluded because they do not form a clean portable primitive across POSIX and Windows.

***

## `os::timespec_t`

Portable time specification.

```cpp
struct timespec_t {
    int64_t tv_sec;
    long    tv_nsec;
};
```

### Fields

#### `tv_sec`

```cpp
int64_t tv_sec;
```

Seconds component.

***

#### `tv_nsec`

```cpp
long tv_nsec;
```

Nanoseconds component.

Valid range:

```cpp
0 <= tv_nsec < 1000000000
```

***

## `os::dirent`

Portable directory entry.

```cpp
struct dirent {
    char d_name[4096];
};
```

### `d_name`

```cpp
char d_name[4096];
```

Null-terminated directory entry name.

Only the entry name is exposed. File type information is intentionally omitted because directory-entry type data is not equally reliable or portable across all target systems.

***

## `os::DIR`

Opaque directory stream type.

```cpp
struct DIR;
```

Used by:

```cpp
os::opendir()
os::readdir()
os::closedir()
```

The actual platform-specific layout is private to `FrankoModule_os.cpp`.

***

# Functions

## File Descriptor I/O

***

## `os::read`

Read bytes from a file descriptor.

```cpp
os::ssize_t read(int fd, void* buf, size_t count);
```

### Parameters

#### `fd`

File descriptor to read from.

#### `buf`

Destination buffer.

#### `count`

Maximum number of bytes to read.

### Returns

Returns the number of bytes read.

Returns `0` on end-of-file.

Returns a negative value on failure.

### Example

```cpp
char buf[1024];

os::ssize_t n = os::read(fd, buf, sizeof(buf));

if (n < 0) {
    std::perror("os::read");
}
```

***

## `os::write`

Write bytes to a file descriptor.

```cpp
os::ssize_t write(int fd, const void* buf, size_t count);
```

### Parameters

#### `fd`

File descriptor to write to.

#### `buf`

Source buffer.

#### `count`

Number of bytes to write.

### Returns

Returns the number of bytes written.

Returns a negative value on failure.

### Notes

A successful write may write fewer bytes than requested. Callers that need to write an entire buffer should loop until all bytes are written or an error occurs.

### Example

```cpp
const char msg[] = "hello\n";

os::ssize_t n = os::write(fd, msg, sizeof(msg) - 1);

if (n < 0) {
    std::perror("os::write");
}
```

***

## `os::close`

Close a file descriptor.

```cpp
int close(int fd);
```

### Parameters

#### `fd`

File descriptor to close.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::close(fd) != 0) {
    std::perror("os::close");
}
```

***

## File Operations

***

## `os::open`

Open or create a file.

```cpp
int open(const char* path, open_flags oflag, int mode = 0666);
```

### Parameters

#### `path`

Path to the file.

On Windows, this string is interpreted as UTF-8 and converted to UTF-16 internally.

#### `oflag`

Open flags.

Common examples:

```cpp
os::O_RDONLY
os::O_WRONLY | os::O_CREAT | os::O_TRUNC
os::O_RDWR | os::O_CREAT | os::O_BINARY
```

#### `mode`

Permission mode used when creating a new file.

This is relevant when `oflag` includes:

```cpp
os::O_CREAT
```

Default:

```cpp
0666
```

### Returns

Returns a non-negative file descriptor on success.

Returns `-1` on failure.

### Example

```cpp
int fd = os::open(
    "output.txt",
    os::O_WRONLY | os::O_CREAT | os::O_TRUNC,
    0644
);

if (fd < 0) {
    std::perror("os::open");
}
```

***

## `os::lseek`

Reposition a file descriptor offset.

```cpp
int64_t lseek(int fd, int64_t offset, seek_origin origin);
```

### Parameters

#### `fd`

File descriptor.

#### `offset`

Offset in bytes.

#### `origin`

Seek origin.

One of:

```cpp
os::SEEK_SET
os::SEEK_CUR
os::SEEK_END
```

### Returns

Returns the resulting file offset on success.

Returns `-1` on failure.

### Example

```cpp
if (os::lseek(fd, 0, os::SEEK_SET) < 0) {
    std::perror("os::lseek");
}
```

***

## `os::unlink`

Remove a filesystem entry.

```cpp
int unlink(const char* path);
```

### Parameters

#### `path`

Path to remove.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::unlink("old.txt") != 0) {
    std::perror("os::unlink");
}
```

***

## `os::rename`

Rename or move a filesystem entry.

```cpp
int rename(const char* old_path, const char* new_path);
```

### Parameters

#### `old_path`

Existing path.

#### `new_path`

New path.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Notes

On Windows, this implementation uses replacement behavior for the destination path where supported.

### Example

```cpp
if (os::rename("old.txt", "new.txt") != 0) {
    std::perror("os::rename");
}
```

***

## File Metadata

***

## `os::stat`

Get metadata for a path.

```cpp
int stat(const char* path, os::stat_t* out);
```

### Parameters

#### `path`

Path to inspect.

#### `out`

Pointer to an `os::stat_t` structure that receives the metadata.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
os::stat_t st;

if (os::stat("file.txt", &st) == 0) {
    std::printf("size = %lld\n", static_cast<long long>(st.size));
}
```

***

## `os::fstat`

Get metadata for an open file descriptor.

```cpp
int fstat(int fd, os::stat_t* out);
```

### Parameters

#### `fd`

Open file descriptor.

#### `out`

Pointer to an `os::stat_t` structure that receives the metadata.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
os::stat_t st;

if (os::fstat(fd, &st) == 0) {
    std::printf("size = %lld\n", static_cast<long long>(st.size));
}
```

***

## `os::access`

Check path accessibility.

```cpp
int access(const char* path, access_mode mode);
```

### Parameters

#### `path`

Path to check.

#### `mode`

Access mode.

Supported values:

```cpp
os::F_OK
os::R_OK
os::W_OK
os::R_OK | os::W_OK
```

### Returns

Returns `0` if the requested access check succeeds.

Returns `-1` otherwise.

### Example

```cpp
if (os::access("file.txt", os::F_OK) == 0) {
    // file exists
}
```

***

## Working Directory

***

## `os::getcwd`

Get the current working directory.

```cpp
char* getcwd(char* buf, size_t maxlen);
```

### Parameters

#### `buf`

Destination buffer.

If `buf` is not `nullptr`, the current directory is written into it.

If `buf` is `nullptr`, the function allocates a new buffer using `malloc()`.

#### `maxlen`

Size of `buf`, in bytes.

When `buf == nullptr`, this argument is ignored by the Windows implementation and follows the platform behavior on POSIX.

### Returns

Returns a pointer to the buffer containing the current directory.

Returns `nullptr` on failure.

### Ownership

If `buf == nullptr`, the returned pointer must be freed with:

```cpp
std::free(ptr);
```

### Example

Caller-provided buffer:

```cpp
char cwd[4096];

if (os::getcwd(cwd, sizeof(cwd))) {
    std::printf("cwd = %s\n", cwd);
}
```

Allocated buffer:

```cpp
char* cwd = os::getcwd(nullptr, 0);

if (cwd) {
    std::printf("cwd = %s\n", cwd);
    std::free(cwd);
}
```

***

## `os::chdir`

Change the current working directory.

```cpp
int chdir(const char* path);
```

### Parameters

#### `path`

Directory to switch to.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::chdir("subdir") != 0) {
    std::perror("os::chdir");
}
```

***

## Directory Creation and Removal

***

## `os::mkdir`

Create a directory.

```cpp
int mkdir(const char* path);
```

### Parameters

#### `path`

Directory path to create.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Notes

On POSIX systems, the directory is created using mode:

```cpp
0777
```

subject to the process umask.

On Windows, the path is converted from UTF-8 to UTF-16 and created using the wide CRT directory API.

### Example

```cpp
if (os::mkdir("build") != 0) {
    std::perror("os::mkdir");
}
```

***

## `os::rmdir`

Remove an empty directory.

```cpp
int rmdir(const char* path);
```

### Parameters

#### `path`

Directory path to remove.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::rmdir("build") != 0) {
    std::perror("os::rmdir");
}
```

***

## Directory Iteration

***

## `os::opendir`

Open a directory stream.

```cpp
DIR* opendir(const char* path);
```

### Parameters

#### `path`

Directory path.

### Returns

Returns a pointer to an `os::DIR` directory stream on success.

Returns `nullptr` on failure.

### Example

```cpp
os::DIR* dir = os::opendir(".");

if (!dir) {
    std::perror("os::opendir");
}
```

***

## `os::readdir`

Read the next directory entry.

```cpp
os::dirent* readdir(DIR* dir);
```

### Parameters

#### `dir`

Directory stream returned by `os::opendir()`.

### Returns

Returns a pointer to an internal `os::dirent` object on success.

Returns `nullptr` at end of directory or on failure.

### Notes

The returned pointer is owned by the directory stream and remains valid only until the next call to `os::readdir()` on the same stream or until `os::closedir()` is called.

To distinguish end-of-directory from error, set `errno = 0` before calling `os::readdir()`.

Example:

```cpp
errno = 0;
os::dirent* ent = os::readdir(dir);

if (!ent) {
    if (errno != 0) {
        std::perror("os::readdir");
    } else {
        // end of directory
    }
}
```

***

## `os::closedir`

Close a directory stream.

```cpp
int closedir(DIR* dir);
```

### Parameters

#### `dir`

Directory stream returned by `os::opendir()`.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::closedir(dir) != 0) {
    std::perror("os::closedir");
}
```

***

## Process and Terminal Helpers

***

## `os::getpid`

Get the current process ID.

```cpp
int getpid();
```

### Returns

Returns the current process ID.

### Example

```cpp
int pid = os::getpid();
```

***

## `os::isatty`

Check whether a file descriptor refers to a terminal.

```cpp
int isatty(int fd);
```

### Parameters

#### `fd`

File descriptor to check.

### Returns

Returns nonzero if the file descriptor refers to a terminal.

Returns `0` otherwise.

### Notes

The exact nonzero value is platform-dependent. For example, some Windows CRTs may return a value other than `1`.

Use it as a boolean test:

```cpp
if (os::isatty(os::STDOUT_FILENO)) {
    // stdout is a terminal
}
```

***

## File Descriptor Duplication and Pipes

***

## `os::dup`

Duplicate a file descriptor.

```cpp
int dup(int fd);
```

### Parameters

#### `fd`

File descriptor to duplicate.

### Returns

Returns the new file descriptor on success.

Returns `-1` on failure.

### Example

```cpp
int copy = os::dup(os::STDOUT_FILENO);

if (copy >= 0) {
    os::write(copy, "hello\n", 6);
    os::close(copy);
}
```

***

## `os::dup2`

Duplicate one file descriptor onto another.

```cpp
int dup2(int fd1, int fd2);
```

### Parameters

#### `fd1`

Source file descriptor.

#### `fd2`

Destination file descriptor.

### Returns

Returns `fd2` or a non-negative value on success, depending on platform behavior.

Returns `-1` on failure.

### Example

```cpp
if (os::dup2(fd_a, fd_b) < 0) {
    std::perror("os::dup2");
}
```

***

## `os::pipe`

Create a pipe.

```cpp
int pipe(int fds[2]);
```

### Parameters

#### `fds`

Array that receives two file descriptors:

```cpp
fds[0] // read end
fds[1] // write end
```

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Notes

On Windows, the pipe is created in binary mode.

### Example

```cpp
int fds[2];

if (os::pipe(fds) == 0) {
    os::write(fds[1], "hello", 5);

    char buf[16] = {};
    os::read(fds[0], buf, sizeof(buf));

    os::close(fds[0]);
    os::close(fds[1]);
}
```

***

## File Synchronization and Truncation

***

## `os::fsync`

Synchronize file contents to storage.

```cpp
int fsync(int fd);
```

### Parameters

#### `fd`

File descriptor to synchronize.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Platform Notes

On POSIX systems, this maps to `fsync()`.

On macOS, the implementation first attempts `F_FULLFSYNC` using `fcntl()` and falls back to `fsync()` if needed.

On Windows, this maps to `_commit()`.

### Example

```cpp
if (os::fsync(fd) != 0) {
    std::perror("os::fsync");
}
```

***

## `os::ftruncate`

Resize an open file.

```cpp
int ftruncate(int fd, int64_t length);
```

### Parameters

#### `fd`

Open file descriptor.

#### `length`

New file length in bytes.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Notes

If `length < 0`, the function fails with `errno = EINVAL`.

### Example

```cpp
if (os::ftruncate(fd, 1024) != 0) {
    std::perror("os::ftruncate");
}
```

***

## Time Functions

***

## `os::nanosleep`

Sleep for a specified duration.

```cpp
int nanosleep(int64_t seconds, int64_t nanoseconds);
```

### Parameters

#### `seconds`

Whole seconds to sleep.

#### `nanoseconds`

Additional nanoseconds to sleep.

Must satisfy:

```cpp
0 <= nanoseconds < 1000000000
```

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Notes

If interrupted on POSIX systems, this implementation retries until the full duration has elapsed or a non-interruption error occurs.

On Windows, the duration is rounded up to milliseconds and implemented with `Sleep()`.

### Example

```cpp
os::nanosleep(0, 1000000); // approximately 1 ms
```

***

## `os::clock_gettime`

Get time from a portable clock.

```cpp
int clock_gettime(clock_id clock_id_value, os::timespec_t* ts);
```

### Parameters

#### `clock_id_value`

Clock to query.

Supported values:

```cpp
os::CLOCK_REALTIME_ID
os::CLOCK_MONOTONIC_ID
```

#### `ts`

Output pointer that receives the time value.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Platform Notes

On POSIX systems:

* `os::CLOCK_REALTIME_ID` maps to native `CLOCK_REALTIME`
* `os::CLOCK_MONOTONIC_ID` maps to native `CLOCK_MONOTONIC`

On Windows:

* `os::CLOCK_REALTIME_ID` uses `GetSystemTimePreciseAsFileTime()` when available, otherwise `GetSystemTimeAsFileTime()`
* `os::CLOCK_MONOTONIC_ID` uses `QueryPerformanceCounter()`

### Example

```cpp
os::timespec_t ts;

if (os::clock_gettime(os::CLOCK_MONOTONIC_ID, &ts) == 0) {
    std::printf(
        "monotonic = %lld.%09ld\n",
        static_cast<long long>(ts.tv_sec),
        ts.tv_nsec
    );
}
```

***

## Process Termination

***

## `os::abort`

Abort the current process.

```cpp
[[noreturn]] void abort();
```

### Behavior

Terminates the process abnormally.

This function does not return.

### Example

```cpp
os::abort();
```

***

## `os::exit`

Exit the current process with a status code.

```cpp
[[noreturn]] void exit(int status);
```

### Parameters

#### `status`

Process exit status.

### Behavior

Terminates the process normally with the given status.

This function does not return.

### Example

```cpp
os::exit(0);
```

***

## System Command Execution

***

## `os::system`

Execute a command using the host command processor.

```cpp
int system(const char* command);
```

### Parameters

#### `command`

Command string to execute.

If `command == nullptr`, the function probes whether a command processor is available.

### Returns

Returns the platform-specific command processor result.

### Notes

Return encoding differs between POSIX and Windows.

For example, on POSIX, the returned value may encode the child process exit status in wait-status format. On Windows, CRT behavior may return the command’s exit code more directly.

Do not assume that `os::system("command") == 7` means the same thing on every platform.

### Windows Encoding

On Windows, non-null commands are interpreted as UTF-8 and converted to UTF-16 before being passed to `_wsystem()`.

### Example

```cpp
int r = os::system("echo hello from os::system");
```

***

## Environment Variables

***

## `os::getenv`

Get an environment variable.

```cpp
bool getenv(const char* name, std::string& out_value);
```

### Parameters

#### `name`

Environment variable name.

#### `out_value`

Receives the variable value if found.

### Returns

Returns `true` if the variable exists.

Returns `false` if the variable does not exist or if the input is invalid.

### Example

```cpp
std::string value;

if (os::getenv("PATH", value)) {
    std::printf("PATH = %s\n", value.c_str());
}
```

***

## `os::setenv`

Set an environment variable.

```cpp
int setenv(const char* name, const char* value, int overwrite);
```

### Parameters

#### `name`

Environment variable name.

Must not be null, empty, or contain `=`.

#### `value`

Environment variable value.

Must not be null.

#### `overwrite`

If zero and the variable already exists, the existing value is preserved.

If nonzero, any existing value is replaced.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::setenv("FRANKO_MODE", "debug", 1) != 0) {
    std::perror("os::setenv");
}
```

***

## `os::unsetenv`

Remove an environment variable.

```cpp
int unsetenv(const char* name);
```

### Parameters

#### `name`

Environment variable name.

Must not be null, empty, or contain `=`.

### Returns

Returns `0` on success.

Returns `-1` on failure.

### Example

```cpp
if (os::unsetenv("FRANKO_MODE") != 0) {
    std::perror("os::unsetenv");
}
```

***

# Complete Minimal Example

```cpp
#include "FrankoModule_os.hpp"

#include <cstdio>
#include <cstring>
#include <cerrno>

int main() {
    int fd = os::open(
        "hello.txt",
        os::O_WRONLY | os::O_CREAT | os::O_TRUNC | os::O_BINARY,
        0644
    );

    if (fd < 0) {
        std::perror("os::open");
        return 1;
    }

    const char msg[] = "hello franko os\n";

    if (os::write(fd, msg, std::strlen(msg)) < 0) {
        std::perror("os::write");
        os::close(fd);
        return 1;
    }

    if (os::fsync(fd) != 0) {
        std::perror("os::fsync");
    }

    os::close(fd);

    os::stat_t st;

    if (os::stat("hello.txt", &st) == 0) {
        std::printf("size = %lld\n", static_cast<long long>(st.size));
    }

    os::unlink("hello.txt");

    return 0;
}
```

***

# Portability Notes

## Public constants are namespace-scoped

Use:

```cpp
os::O_RDONLY
os::SEEK_SET
os::R_OK
```

Do not use:

```cpp
O_RDONLY
SEEK_SET
R_OK
```

Franko OS intentionally avoids relying on global POSIX or CRT macros in public user code.

***

## File descriptors are plain `int`

The API is POSIX-shaped, so file descriptors are represented as:

```cpp
int
```

This applies on both POSIX and Windows.

***

## Directory entries contain names only

`os::dirent` exposes only:

```cpp
char d_name[4096];
```

It does not expose file type, inode number, or other non-portable directory metadata.

If you need metadata for a directory entry, construct the full path and call:

```cpp
os::stat()
```

***

## `os::system()` return values are platform-dependent

Do not rely on identical integer return values from `os::system()` across POSIX and Windows.

For example, a child process exiting with status `7` may produce different raw return values depending on the platform and C runtime.

***

## `os::O_BINARY` and `os::O_TEXT`

These flags matter on Windows.

They have no effect on POSIX systems.

For portable binary file handling, explicitly use:

```cpp
os::O_BINARY
```

when opening files that should not undergo text translation on Windows.

***

# API Summary

## Constants

Standard descriptors:

```cpp
os::STDIN_FILENO
os::STDOUT_FILENO
os::STDERR_FILENO
```

Access modes:

```cpp
os::F_OK
os::R_OK
os::W_OK
```

Open flags:

```cpp
os::O_RDONLY
os::O_WRONLY
os::O_RDWR
os::O_APPEND
os::O_CREAT
os::O_TRUNC
os::O_EXCL
os::O_BINARY
os::O_TEXT
```

Seek origins:

```cpp
os::SEEK_SET
os::SEEK_CUR
os::SEEK_END
```

Clock identifiers:

```cpp
os::CLOCK_REALTIME_ID
os::CLOCK_MONOTONIC_ID
```

***

## Types

```cpp
os::ssize_t
os::flag_value<Tag>
os::access_mode
os::open_flags
os::seek_origin
os::clock_id
os::stat_t
os::timespec_t
os::dirent
os::DIR
```

***

## Functions

File I/O:

```cpp
os::ssize_t os::read(int fd, void* buf, size_t count);
os::ssize_t os::write(int fd, const void* buf, size_t count);
int os::close(int fd);
```

File operations:

```cpp
int os::open(const char* path, os::open_flags oflag, int mode = 0666);
int64_t os::lseek(int fd, int64_t offset, os::seek_origin origin);
int os::unlink(const char* path);
int os::rename(const char* old_path, const char* new_path);
```

Metadata and access:

```cpp
int os::stat(const char* path, os::stat_t* out);
int os::fstat(int fd, os::stat_t* out);
int os::access(const char* path, os::access_mode mode);
```

Working directory and directories:

```cpp
char* os::getcwd(char* buf, size_t maxlen);
int os::chdir(const char* path);
int os::mkdir(const char* path);
int os::rmdir(const char* path);
os::DIR* os::opendir(const char* path);
os::dirent* os::readdir(os::DIR* dir);
int os::closedir(os::DIR* dir);
```

Process and descriptors:

```cpp
int os::getpid();
int os::isatty(int fd);
int os::dup(int fd);
int os::dup2(int fd1, int fd2);
int os::pipe(int fds[2]);
```

Synchronization and sizing:

```cpp
int os::fsync(int fd);
int os::ftruncate(int fd, int64_t length);
```

Time:

```cpp
int os::nanosleep(int64_t seconds, int64_t nanoseconds);
int os::clock_gettime(os::clock_id clock_id_value, os::timespec_t* ts);
```

Process termination and command execution:

```cpp
[[noreturn]] void os::abort();
[[noreturn]] void os::exit(int status);
int os::system(const char* command);
```

Environment:

```cpp
bool os::getenv(const char* name, std::string& out_value);
int os::setenv(const char* name, const char* value, int overwrite);
int os::unsetenv(const char* name);
```

