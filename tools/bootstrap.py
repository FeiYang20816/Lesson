"""Download an isolated Android build toolchain from official distribution servers."""
from pathlib import Path
import hashlib
import concurrent.futures
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)

def fetch(url, name, expected=None):
    target = TOOLS / name
    if not target.exists():
        print(f'Downloading {name}', flush=True)
        with urllib.request.urlopen(url, timeout=90) as r, target.with_suffix('.part').open('wb') as f:
            while chunk := r.read(1024 * 1024):
                f.write(chunk)
        target.with_suffix('.part').replace(target)
    actual = hashlib.sha256(target.read_bytes()).hexdigest()
    if expected and actual.lower() != expected.lower():
        raise ValueError(f'Checksum mismatch for {name}: {actual}')
    return target

def gradle():
    url = 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip'
    expected = urllib.request.urlopen(url + '.sha256', timeout=60).read().decode().strip()
    archive = fetch(url, 'gradle-8.11.1-bin.zip', expected)
    if not (TOOLS / 'gradle-8.11.1/bin/gradle.bat').exists():
        with zipfile.ZipFile(archive) as z: z.extractall(TOOLS)
    print('Gradle ready', flush=True)

def android():
    archive = fetch('https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip',
                    'android-command-tools.zip', '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a')
    dest = TOOLS / 'android-sdk/cmdline-tools/latest'
    if not (dest / 'bin/sdkmanager.bat').exists():
        with zipfile.ZipFile(archive) as z:
            for member in z.infolist():
                relative = member.filename.removeprefix('cmdline-tools/')
                if not relative: continue
                target = dest / relative
                if member.is_dir(): target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(z.read(member))
    (ROOT / 'local.properties').write_text('sdk.dir=' + str(TOOLS / 'android-sdk').replace('\\','/') + '\n')
    print('Android command tools ready', flush=True)

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for result in [pool.submit(gradle), pool.submit(android)]: result.result()
