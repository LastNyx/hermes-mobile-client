"""Find calls a library makes into androidx.compose that the APK's Compose does not define.

Usage: python3 -I tools/linkcheck.py <app-debug.apk> [package-prefix, default Lcom/mikepenz/] [--dexdump PATH]

dexdump is found with --dexdump, then $ANDROID_HOME (or $ANDROID_SDK_ROOT), then ~/tools/android-sdk
(where scripts/setup-toolchain.sh installs it), newest build-tools first.

A green build only proves a library compiles. A library built against a newer Compose runtime fails at
runtime with NoSuchMethodError the first time a composable runs. This compares every invoke-* from the
library's classes into androidx/compose classes against the methods those classes define in the APK
(walking superclasses). Unresolved hashCode/toPx/toDp/ordinal/compareTo/setStackTrace entries are noise
(inherited or interface-default methods the walk cannot see); anything else is a real gap.
Needs build-tools dexdump. It cannot see interface supertypes, so it is a filter, not proof.
"""
import argparse, collections, glob, os, re, subprocess, sys, tempfile, zipfile


def find_dexdump(explicit):
    if explicit:
        return explicit
    roots = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'), os.path.expanduser('~/tools/android-sdk')]
    for root in filter(None, roots):
        hits = glob.glob(os.path.join(root, 'build-tools', '*', 'dexdump*'))
        hits = [h for h in hits if os.path.basename(h) in ('dexdump', 'dexdump.exe')]
        if hits:
            return sorted(hits, key=lambda h: [int(x) if x.isdigit() else 0
                                               for x in re.split(r'[.\\/]', h.split('build-tools')[-1])])[-1]
    sys.exit('dexdump not found: set ANDROID_HOME or pass --dexdump PATH')


ap = argparse.ArgumentParser()
ap.add_argument('apk')
ap.add_argument('prefix', nargs='?', default='Lcom/mikepenz/')
ap.add_argument('--dexdump')
args = ap.parse_args()
apk, src_prefix, DD = args.apk, args.prefix, find_dexdump(args.dexdump)
work = tempfile.mkdtemp()
z = zipfile.ZipFile(apk)
dexes = [n for n in z.namelist() if re.fullmatch(r'classes\d*\.dex', n)]
defined = collections.defaultdict(set)
supers = {}
refs = set()
for n in dexes:
    p = z.extract(n, work)
    out = subprocess.run([DD, '-d', p], capture_output=True).stdout.decode('utf-8', 'replace')
    cls = name = None
    for line in out.splitlines():
        m = re.match(r"\s*Class descriptor\s*:\s*'(.*)'", line)
        if m:
            cls = m.group(1)
            continue
        m = re.match(r"\s*Superclass\s*:\s*'(.*)'", line)
        if m and cls:
            supers[cls] = m.group(1)
            continue
        m = re.match(r"\s*name\s*:\s*'(.*)'", line)
        if m:
            name = m.group(1)
            continue
        m = re.match(r"\s*type\s*:\s*'(\(.*)'", line)
        if m and cls and name:
            defined[cls].add(name + m.group(1))
            name = None
            continue
        m = re.search(r"\s(L[\w/$\-]+;)\.([\w$<>\-]+):(\([^)]*\)\S+)\s*(?://|$)", line)
        if m and 'invoke-' in line:
            refs.add((m.group(1), m.group(2) + m.group(3), cls))
bad = []
for c, ms, src in sorted(refs, key=str):
    if not src or not src.startswith(src_prefix) or not c.startswith('Landroidx/compose/') or c not in defined:
        continue
    k, found = c, False
    while k:
        if ms in defined.get(k, ()):
            found = True
            break
        k = supers.get(k)
    if not found:
        bad.append((c, ms))
print('library->compose refs:', len([r for r in refs if r[2] and r[2].startswith(src_prefix) and r[0].startswith('Landroidx/compose/')]), 'unresolved:', len(bad))
for c, ms in bad:
    print(' ', c[1:-1].split('/')[-1], ms[:150])
