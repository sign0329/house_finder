#!/usr/bin/env python3
"""Apply or check the project's pinned Java formatting style."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
VERSION = '1.24.0'
SHA256 = '812f805f58112460edf01bf202a8e61d0fd1f35c0d4fabd54220640776ec57a1'
URL = ('https://repo.maven.apache.org/maven2/com/google/googlejavaformat/'
       f'google-java-format/{VERSION}/google-java-format-{VERSION}-all-deps.jar')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Check without changing files')
    args = parser.parse_args()
    jar = ROOT / '.tools' / f'google-java-format-{VERSION}.jar'
    if not jar.exists():
        with urllib.request.urlopen(URL, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != SHA256:
            raise SystemExit('Formatter checksum mismatch; download rejected.')
        jar.parent.mkdir(parents=True, exist_ok=True)
        jar.write_bytes(data)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != SHA256:
        raise SystemExit('Formatter checksum mismatch; remove the cached formatter and retry.')
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
    if not java:
        raise SystemExit('Java 21 is required; set JAVA_HOME.')
    files = sorted(str(path) for path in (ROOT / 'src').rglob('*.java'))
    flags = ['--dry-run', '--set-exit-if-changed'] if args.check else ['--replace']
    return subprocess.run([java, '-jar', str(jar), '--aosp', *flags, *files]).returncode


if __name__ == '__main__':
    raise SystemExit(main())
