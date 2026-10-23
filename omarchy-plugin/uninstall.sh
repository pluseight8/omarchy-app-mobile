#!/usr/bin/env bash
set -euo pipefail

plugin_id='org.pluseight.omaapp'
config_root="${XDG_CONFIG_HOME:-$HOME/.config}"
target_dir="$config_root/omarchy/plugins/$plugin_id"
state_root="${XDG_STATE_HOME:-$HOME/.local/state}"
backup_root="$state_root/omarchy/plugin-backups"

if ! command -v omarchy >/dev/null 2>&1; then
    printf 'Omarchy was not found on PATH.\n' >&2
    exit 1
fi

if [[ ! -f "$target_dir/manifest.json" ]]; then
    printf 'OmaApp plugin was not found at %s\n' "$target_dir" >&2
    exit 1
fi

installed_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8")).get("id", ""))' "$target_dir/manifest.json")"
if [[ "$installed_id" != "$plugin_id" ]]; then
    printf 'Refusing to move an unexpected plugin id: %s\n' "$installed_id" >&2
    exit 1
fi

omarchy plugin disable "$plugin_id"
mkdir -p "$backup_root"
chmod 700 "$backup_root"
backup_dir="$backup_root/$plugin_id.$(date +%Y%m%d%H%M%S%N)"
mv -- "$target_dir" "$backup_dir"
printf 'Disabled %s and preserved its source outside the plugin scan at %s\n' \
    "$plugin_id" "$backup_dir"
