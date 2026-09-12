cmake_minimum_required(VERSION 3.22.1)
project(meridian_aubio)

file(GLOB_RECURSE AUBIO_SRC CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/aubio/src/*.c")

list(FILTER AUBIO_SRC EXCLUDE REGEX ".*/io/.*")
list(FILTER AUBIO_SRC EXCLUDE REGEX ".*/synth/.*")
list(FILTER AUBIO_SRC EXCLUDE REGEX ".*/notes/.*")
list(FILTER AUBIO_SRC EXCLUDE REGEX "dct_fftw\\.c$")
list(FILTER AUBIO_SRC EXCLUDE REGEX "dct_accelerate\\.c$")
list(FILTER AUBIO_SRC EXCLUDE REGEX "dct_ipp\\.c$")
list(FILTER AUBIO_SRC EXCLUDE REGEX "resampler\\.c$")
list(FILTER AUBIO_SRC EXCLUDE REGEX "windll\\.c$")
list(FILTER AUBIO_SRC EXCLUDE REGEX "audio_unit\\.c$")

add_library(meridian_aubio SHARED
    aubio_jni.c
    ${AUBIO_SRC}
)

target_include_directories(meridian_aubio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/aubio/src
)

target_compile_definitions(meridian_aubio PRIVATE
    HAVE_CONFIG_H=1
    HAVE_STDLIB_H=1
    HAVE_STDIO_H=1
    HAVE_MATH_H=1
    HAVE_STRING_H=1
    HAVE_ERRNO_H=1
    HAVE_LIMITS_H=1
    HAVE_STDARG_H=1
    HAVE_C99_VARARGS_MACROS=1
    HAVE_AUBIO_DOUBLE=0
)

target_compile_options(meridian_aubio PRIVATE -O2 -fPIC -std=c11 -Wno-unused-parameter)

find_library(log-lib log)
target_link_libraries(meridian_aubio ${log-lib} m)
