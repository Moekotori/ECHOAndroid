cmake_minimum_required(VERSION 3.22.1)
project(echo_usb_isoc)

add_library(echo_usb_isoc SHARED echo_usb_isoc.cpp)

find_library(log-lib log)

target_link_libraries(echo_usb_isoc ${log-lib})
target_compile_options(echo_usb_isoc PRIVATE -Wall -Wextra -O2 -fno-exceptions -fno-rtti)
