cmake_minimum_required(VERSION 3.18.1)
project("nodebridge")

set(CMAKE_CXX_STANDARD 17)

# libnode.so (nodejs-mobile) được workflow tải về app/libnode/bin/<abi>/
set(NODE_LIB ${CMAKE_SOURCE_DIR}/../../../libnode/bin/${ANDROID_ABI}/libnode.so)
add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION ${NODE_LIB})

add_library(nodebridge SHARED nodebridge.cpp)
target_link_libraries(nodebridge node log)
