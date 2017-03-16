#!/usr/bin/env python3
"""
build_ndk.py : Tool for building Python (w/ OpenSSL) against the Crystax NDK

This file is part of EPControl.

Copyright (C) 2016  Jean-Baptiste Galet & Timothe Aeberhardt

EPControl is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

EPControl is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with EPControl.  If not, see <http://www.gnu.org/licenses/>.
"""
import os
import sys
import sh
import shutil

PYTHON_VERSION = "3.5.2"
OPENSSL_VERSION = "1.0.2j"
ABIS = ('armeabi-v7a',)

HERE = os.path.dirname(os.path.abspath(__file__))
PATCH_FILE = os.path.join(HERE, "crystax-ndk-10.3.2.patch")


def main(target_dir):
    os.makedirs(target_dir, exist_ok=True)
    print("Downloading Crystax NDK 10.3.2")
    print(sh.tar(sh.curl("https://www.crystax.net/download/crystax-ndk-10.3.2-linux-x86_64.tar.xz", _piped=True),
                 "xJ",
                 C=target_dir))
    print("Downloading Python", PYTHON_VERSION)
    print(sh.tar(sh.curl("https://www.python.org/ftp/python/{0}/Python-{0}.tar.xz".format(PYTHON_VERSION), _piped=True),
                 "xJ",
                 C=target_dir))
    print("Downloading OpenSSL", OPENSSL_VERSION)
    print(sh.tar(sh.curl("https://www.openssl.org/source/openssl-{}.tar.gz".format(OPENSSL_VERSION), _piped=True),
                 "xz",
                 C=target_dir))
    print("Patching Crystax NDK")
    print(sh.patch('-p0', d=target_dir, i=PATCH_FILE, force=True))
    os.makedirs(os.path.join(HERE, target_dir, 'crystax-ndk-10.3.2', 'sources', 'openssl', OPENSSL_VERSION), exist_ok=True)
    shutil.copyfile(os.path.join(HERE, 'openssl_Android.mk'), os.path.join(HERE, target_dir, 'crystax-ndk-10.3.2', 'sources', 'openssl', OPENSSL_VERSION, 'Android.mk'))
    print("Building OpenSSL")
    openssl_build = sh.Command(os.path.join(HERE,
                                            target_dir,
                                            "crystax-ndk-10.3.2/build/tools/build-target-openssl.sh"
                                            ))
    openssl_src = os.path.join(HERE, target_dir, "openssl-{}".format(OPENSSL_VERSION))
    
    for line in openssl_build(openssl_src, abis=','.join(ABIS), verbose=True, _iter=True):
        print(line)

    for abi in ABIS:
        os.chmod(os.path.join(HERE, target_dir, "crystax-ndk-10.3.2/sources/openssl/{}/libs/{}/libcrypto.so".format(OPENSSL_VERSION, abi)), 0o755)
        os.chmod(os.path.join(HERE, target_dir, "crystax-ndk-10.3.2/sources/openssl/{}/libs/{}/libssl.so".format(OPENSSL_VERSION, abi)), 0o755)

    print("Building Python")
    python_build = sh.Command(os.path.join(HERE,
                                           target_dir,
                                           "crystax-ndk-10.3.2/build/tools/build-target-python.sh"
                                           ))
    python_src = os.path.join(HERE, target_dir, "Python-{}".format(PYTHON_VERSION))
    for line in python_build(python_src, abis=','.join(ABIS), verbose=True, _iter=True):
        print(line)

    print("All done !")


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit("Usage: {} target_dir".format(sys.argv[0]))
    main(sys.argv[1])
