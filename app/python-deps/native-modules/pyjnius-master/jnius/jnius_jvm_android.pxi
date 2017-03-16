# on android, rely on SDL to get the JNI env
cdef extern JNIEnv *EPC_AndroidGetJNIEnv()

cdef JNIEnv *get_platform_jnienv():
    return <JNIEnv*>EPC_AndroidGetJNIEnv()
