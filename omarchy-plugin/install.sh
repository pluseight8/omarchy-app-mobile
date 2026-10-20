#!/usr/bin/env bash
set -euo pipefail

plugin_id='org.pluseight.omaapp'
source_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
config_root="${XDG_CONFIG_HOME:-$HOME/.config}"
plugins_dir="$config_root/omarchy/plugins"
target_dir="$plugins_dir/$plugin_id"

if ! command -v omarchy >/dev/null 2>&1; then
    printf 'Omarchy was not found on PATH.\n' >&2
    exit 1
fi

if [[ -e "$target_dir" ]]; then
    printf 'Plugin directory already exists: %s\n' "$target_dir" >&2
    printf 'Review it and remove or back it up before reinstalling.\n' >&2
    exit 1
fi

mkdir -p "$plugins_dir"
stage_dir="$(mktemp -d "$plugins_dir/.omaapp-install.XXXXXX")"
cleanup() {
    if [[ -n "${stage_dir:-}" && -d "$stage_dir" ]]; then
        rm -rf -- "$stage_dir"
    fi
}
trap cleanup EXIT

cp -a "$source_dir/." "$stage_dir/"
omarchy plugin validate "$stage_dir"
mv -- "$stage_dir" "$target_dir"
stage_dir=''
omarchy plugin enable "$plugin_id"

printf 'Installed and enabled %s at %s\n' "$plugin_id" "$target_dir"
