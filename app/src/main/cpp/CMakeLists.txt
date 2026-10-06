cmake_minimum_required(VERSION 3.22.1)
project(localmumble LANGUAGES C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
set(DISABLE_PACKAGE_CONFIG_AND_INSTALL ON CACHE BOOL "" FORCE)
set(MBEDTLS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/mbedtls)
add_subdirectory(third_party/mbedtls/library)

set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_CXX OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/libconfig)

add_library(protobuf-c STATIC
    third_party/protobuf-c/protobuf-c/protobuf-c.c
)
target_include_directories(protobuf-c PUBLIC
    third_party/protobuf-c
)
target_compile_definitions(protobuf-c PUBLIC PROTOBUF_C__NO_DEPRECATED)

set(UMURMUR_VERSION "0.5.1-android")
set(SSLIMP_VERSION "MbedTLS 3.6.7")
set(CMAKE_INSTALL_FULL_SYSCONFDIR "/data/local/tmp")
set(USE_MBEDTLS ON)
set(USE_MBEDTLS4 OFF)
set(USE_MBEDTLS_TESTCERT OFF)
set(USE_SHAREDMEMORY_API OFF)
configure_file(
    umurmur/config.h.in
    ${CMAKE_CURRENT_BINARY_DIR}/generated/config.h
)

set(UMURMUR_SOURCES
    umurmur/Mumble.pb-c.c
    umurmur/ban.c
    umurmur/channel.c
    umurmur/client.c
    umurmur/conf.c
    umurmur/crypt.c
    umurmur/log.c
    umurmur/main.c
    umurmur/memory.c
    umurmur/messagehandler.c
    umurmur/messages.c
    umurmur/pds.c
    umurmur/server.c
    umurmur/ssli_mbedtls.c
    umurmur/timer.c
    umurmur/util.c
    umurmur/voicetarget.c
)

add_executable(umurmur_server ${UMURMUR_SOURCES})
set_target_properties(umurmur_server PROPERTIES OUTPUT_NAME libumurmur_server.so)
if(SERVER_OUTPUT_DIR)
    set_target_properties(umurmur_server PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${SERVER_OUTPUT_DIR}")
endif()
target_compile_definitions(umurmur_server PRIVATE
    _GNU_SOURCE
)
target_compile_options(umurmur_server PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-deprecated-declarations
)
target_include_directories(umurmur_server PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}/generated
    umurmur
    third_party/libconfig/lib
    third_party/protobuf-c
    third_party/mbedtls/include
)
target_link_libraries(umurmur_server PRIVATE
    config
    protobuf-c
    mbedtls
    mbedx509
    mbedcrypto
)
if(ANDROID)
    target_link_options(umurmur_server PRIVATE -Wl,-z,max-page-size=16384)
endif()
