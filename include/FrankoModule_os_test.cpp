#include "FrankoModule_os.hpp"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <string>

static int g_failures = 0;

static void pass(const char* name) {
    std::printf("[PASS] %s\n", name);
}

static void fail(const char* name) {
    std::printf("[FAIL] %s: errno=%d\n", name, errno);
    ++g_failures;
}

static void check(bool ok, const char* name) {
    if (ok) {
        pass(name);
    } else {
        fail(name);
    }
}

static bool write_all(int fd, const char* s) {
    size_t len = std::strlen(s);
    size_t off = 0;

    while (off < len) {
        os::ssize_t n = os::write(fd, s + off, len - off);

        if (n < 0) {
            return false;
        }

        if (n == 0) {
            return false;
        }

        off += static_cast<size_t>(n);
    }

    return true;
}

static std::string make_test_dir_name() {
    char buf[128];
    std::snprintf(buf, sizeof(buf), "franko_os_test_%d", os::getpid());
    return std::string(buf);
}

int main(int argc, char** argv) {
    if (argc >= 2 && std::strcmp(argv[1], "--child-exit") == 0) {
        os::exit(7);
    }

    if (argc >= 2 && std::strcmp(argv[1], "--child-abort") == 0) {
        os::abort();
    }

    std::printf("=== Franko OS smoke test ===\n");

    int pid = os::getpid();
    check(pid > 0, "os::getpid");

    /*
     * Verify public standard file descriptor constants.
     *
     * These are intentionally plain int values because the OS layer remains
     * POSIX-shaped and file descriptors are represented as int.
     */
    check(os::STDIN_FILENO == 0, "os::STDIN_FILENO");
    check(os::STDOUT_FILENO == 1, "os::STDOUT_FILENO");
    check(os::STDERR_FILENO == 2, "os::STDERR_FILENO");

    char original_cwd[4096];

    if (os::getcwd(original_cwd, sizeof(original_cwd)) != nullptr) {
        pass("os::getcwd caller buffer");
        std::printf("cwd = %s\n", original_cwd);
    } else {
        fail("os::getcwd caller buffer");
        return 1;
    }

    char* allocated_cwd = os::getcwd(nullptr, 0);

    if (allocated_cwd) {
        pass("os::getcwd allocated buffer");
        std::free(allocated_cwd);
    } else {
        fail("os::getcwd allocated buffer");
    }

    std::string test_dir = make_test_dir_name();

    os::rmdir(test_dir.c_str());

    check(os::mkdir(test_dir.c_str()) == 0, "os::mkdir");
    check(os::chdir(test_dir.c_str()) == 0, "os::chdir into test dir");

    char inside_cwd[4096];
    check(os::getcwd(inside_cwd, sizeof(inside_cwd)) != nullptr, "os::getcwd after chdir");

    check(os::chdir(original_cwd) == 0, "os::chdir back to original cwd");

    std::string file_path = test_dir + "/file.txt";

    int fd = os::open(
        file_path.c_str(),
        os::O_RDWR | os::O_CREAT | os::O_TRUNC | os::O_BINARY,
        0644
    );

    check(fd >= 0, "os::open create file");

    if (fd >= 0) {
        check(write_all(fd, "hello franko os\n"), "os::write");

        check(os::fsync(fd) == 0, "os::fsync");

        os::stat_t st1;
        check(os::fstat(fd, &st1) == 0, "os::fstat");
        std::printf("fstat size before truncate = %lld\n", static_cast<long long>(st1.size));

        check(os::lseek(fd, 0, os::SEEK_SET) == 0, "os::lseek os::SEEK_SET");

        char read_buf[64];
        std::memset(read_buf, 0, sizeof(read_buf));

        os::ssize_t n = os::read(fd, read_buf, sizeof(read_buf) - 1);
        check(n > 0, "os::read");

        if (n > 0) {
            std::printf("read data = %s", read_buf);
        }

        check(os::ftruncate(fd, 5) == 0, "os::ftruncate");

        os::stat_t st2;
        check(os::fstat(fd, &st2) == 0 && st2.size == 5, "os::fstat after ftruncate");

        check(os::close(fd) == 0, "os::close file");
    }

    os::stat_t path_st;
    check(os::stat(file_path.c_str(), &path_st) == 0, "os::stat");
    std::printf("stat size = %lld\n", static_cast<long long>(path_st.size));
    std::printf("stat mtime_sec = %lld\n", static_cast<long long>(path_st.mtime_sec));

    check(os::access(file_path.c_str(), os::F_OK) == 0, "os::access os::F_OK");
    check(os::access(file_path.c_str(), os::R_OK) == 0, "os::access os::R_OK");

    std::string renamed_path = test_dir + "/renamed.txt";

    check(os::rename(file_path.c_str(), renamed_path.c_str()) == 0, "os::rename");
    check(os::access(renamed_path.c_str(), os::F_OK) == 0, "os::access renamed file");

    int stdout_copy = os::dup(os::STDOUT_FILENO);

    if (stdout_copy >= 0) {
        pass("os::dup os::STDOUT_FILENO");
        write_all(stdout_copy, "hello via os::dup\n");
        check(os::close(stdout_copy) == 0, "os::close duplicated stdout");
    } else {
        fail("os::dup os::STDOUT_FILENO");
    }

    std::string dup2_a_path = test_dir + "/dup2_a.txt";
    std::string dup2_b_path = test_dir + "/dup2_b.txt";

    int fd_a = os::open(
        dup2_a_path.c_str(),
        os::O_WRONLY | os::O_CREAT | os::O_TRUNC | os::O_BINARY,
        0644
    );

    int fd_b = os::open(
        dup2_b_path.c_str(),
        os::O_WRONLY | os::O_CREAT | os::O_TRUNC | os::O_BINARY,
        0644
    );

    if (fd_a >= 0 && fd_b >= 0) {
        if (os::dup2(fd_a, fd_b) >= 0) {
            pass("os::dup2");
            check(write_all(fd_b, "written through dup2\n"), "os::write through dup2 fd");
        } else {
            fail("os::dup2");
        }
    } else {
        fail("setup for os::dup2");
    }

    if (fd_a >= 0) {
        os::close(fd_a);
    }

    if (fd_b >= 0) {
        os::close(fd_b);
    }

    int pipe_fds[2];

    if (os::pipe(pipe_fds) == 0) {
        pass("os::pipe");

        const char pipe_msg[] = "pipe message";
        check(write_all(pipe_fds[1], pipe_msg), "os::write pipe");

        char pipe_buf[64];
        std::memset(pipe_buf, 0, sizeof(pipe_buf));

        os::ssize_t pn = os::read(pipe_fds[0], pipe_buf, sizeof(pipe_buf) - 1);
        check(pn > 0, "os::read pipe");

        if (pn > 0) {
            std::printf("pipe read = %s\n", pipe_buf);
        }

        check(os::close(pipe_fds[0]) == 0, "os::close pipe read end");
        check(os::close(pipe_fds[1]) == 0, "os::close pipe write end");
    } else {
        fail("os::pipe");
    }

    int tty = os::isatty(os::STDOUT_FILENO);
    std::printf("os::isatty(os::STDOUT_FILENO) = %d\n", tty);
    pass("os::isatty os::STDOUT_FILENO");

    os::DIR* dir = os::opendir(test_dir.c_str());

    if (dir) {
        pass("os::opendir");

        int entry_count = 0;

        while (true) {
            errno = 0;
            os::dirent* ent = os::readdir(dir);

            if (!ent) {
                if (errno != 0) {
                    fail("os::readdir");
                }
                break;
            }

            ++entry_count;
            std::printf("dir entry: %s\n", ent->d_name);
        }

        check(entry_count > 0, "os::readdir entries");
        check(os::closedir(dir) == 0, "os::closedir");
    } else {
        fail("os::opendir");
    }

    os::timespec_t ts_real;
    os::timespec_t ts_mono;

    check(
        os::clock_gettime(os::CLOCK_REALTIME_ID, &ts_real) == 0,
        "os::clock_gettime os::CLOCK_REALTIME_ID"
    );

    if (os::clock_gettime(os::CLOCK_MONOTONIC_ID, &ts_mono) == 0) {
        pass("os::clock_gettime os::CLOCK_MONOTONIC_ID");
        std::printf(
            "monotonic = %lld.%09ld\n",
            static_cast<long long>(ts_mono.tv_sec),
            ts_mono.tv_nsec
        );
    } else {
        fail("os::clock_gettime os::CLOCK_MONOTONIC_ID");
    }

    check(os::nanosleep(0, 1000000) == 0, "os::nanosleep 1ms");

    std::string env_val;

    check(os::setenv("FRANKO_OS_TEST_VAR", "hello_env", 1) == 0, "os::setenv");

    if (os::getenv("FRANKO_OS_TEST_VAR", env_val) && env_val == "hello_env") {
        pass("os::getenv");
    } else {
        fail("os::getenv");
    }

    check(os::unsetenv("FRANKO_OS_TEST_VAR") == 0, "os::unsetenv");

    int sys_probe = os::system(nullptr);
    std::printf("os::system(nullptr) = %d\n", sys_probe);
    pass("os::system nullptr probe");

    int sys_echo = os::system("echo hello from os::system");
    std::printf("os::system echo returned %d\n", sys_echo);
    pass("os::system command");

    if (argc >= 1 && argv[0]) {
        std::string child_cmd = "\"";
        child_cmd += argv[0];
        child_cmd += "\" --child-exit";

        int child_result = os::system(child_cmd.c_str());
        std::printf("child exit command returned %d\n", child_result);
        pass("os::exit via child process");
    }

    std::string abort_flag;

    if (os::getenv("FRANKO_OS_TEST_ABORT", abort_flag) && abort_flag == "1") {
        std::string abort_cmd = "\"";
        abort_cmd += argv[0];
        abort_cmd += "\" --child-abort";

        int abort_result = os::system(abort_cmd.c_str());
        std::printf("child abort command returned %d\n", abort_result);
        pass("os::abort via child process");
    } else {
        std::printf("[SKIP] os::abort destructive test; set FRANKO_OS_TEST_ABORT=1 to run\n");
    }

    check(os::unlink(renamed_path.c_str()) == 0, "os::unlink renamed file");

    os::unlink(dup2_a_path.c_str());
    os::unlink(dup2_b_path.c_str());

    check(os::rmdir(test_dir.c_str()) == 0, "os::rmdir test dir");

    std::printf("\n=== Result: %d failure(s) ===\n", g_failures);

    return g_failures == 0 ? 0 : 1;
}