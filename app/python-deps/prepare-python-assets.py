import shutil
import sys
import os
import re
import py_compile
from pathlib import Path
import tempfile
import json
from zipfile import ZIP_DEFLATED
from zipfile import ZipFile


TKTCL_RE = re.compile(r'^(_?tk|tcl).+\.(pyd|dll)', re.IGNORECASE)
DEBUG_RE = re.compile(r'_d\.(pyd|dll|exe)$', re.IGNORECASE)

EXCLUDE_FROM_LIBRARY = {
    '__pycache__',
    'ensurepip',
    'idlelib',
    'pydoc_data',
    'site-packages',
    'tkinter',
    'turtledemo',
    'venv',
    'pypiwin32_system32'
}
EXCLUDE_FILE_FROM_LIBRARY = {
    'bdist_wininst.py',
}


def is_not_debug(p):
    if DEBUG_RE.search(p.name):
        return False

    if TKTCL_RE.search(p.name):
        return False

    return p.name.lower() not in {
        '_ctypes_test.so',
        '_testbuffer.so',
        '_testcapi.so',
        '_testimportmultiple.so',
        '_testmultiphase.so',
        'xxlimited.so',
    }


def include_in_lib(p):
    name = p.name.lower()
    if p.is_dir():
        if name in EXCLUDE_FROM_LIBRARY:
            return False
        if name.startswith('plat-'):
            return False
        if name.endswith('.dist-info') or name.endswith('.egg-info'):
            return False
        if name == 'test' and p.parts[-2].lower() in ['pytool', 'lib']:
            return False
        if name in {'test', 'tests'} and p.parts[-3].lower() in ['pytool', 'lib']:
            return False
        return True

    if name in EXCLUDE_FILE_FROM_LIBRARY:
        return False

    suffix = p.suffix.lower()
    return suffix not in {'.pyc', '.pyo', '.exe'}

PKG_LAYOUT = [
    ('/', 'src:', 'settings.json', None),
    ('/', 'src:', 'trust.pem', None),
    ('lib/pytool/', 'site-packages:', '**/*', include_in_lib),
    ('lib/pylib/', 'stdlib:', '**/*', include_in_lib),
    ('lib/modules/', 'modules:', '**/*', is_not_debug),
]


def copy_to_layout(target, rel_sources):
    count = 0

    if target.suffix.lower() == '.zip':
        if target.exists():
            target.unlink()

        with ZipFile(str(target), 'w', ZIP_DEFLATED) as f:
            with tempfile.TemporaryDirectory() as tmpdir:
                for s, rel in rel_sources:
                    if rel.suffix.lower() == '.py':
                        pyc = Path(tmpdir) / rel.with_suffix('.pyc').name
                        try:
                            py_compile.compile(str(s), str(pyc), str(rel), doraise=True, optimize=2)
                        except py_compile.PyCompileError:
                            f.write(str(s), str(rel))
                        else:
                            f.write(str(pyc), str(rel.with_suffix('.pyc')))
                    else:
                        f.write(str(s), str(rel))
                    count += 1

    else:
        for s, rel in rel_sources:
            dest = target / rel
            try:
                dest.parent.mkdir(parents=True)
            except FileExistsError:
                pass

            if rel.suffix.lower() == '.py':
                pyc = Path(target) / rel.with_suffix('.pyc')
                try:
                    py_compile.compile(str(s), str(pyc), str(rel), doraise=True, optimize=2)
                except py_compile.PyCompileError:
                    shutil.copy(str(s), str(dest))
            else:
                shutil.copy(str(s), str(dest))
            count += 1

    return count


def rglob(root, pattern, condition):
    dirs = [root]
    recurse = pattern[:3] in {'**/', '**\\'}
    while dirs:
        d = dirs.pop(0)
        for f in d.glob(pattern[3:] if recurse else pattern):
            if recurse and f.is_dir() and (not condition or condition(f)):
                dirs.append(f)
            elif f.is_file() and (not condition or condition(f)):
                yield f, f.relative_to(root)


def prepare_assets(site_packages, crystax_python, output_folder):
    os.makedirs(output_folder, exist_ok=True)
    with tempfile.TemporaryDirectory() as stdlib:
        ZipFile(os.path.join(crystax_python, 'stdlib.zip'), 'r').extractall(stdlib)
        for t, s, p, c in PKG_LAYOUT:
            tmp = s.split(':')
            if tmp[0] == 'site-packages':
                s = Path(site_packages)
            elif tmp[0] == 'stdlib':
                s = Path(stdlib)
            elif tmp[0] == 'modules':
                s = Path(crystax_python) / 'modules'
            elif tmp[0] == 'src':
                s = Path(os.getcwd()) / 'src/main'
            else:
                continue
            copied = copy_to_layout(Path(output_folder) / t.rstrip('/'), rglob(s, p, c))
            print('Copied {} files'.format(copied))

if __name__ == '__main__':
    prepare_assets(*sys.argv[1:])
