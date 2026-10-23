if(NOT DEFINED REPO_SOURCE_DIR)
    message(FATAL_ERROR "REPO_SOURCE_DIR must point to the repository root")
endif()

include("${REPO_SOURCE_DIR}/cmake/AndroidVersionCode.cmake")

function(assert_version_code version_name expected)
    if(NOT "${version_name}" MATCHES "^([0-9]+\\.[0-9]+\\.[0-9]+)")
        message(FATAL_ERROR "Could not read version core from '${version_name}'")
    endif()
    set(expected_core "${CMAKE_MATCH_1}")
    omaapp_compute_android_version_code("${version_name}" "${expected_core}" actual)
    if(NOT "${actual}" STREQUAL "${expected}")
        message(FATAL_ERROR "${version_name}: expected versionCode ${expected}, got ${actual}")
    endif()
endfunction()

function(assert_version_code_rejected version_name expected_core expected_message)
    execute_process(
        COMMAND "${CMAKE_COMMAND}"
            "-DREPO_SOURCE_DIR=${REPO_SOURCE_DIR}"
            "-DVERSION_NAME=${version_name}"
            "-DEXPECTED_CORE=${expected_core}"
            -P "${REPO_SOURCE_DIR}/cmake/tests/AndroidVersionCodeCase.cmake"
        RESULT_VARIABLE result
        OUTPUT_VARIABLE output
        ERROR_VARIABLE error)
    set(combined_output "${output}${error}")
    if(result EQUAL 0 OR NOT "${combined_output}" MATCHES "${expected_message}")
        message(FATAL_ERROR
            "Expected '${version_name}' to be rejected with '${expected_message}', result=${result}: ${combined_output}")
    endif()
endfunction()

assert_version_code("0.1.0-alpha.1" 1000001)
assert_version_code("0.1.0-alpha.2" 1000002)
assert_version_code("0.1.0-alpha.10" 1000010)
assert_version_code("0.1.0-alpha.3000" 1003000)
assert_version_code("0.1.0-beta.1" 1003001)
assert_version_code("0.1.0-beta.3000" 1006000)
assert_version_code("0.1.0-rc.1" 1006001)
assert_version_code("0.1.0-rc.3000" 1009000)
assert_version_code("0.1.0" 1009999)
assert_version_code("0.1.1-alpha.1" 1010001)
assert_version_code("20.99.99" 2099999999)

assert_version_code_rejected("0.1.0-alpha.0" "0.1.0" "Unsupported Android version name")
assert_version_code_rejected("0.1.0-alpha.1" "0.2.0" "does not match CMake project version")
assert_version_code_rejected("0.01.0-alpha.1" "0.01.0" "leading zero")
assert_version_code_rejected("21.0.0-alpha.1" "21.0.0" "exceeds the supported")
assert_version_code_rejected("0.1.0-alpha.3001" "0.1.0" "sequence must be between")
assert_version_code_rejected("0.1.0-beta.3001" "0.1.0" "sequence must be between")
assert_version_code_rejected("0.1.0-rc.3001" "0.1.0" "sequence must be between")
assert_version_code_rejected("0.1.0-alpha.999999999999999" "0.1.0" "sequence must be between")

omaapp_compute_android_version_code("0.0.1" "0.0.1" seed_code)
omaapp_compute_android_version_code("0.1.0-alpha.1" "0.1.0" first_alpha_code)
omaapp_compute_android_version_code("0.1.0-alpha.2" "0.1.0" second_alpha_code)
if(NOT seed_code LESS first_alpha_code OR NOT first_alpha_code LESS second_alpha_code)
    message(FATAL_ERROR
        "Version codes must increase from the update seed through alpha.1 and alpha.2: ${seed_code}, ${first_alpha_code}, ${second_alpha_code}")
endif()

message(STATUS "Android versionCode ordering regression tests passed")
