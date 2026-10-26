#!/usr/bin/env sh
set -eu
ARCH=$1
KIND=$2
DEST=$3
ROW=$(awk -v arch="$ARCH" -v kind="$KIND" '$1==arch && $2==kind { print $3 " " $4 }' /tmp/java21-checksums.txt)
[ -n "$ROW" ] || { echo 'Unsupported Java platform' >&2; exit 1; }
CHECKSUM=${ROW%% *}
URL=${ROW#* }
curl --fail --location --retry 3 "$URL" -o /tmp/java.tar.gz
printf '%s  /tmp/java.tar.gz\n' "$CHECKSUM" | sha256sum -c -
mkdir -p "$DEST"
tar -xzf /tmp/java.tar.gz --strip-components=1 -C "$DEST"
rm /tmp/java.tar.gz
