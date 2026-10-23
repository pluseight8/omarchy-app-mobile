function(omaapp_compute_android_version_code version_name expected_core output_variable)
    if(NOT "${version_name}" MATCHES "^([0-9]+)\\.([0-9]+)\\.([0-9]+)(-(alpha|beta|rc)\\.([1-9][0-9]*))?$")
        message(FATAL_ERROR
            "Unsupported Android version name '${version_name}'; expected MAJOR.MINOR.PATCH, optionally followed by -alpha.N, -beta.N, or -rc.N")
    endif()

    set(major "${CMAKE_MATCH_1}")
    set(minor "${CMAKE_MATCH_2}")
    set(patch "${CMAKE_MATCH_3}")
    set(stage "${CMAKE_MATCH_5}")
    set(sequence "${CMAKE_MATCH_6}")
    set(actual_core "${major}.${minor}.${patch}")

    if(NOT "${actual_core}" STREQUAL "${expected_core}")
        message(FATAL_ERROR
            "Android version name '${version_name}' does not match CMake project version '${expected_core}'")
    endif()

    foreach(component IN ITEMS major minor patch)
        string(LENGTH "${${component}}" component_length)
        if(component_length GREATER 2)
            message(FATAL_ERROR "Android version component '${${component}}' is too large")
        endif()
        if("${${component}}" MATCHES "^0[0-9]+$")
            message(FATAL_ERROR "Android version component '${${component}}' has a leading zero")
        endif()
    endforeach()

    # Reserve four decimal digits per patch version for ordered prereleases.
    # 20.99.99 stays below Android's 2,100,000,000 versionCode ceiling.
    if(major GREATER 20 OR minor GREATER 99 OR patch GREATER 99)
        message(FATAL_ERROR
            "Android version '${version_name}' exceeds the supported 20.99.99 versionCode range")
    endif()

    if(NOT "${sequence}" STREQUAL "")
        string(LENGTH "${sequence}" sequence_length)
        if(sequence_length GREATER 4)
            message(FATAL_ERROR "Android prerelease sequence must be between 1 and 3333")
        endif()
    endif()

    if(stage STREQUAL "alpha")
        if(sequence GREATER 3000)
            message(FATAL_ERROR "Android prerelease sequence must be between 1 and 3000")
        endif()
        set(prerelease_rank "${sequence}")
    elseif(stage STREQUAL "beta")
        if(sequence GREATER 3000)
            message(FATAL_ERROR "Android prerelease sequence must be between 1 and 3000")
        endif()
        math(EXPR prerelease_rank "3000 + ${sequence}")
    elseif(stage STREQUAL "rc")
        if(sequence GREATER 3000)
            message(FATAL_ERROR "Android prerelease sequence must be between 1 and 3000")
        endif()
        math(EXPR prerelease_rank "6000 + ${sequence}")
    else()
        set(prerelease_rank 9999)
    endif()

    math(EXPR core_index "${major} * 10000 + ${minor} * 100 + ${patch}")
    math(EXPR version_code "${core_index} * 10000 + ${prerelease_rank}")
    if(version_code GREATER 2100000000)
        message(FATAL_ERROR
            "Android versionCode ${version_code} for '${version_name}' exceeds Android's 2,100,000,000 limit")
    endif()

    set(${output_variable} "${version_code}" PARENT_SCOPE)
endfunction()
