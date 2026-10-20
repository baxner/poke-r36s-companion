# El emulador de GBA (mGBA 0.10.5, MPL-2.0) como librería estática, más la capa JNI de la app.
# El código de mGBA lo baja scripts/bajar-mgba.sh a third_party/mgba (no se versiona).
cmake_minimum_required(VERSION 3.22.1)
project(emulador C)

if(NOT MGBA_DIR OR NOT EXISTS "${MGBA_DIR}/CMakeLists.txt")
	message(FATAL_ERROR "Falta el código de mGBA en '${MGBA_DIR}': corre scripts/bajar-mgba.sh")
endif()

# Siempre optimizado, también en debug: a -O0 el emulador no llega a velocidad real. Va para
# mGBA y para nuestra capa por igual (las dos ven las mismas estructuras).
set(CMAKE_C_FLAGS_DEBUG "-O2 -g")

# Solo el núcleo de GBA, sin depender de nada más (las mismas opciones de la prueba técnica,
# ver docs/plan.md, "Prueba técnica del intercambio real").
set(LIBMGBA_ONLY ON CACHE BOOL "" FORCE)
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB OFF CACHE BOOL "" FORCE)
foreach(opcion USE_DEBUGGERS USE_GDB_STUB USE_EDITLINE USE_FFMPEG USE_ZLIB USE_MINIZIP USE_PNG
		USE_LIBZIP USE_SQLITE3 USE_ELF USE_LZMA USE_LUA USE_DISCORD_RPC USE_EPOXY ENABLE_SCRIPTING
		BUILD_GL BUILD_GLES2 BUILD_GLES3)
	set(${opcion} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory("${MGBA_DIR}" mgba EXCLUDE_FROM_ALL)

add_library(emulador SHARED emulador.c)
target_link_libraries(emulador PRIVATE mgba aaudio android log)
