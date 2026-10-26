#!/usr/bin/env python3
"""Install official Temurin 21 locally, verifying its SHA-256; no system settings changed."""
import hashlib,json,pathlib,platform,subprocess,tarfile,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
osname={'Darwin':'mac','Linux':'linux'}.get(platform.system())
arch={'arm64':'aarch64','aarch64':'aarch64','x86_64':'x64','AMD64':'x64'}.get(platform.machine())
if not osname or not arch:raise SystemExit('Use a current patched JDK 21 and configure JAVA_HOME on this platform.')
url=f'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture={arch}&image_type=jdk&os={osname}&vendor=eclipse'
with urllib.request.urlopen(url,timeout=30) as r:meta=json.load(r)[0]
version=meta['version']
if (version['major'],version['minor'],version['security']) < (21,0,13):raise SystemExit('JDK response is below the required security baseline.')
pkg=meta['binary']['package'];tools=root/'.tools';tools.mkdir(exist_ok=True)
archive=tools/'jdk-download.tar.gz'
subprocess.run(['curl','--fail','--location','--retry','2',pkg['link'],'-o',str(archive)],check=True)
if hashlib.sha256(archive.read_bytes()).hexdigest()!=pkg['checksum']:raise SystemExit('Checksum mismatch; archive was not extracted.')
with tarfile.open(archive) as t:
 top=t.getnames()[0].split('/')[0]
 t.extractall(tools,filter='data')
archive.unlink()
home=tools/top
if osname=='mac':home=home/'Contents/Home'
link=tools/'java-home'
if link.is_symlink():link.unlink()
elif link.exists():raise SystemExit('Refusing to overwrite existing java-home directory.')
link.symlink_to(home.relative_to(tools),target_is_directory=True)
print('Verified project JDK:',version['semver'])
