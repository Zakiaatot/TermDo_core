//
// Created by Code01 on 2023/1/13.
//
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <sys/ioctl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>
#include <pty.h>
#include <jni.h>
#include <signal.h>
#include <dirent.h>

//debug tools
//#include <android/log.h>
//#define TAG    "TermDo-c" // 这个是自定义的LOG的标识
//#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,TAG,__VA_ARGS__) // 定义LOGD类型


/*define*/
#define BUFSIZE 1025
#define NAMESIZE 64

/*tools*/
int throw_runtime_exception(JNIEnv *env, char const *message) {
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    (*env)->ThrowNew(env, exClass, message);
    return -1;
}

jstring char_to_jstring(JNIEnv *env, const char *pat) {
    jclass strClass = (*env)->FindClass(env, "java/lang/String");
    jmethodID ctorID = (*env)->GetMethodID(env, strClass, "<init>", "([BLjava/lang/String;)V");
    jbyteArray bytes = (*env)->NewByteArray(env, strlen(pat));
    (*env)->SetByteArrayRegion(env, bytes, 0, strlen(pat), (jbyte *) pat);
    jstring encoding = (*env)->NewStringUTF(env, "utf-8");
    return (jstring)(*env)->NewObject(env, strClass, ctorID, bytes, encoding);
}

/*core function*/
int ptym_open(char *pts_name, int pts_namesz) {
    char *ptr;
    int fdm;
    //曲线救国：保证pts_namesz>strlen("/dev/ptmx")
    strncpy(pts_name, "/dev/ptmx", pts_namesz);
    pts_name[pts_namesz - 1] = '\0';

    if ((fdm = open(pts_name, O_RDWR | O_RDWR)) < 0)
        return -1;
    if (grantpt(fdm) < 0 ||
        unlockpt(fdm) < 0 ||
        (ptr = ptsname(fdm)) == NULL
            ) {
        close(fdm);
        return -1;
    }

    strncpy(pts_name, ptr, pts_namesz);
    pts_name[pts_namesz - 1] = '\0';
    return fdm;
}

int ptys_open(char *pts_name) {
    int fds;
    if ((fds = open(pts_name, O_RDWR)) < 0)
        return -1;
    return fds;
}

pid_t pty_fork(int *ptrfdm, char *slave_name, int slave_namesz,
               unsigned short rows, unsigned short columns) {
    int fdm, fds;
    pid_t pid;
    char pts_name[NAMESIZE];
    if ((fdm = ptym_open(pts_name, sizeof pts_name)) < 0)
        return -1;
    if (slave_name != NULL) {
        strncpy(slave_name, pts_name, slave_namesz);
        slave_name[slave_namesz - 1] = '\0';
    }

    //fdm termios
    struct termios tios;
    tcgetattr(fdm, &tios);
    tios.c_iflag |= IUTF8;
    tios.c_iflag &= ~(IXON | IXOFF);
    tcsetattr(fdm, TCSANOW, &tios);
    //fdm winsize
    struct winsize sz = {.ws_row = rows, .ws_col = columns};
    ioctl(fdm, TIOCSWINSZ, &sz);

    pid = fork();
    if (pid < 0) {
        return -1;
    } else if (pid == 0) {//child
        // Clear signals which the Android java process may have blocked:
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

        if (setsid() < 0 || (fds = ptys_open(pts_name)) < 0)
            return -1;//setsid error //can't open slave pty
        close(fdm);

        dup2(fds, STDIN_FILENO);
        dup2(fds, STDOUT_FILENO);
        dup2(fds, STDERR_FILENO);
        return 0;
    } else {
        *ptrfdm = fdm;
        return pid;
    }

}

int creat_term(JNIEnv *env, char const *cmd, jint rows, jint columns) {
    int fdm;
    char slave_name[NAMESIZE];
    pid_t pid = pty_fork(&fdm,
                         slave_name,
                         NAMESIZE,
                         (unsigned short) rows,
                         (unsigned short) columns);

    if (pid == 0) {
        DIR *self_dir = opendir("/proc/self/fd");
        if (self_dir != NULL) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent *entry;
            while ((entry = readdir(self_dir)) != NULL) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }
        execvp(cmd, NULL);
        return throw_runtime_exception(env, "out");
    } else if (pid > 0) {
        return fdm;
    } else {
        return throw_runtime_exception(env, "pty fork failed");
    }
}

int close_term(jint fdm) {
    return close(fdm);
}


/*export*/
JNIEXPORT jint

JNICALL
Java_com_termdo_core_JNI_createTerm(JNIEnv *env, jclass clazz, jstring cmd, jint rows,
                                    jint columns) {
    // TODO: implement createTerm()
    char const *cmd_utf8 = (*env)->GetStringUTFChars(env, cmd, NULL);
    int result = creat_term(env, cmd_utf8, rows, columns);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
    return result;
}

JNIEXPORT jint

JNICALL
Java_com_termdo_core_JNI_closeTerm(JNIEnv *env, jobject thiz, jint fdm) {
    return close_term(fdm);
}

JNIEXPORT jint

JNICALL
Java_com_termdo_core_JNI_write(JNIEnv *env, jclass clazz, jint fdm, jstring data) {
    char const *data_buf = (*env)->GetStringUTFChars(env, data, NULL);
    int result = write(fdm, data_buf, strlen(data_buf));
    (*env)->ReleaseStringUTFChars(env, data, data_buf);
    return result;
}

JNIEXPORT jstring

JNICALL
Java_com_termdo_core_JNI_read(JNIEnv *env, jclass clazz, jint fdm) {
    // TODO: implement read()
    char buf[BUFSIZE];
    int length = read(fdm, buf, BUFSIZE - 1);
    if (length == -1) return char_to_jstring(env, '\0');
    buf[length] = '\0';
    return char_to_jstring(env, buf);
}


JNIEXPORT jint

JNICALL
Java_com_termdo_core_JNI_changeSize(JNIEnv *env, jclass clazz, jint fdm, jint rows, jint columns) {
    // TODO: implement changeSize()
    struct winsize sz = {.ws_row = (unsigned short) rows, .ws_col =(unsigned short) columns};
    return ioctl(fdm, TIOCSWINSZ, &sz);
}