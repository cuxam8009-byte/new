#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

// Hàm khởi động Node.js do libnode.so (nodejs-mobile) cung cấp
namespace node {
int Start(int argc, char* argv[]);
}

#define TAG "McBotNode"

static int g_pipe[2];
static bool g_redirected = false;

static void* logPump(void*) {
  char buf[1024];
  ssize_t n;
  while ((n = read(g_pipe[0], buf, sizeof(buf) - 1)) > 0) {
    if (buf[n - 1] == '\n') --n;
    buf[n] = 0;
    __android_log_write(ANDROID_LOG_INFO, TAG, buf);
  }
  return nullptr;
}

// Đưa stdout/stderr của Node vào logcat
static void redirectStdio() {
  if (g_redirected) return;
  g_redirected = true;
  setvbuf(stdout, nullptr, _IONBF, 0);
  setvbuf(stderr, nullptr, _IONBF, 0);
  if (pipe(g_pipe) != 0) return;
  dup2(g_pipe[1], 1);
  dup2(g_pipe[1], 2);
  pthread_t t;
  pthread_create(&t, nullptr, logPump, nullptr);
  pthread_detach(t);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mcbot_manager_NodeBridge_startNode(JNIEnv* env, jobject /*thiz*/, jobjectArray args) {
  redirectStdio();

  jsize argc = env->GetArrayLength(args);
  std::vector<std::string> strs;
  size_t total = 0;
  for (jsize i = 0; i < argc; i++) {
    jstring js = (jstring)env->GetObjectArrayElement(args, i);
    const char* c = env->GetStringUTFChars(js, nullptr);
    strs.emplace_back(c);
    env->ReleaseStringUTFChars(js, c);
    env->DeleteLocalRef(js);
    total += strs.back().size() + 1;
  }

  // libuv yêu cầu argv nằm liền một khối bộ nhớ -> cố ý không free
  char* buffer = (char*)calloc(total + 1, 1);
  std::vector<char*> argv(argc + 1, nullptr);
  char* cur = buffer;
  for (jsize i = 0; i < argc; i++) {
    memcpy(cur, strs[i].c_str(), strs[i].size() + 1);
    argv[i] = cur;
    cur += strs[i].size() + 1;
  }
  return (jint)node::Start((int)argc, argv.data());
}
