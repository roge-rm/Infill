/*
 * The platform part of the JNI headers, for building the Windows engine on
 * Linux. jni.h is the same everywhere, but the cross compiler only has the
 * Linux JDK. These match Windows' own jni_md.h, using the types this code
 * already uses (jint is 32 bits and jlong 64 either way).
 */
#ifndef ACIDULOUS_WIN32_JNI_MD_H
#define ACIDULOUS_WIN32_JNI_MD_H

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall

typedef int jint;
typedef long long jlong;
typedef signed char jbyte;

#endif
