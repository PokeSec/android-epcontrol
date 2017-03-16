"""
build_python_deps.py : Tool for building Python modules for Android

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
import fnmatch
import os
import sys

import sh

from gitlab import GitlabUtils

AGENTLIB_ID = os.getenv("AGENTLIB_ID")
JOB_ID = 'android'
AGENTLIB_VERSION = "0.0.0.1"


class PythonModulesBuilder:

    def __init__(self, inpath, venv):
        self.inpath = inpath
        self.venv = venv
        sh.python3('-m', 'venv', '--clear', '--copies', self.venv)
        self.python = sh.Command(os.path.join(self.venv, "bin", "python3"))
        self.pip = sh.Command(os.path.join(self.venv, "bin", "pip"))
        self.pip("install", "-I", "cython==0.24")
        self.pip('install', 'wheel')
        self.cython = sh.Command(os.path.join(self.venv, "bin", "cython"))

    def patch_psutil(self):
        try:
            os.chdir(os.path.join(self.inpath, "native-modules", "psutil"))
            print(sh.git("submodule", "update", "--init"))
            print(sh.git("apply", "../psutil.patch"))
        except sh.ErrorReturnCode_1:
            pass

    def build_native_modules(self):
        os.chdir(self.inpath)
        for mod in os.listdir("native-modules"):
            if not os.path.isdir(os.path.join(self.inpath, "native-modules", mod)):
                continue
            print("Building native module", mod)
            os.chdir(os.path.join(self.inpath, "native-modules", mod))
            try:
                print(self.python('setup.py', 'build'))
            except sh.ErrorReturnCode_1 as e:
                print(e.stdout.decode('utf-8'))
                print(e.stderr.decode('utf-8'))
                for root, dirnames, filenames in os.walk("."):
                    for filename in fnmatch.filter(filenames, "*.pyx"):
                        print(self.cython(os.path.join(root, filename)))
                print(self.python('setup.py', 'build'))
            print(self.pip('install', '-I', '.'))

    def install_agentlib(self):
       # Ensure that no non-python modules are built
       os.putenv("CC", "/bin/false")
       os.putenv("CXX", "/bin/false")

       # Use a local development agentlib directory if it exists, else get the latest wheel from the repo
       dev_path = os.path.join(self.inpath,'agentlib')
       if os.path.exists(dev_path):
           print("Using local dev agentlib/")
           os.chdir(dev_path)
           print(self.python('setup.py', 'bdist_wheel', '--os', 'android'))
           print(self.pip('install', 'dist/agentlib-{}-py3-none-any.whl'.format(AGENTLIB_VERSION)))
       else:
           artifacts = GitlabUtils.get_latest_artifact(AGENTLIB_ID, JOB_ID)
           if artifacts:
               with open('/tmp/wheel.zip', 'wb') as ofile:
                   ofile.write(artifacts)
               print(sh.unzip('-o',
                              '/tmp/wheel.zip',
                              'wheel/{}/agentlib-{}-py3-none-any.whl'.format(JOB_ID, AGENTLIB_VERSION),
                              '-d',
                              '/tmp/'))
               print(self.pip('install', '/tmp/wheel/{}/agentlib-{}-py3-none-any.whl'.format(JOB_ID, AGENTLIB_VERSION)))
           else:
               raise RuntimeError("Cannot get agentlib")

if __name__ == '__main__':
    inpath = os.getcwd()
    venv = sys.argv[1]
    os.makedirs(venv, exist_ok=True)
    builder = PythonModulesBuilder(inpath, venv)
    builder.patch_psutil()
    builder.build_native_modules()
    builder.install_agentlib()
