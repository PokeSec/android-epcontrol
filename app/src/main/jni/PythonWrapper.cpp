/*
 * PythonWrapper.cpp : JNI wrapper for Python code
 *
 * This file is part of EPControl.
 *
 * Copyright (C) 2016  Jean-Baptiste Galet & Timothe Aeberhardt
 *
 * EPControl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EPControl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with EPControl.  If not, see <http://www.gnu.org/licenses/>.
 */
#include <string>
#include <jni.h>

#include <android/log.h>
#include <python/Python.h>

#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "PythonWrapper.cpp", fmt, ##__VA_ARGS__)
#define LOGP(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "python", fmt, ##__VA_ARGS__)

static PyObject *androidembed_log(PyObject *self, PyObject *args) {
    char *logstr = NULL;
    if (!PyArg_ParseTuple(args, "s", &logstr)) {
        return NULL;
    }
    LOGP(logstr);
    Py_RETURN_NONE;
}

static PyMethodDef AndroidEmbedMethods[] = {
        {"log", androidembed_log, METH_VARARGS, "Log on android platform"},
        {NULL, NULL, 0, NULL}};

static struct PyModuleDef androidembed = {PyModuleDef_HEAD_INIT, "androidembed",
                                          "", -1, AndroidEmbedMethods};

PyMODINIT_FUNC initandroidembed(void) {
    return PyModule_Create(&androidembed);
}

#define GPEM_ERROR(what) ;
#define PyString_Check PyBytes_Check
#define PyString_AsString PyBytes_AsString

char *GetPythonTraceback(PyObject *exc_type, PyObject *exc_value, PyObject *exc_tb)
{
    // Sleep (30000); // Time enough to attach the debugger (barely)
    char *result = NULL;
    char *errorMsg = NULL;
    PyObject *modStringIO = NULL;
    PyObject *modTB = NULL;
    PyObject *obFuncStringIO = NULL;
    PyObject *obStringIO = NULL;
    PyObject *obFuncTB = NULL;
    PyObject *argsTB = NULL;
    PyObject *obResult = NULL;

    /* Import the modules we need - cStringIO and traceback */
#if (PY_VERSION_HEX < 0x03000000)
    modStringIO = PyImport_ImportModule("cStringIO");
#else
    // In py3k, cStringIO is in "io"
    modStringIO = PyImport_ImportModule("io");
#endif

    if (modStringIO==NULL) GPEM_ERROR("cant import cStringIO");
    modTB = PyImport_ImportModule("traceback");
    if (modTB==NULL) GPEM_ERROR("cant import traceback");

    /* Construct a cStringIO object */
    obFuncStringIO = PyObject_GetAttrString(modStringIO, "StringIO");
    if (obFuncStringIO==NULL) GPEM_ERROR("cant find cStringIO.StringIO");
    obStringIO = PyObject_CallObject(obFuncStringIO, NULL);
    if (obStringIO==NULL) GPEM_ERROR("cStringIO.StringIO() failed");

    /* Get the traceback.print_exception function, and call it. */
    obFuncTB = PyObject_GetAttrString(modTB, "print_exception");
    if (obFuncTB==NULL) GPEM_ERROR("cant find traceback.print_exception");
    argsTB = Py_BuildValue("OOOOO"
#if (PY_VERSION_HEX >= 0x03000000)
                                   "i"
            // Py3k has added an undocumented 'chain' argument which defaults to True
            //	and causes all kinds of exceptions while trying to print a goddam exception
#endif
            ,
                           exc_type ? exc_type : Py_None,
                           exc_value ? exc_value : Py_None,
                           exc_tb  ? exc_tb  : Py_None,
                           Py_None,	// limit
                           obStringIO
#if (PY_VERSION_HEX >= 0x03000000)
            ,0	// Goddam undocumented 'chain' param, which defaults to True
#endif
    );
    if (argsTB==NULL) GPEM_ERROR("cant make print_exception arguments");

    obResult = PyObject_CallObject(obFuncTB, argsTB);
    if (obResult==NULL){
        // Chain parameter when True causes traceback.print_exception to fail, leaving no
        //	way to see what the original problem is, or even what error print_exc raises
        // PyObject *t, *v, *tb;
        // PyErr_Fetch(&t, &v, &tb);
        // PyUnicodeObject *uo=(PyUnicodeObject *)v;
        // DebugBreak();
        GPEM_ERROR("traceback.print_exception() failed");
    }
    /* Now call the getvalue() method in the StringIO instance */
    Py_DECREF(obFuncStringIO);
    obFuncStringIO = PyObject_GetAttrString(obStringIO, "getvalue");
    if (obFuncStringIO==NULL) GPEM_ERROR("cant find getvalue function");
    Py_DECREF(obResult);
    obResult = PyObject_CallObject(obFuncStringIO, NULL);
    if (obResult==NULL) GPEM_ERROR("getvalue() failed.");

    /* And it should be a string all ready to go - duplicate it. */
    if (PyString_Check(obResult))
        result = strdup(PyString_AsString(obResult));
#if (PY_VERSION_HEX >= 0x03000000)
    else if (PyUnicode_Check(obResult))
        result = strdup(_PyUnicode_AsString(obResult));
#endif
    else
        GPEM_ERROR("getvalue() did not return a string");

    done:
    if (result==NULL && errorMsg != NULL)
        result = strdup(errorMsg);
    Py_XDECREF(modStringIO);
    Py_XDECREF(modTB);
    Py_XDECREF(obFuncStringIO);
    Py_XDECREF(obStringIO);
    Py_XDECREF(obFuncTB);
    Py_XDECREF(argsTB);
    Py_XDECREF(obResult);
    return result;
}


