"""Cache the Android test runtime when Java's automatic download is unavailable."""
from pathlib import Path
import urllib.request
import hashlib

root = Path(__file__).resolve().parents[1]
dest = root / '.tools' / 'robolectric'
dest.mkdir(parents=True, exist_ok=True)
name = 'android-all-instrumented-15-robolectric-12650502-i7.jar'
url = 'https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/15-robolectric-12650502-i7/' + name
target = dest / name
expected = urllib.request.urlopen(url + '.sha512', timeout=60).read().decode().strip().split()[0]
if not target.exists():
    print('Downloading Android test runtime', flush=True)
    with urllib.request.urlopen(url, timeout=90) as response, target.with_suffix('.part').open('wb') as out:
        while chunk := response.read(1024 * 1024): out.write(chunk)
    target.with_suffix('.part').replace(target)
if hashlib.sha512(target.read_bytes()).hexdigest() != expected:
    raise RuntimeError('Android test runtime checksum mismatch')
print('Verified Android test runtime', flush=True)
