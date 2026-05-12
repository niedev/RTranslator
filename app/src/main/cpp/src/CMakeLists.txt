# Copyright 2016 Luca Martino.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.!

cmake_minimum_required(VERSION 3.1 FATAL_ERROR)
project(rtranslator_native)

add_subdirectory(libs/sentencepiece)

add_library(sentencepiece-interface SHARED SentencePieceProcessorInterface.cpp)
add_library(cache_container_native SHARED CacheContainerNative.cpp)
add_library(dictionary_translator_interface SHARED DictionaryTranslator.cpp protobuf/dictionary_data.pb.cc protobuf/dictionary_data.pb.h)

find_package(sqlite4java-android REQUIRED CONFIG)

# protobuf lite import
# FIND THREADS (This populates CMAKE_THREAD_LIBS_INIT) and force protobuf to use it
find_package(Threads REQUIRED)
add_definitions(-DHAVE_PTHREAD)
# Define paths
set(protobuf_source_dir "${CMAKE_CURRENT_SOURCE_DIR}/libs/protobuf-lite")
set(protobuf_cmake_dir "${protobuf_source_dir}/cmake")
# Protobuf Lite configuration variables
# These are required by the 'libprotobuf-lite.cmake' file
set(protobuf_VERSION "3.14.0")
set(LIB_PREFIX "")
set(protobuf_DEBUG_POSTFIX "")
set(protobuf_SHARED_OR_STATIC STATIC) # Usually best to build as static for Android JNI
# Include ONLY the lite library definition
# This file defines the 'libprotobuf-lite' target and its source list
include("${protobuf_cmake_dir}/libprotobuf-lite.cmake")


target_include_directories(dictionary_translator_interface PRIVATE
        #${CMAKE_CURRENT_SOURCE_DIR}/libs/sentencepiece/third_party/protobuf-lite
        ${CMAKE_CURRENT_SOURCE_DIR}/sqlite4java/include
)

target_link_libraries(dictionary_translator_interface
        libprotobuf-lite
        sqlite4java-android::sqlite4java
        android
        log
)

target_link_libraries(sentencepiece-interface
        sentencepiece-static-android
        android
        log
)

target_link_libraries(cache_container_native
        android
        log
)

add_subdirectory(bergamot_translator)