extern "C"
jboolean
Java_io_pokesec_epcontrol_EPCService_initializePython(JNIEnv* env,
                                                         jobject thiz,
                                                         jstring rootPath )
{
    const char *s = env->GetStringUTFChars(rootPath, false);
    chdir(s);
    char paths[512];
    snprintf(paths, 512,
             "%s/assets/lib/pylib:%s/assets/lib/pytool:%s/assets/lib/modules",
             s, s, s);
    LOG("Resolved Python paths : %s", paths);
    Py_SetPath(Py_DecodeLocale(paths, NULL));
    PyImport_AppendInittab("androidembed", initandroidembed);
    Py_Initialize();
    //FIXME: Should be resolved in 3.6 using __ANDROID__
    Py_FileSystemDefaultEncoding = "utf-8";
    Py_HasFileSystemDefaultEncoding = 1;

    PyEval_InitThreads();
    
    PyObject *pModule, *pMain, *pResult;
    pModule = PyImport_ImportModule("epc.android.service");

    if (pModule != NULL) {
        pMain = PyObject_GetAttrString(pModule, "main");
        if (pMain != NULL && PyCallable_Check(pMain)) {
            pResult = PyObject_CallObject(pMain, NULL);
            if (pResult != NULL) {
                Py_DECREF(pResult);
            }
            else {
                Py_DECREF(pMain);
                Py_DECREF(pModule);
                PyErr_Print();
                fprintf(stderr, "Call failed\n");
                return 0;
            }
        }
        else {
            LOG("Could not find main function\n");
        }

        Py_XDECREF(pMain);
        Py_DECREF(pModule);
    }
    else
    {
        PyObject *type, *value, *tb;
        PyErr_Fetch(&type, &value, &tb);
        char *traceback = GetPythonTraceback(type, value, tb);
        if (traceback)
            LOG("%s\n", traceback);
        else
            LOG("Cannot get traceback\n");

        if (traceback)
            free(traceback);
        Py_XDECREF(type);
        Py_XDECREF(value);
        Py_XDECREF(tb);

        LOG("Could not find epc.android.service module\n");
    }
    PyEval_SaveThread();
    return 1;
}

extern "C"
void
Java_io_pokesec_epcontrol_EPCService_onAction(JNIEnv* env,
                                                 jobject thiz,
                                                 jstring action,
                                                 jstring dataString)
{
    PyObject *pModule, *pScheduler, *pResult;

    PyGILState_STATE gstate;
    gstate = PyGILState_Ensure();

    pModule = PyImport_ImportModule("epc.android.service");

    if (pModule != NULL)
    {
        pScheduler = PyObject_GetAttrString(pModule, "service");
        if (pScheduler != NULL) {
            const char* c_Action = NULL;
            const char* c_DataString = NULL;
            if (action != NULL)
                c_Action = env->GetStringUTFChars(action, NULL);
            if (dataString != NULL)
                c_DataString = env->GetStringUTFChars(dataString, NULL);
            pResult = PyObject_CallMethod(pScheduler,
                                          "on_action",
                                          "(ss)",
                                          c_Action,
                                          c_DataString);
            if(pResult != NULL)
            {
                Py_DECREF(pResult);
            }
            else
            {
                Py_DECREF(pScheduler);
                Py_DECREF(pModule);
                PyErr_Print();
                fprintf(stderr, "Call failed\n");
                return;
            }
        }
        else {
            LOG("Could not find scheduler object\n");
        }
        Py_XDECREF(pScheduler);
        Py_DECREF(pModule);
    }
    else
    {
        LOG("Could not find epc.android.service module\n");
    }
    PyGILState_Release(gstate);
}

extern "C"
void
Java_io_pokesec_epcontrol_EPCService_releasePython(JNIEnv* env, jobject thiz, jstring s)
{
    LOG("Uninitializing Python");
    Py_Finalize();
}
