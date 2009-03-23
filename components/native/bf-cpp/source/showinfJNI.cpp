//
// showinfJNI.cpp
//

/*
OME Bio-Formats C++ bindings for native access to Bio-Formats Java library.
Copyright (C) 2008-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

// An example of how to call Bio-Formats from C++ using raw JNI
// (i.e., without the C++ bindings).

/*
Compile on Mac OS X with:
  c++ showinfJNI.cpp -o showinfJNI -framework JavaVM

Compile on 32-bit Linux with:
  c++ showinfJNI.cpp -o showinfJNI \
    -I $JAVA_HOME/include -I $JAVA_HOME/include/linux \
    -L $JAVA_HOME/jre/lib/i386/client -ljvm

Depending on your flavor of 32-bit Linux, you may also need to execute:
  export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$JAVA_HOME/jre/lib/i386/client

Compile on 64-bit Linux with:
  c++ showinfJNI.cpp -o showinfJNI \
    -I $JAVA_HOME/include -I $JAVA_HOME/include/linux \
    -L $JAVA_HOME/jre/lib/amd64/server -ljvm

Depending on your flavor of 64-bit Linux, you may also need to execute:
  export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$JAVA_HOME/jre/lib/amd64/server

Then copy loci_tools.jar to the same folder and run:
  ./showinfJNI
*/

#include <iostream>
using namespace std;

#include <stdlib.h>

#include <jni.h>

int main(int argc, char* argv[]) {
  JavaVM *jvm;
  JNIEnv *env;

  // NB: This program requires loci_tools.jar in the same directory.
  string classpath = "-Djava.class.path";
  const int numJars = 1;
  string jars[numJars] = {
    "loci_tools.jar"
  };
  for (int i=0; i<numJars; i++) {
    classpath += i == 0 ? "=" : ":";
    classpath += jars[i];
  }
  cout << "Classpath = " + classpath << endl;

  // get the default initialization arguments and set the class path
  JavaVMInitArgs vm_args;
  JNI_GetDefaultJavaVMInitArgs(&vm_args);
  const int numOptions = 1;
  JavaVMOption options[numOptions];
  options[0].optionString = (char*) classpath.c_str();
  //options[1].optionString = "-verbose:jni";
  vm_args.version = JNI_VERSION_1_4; // VM version 1.4
  vm_args.options = options;
  vm_args.nOptions = numOptions;

  // load and initialize a Java VM, return a JNI interface pointer in env
  if (JNI_CreateJavaVM(&jvm, (void**) &env, &vm_args)) {
    cout << "Failed to create the JVM" << endl;
    exit(1);
  }

  // invoke the ImageInfo.main(String[]) method using the JNI
  jclass imageInfoClass = env->FindClass("loci/formats/tools/ImageInfo");
  cout << "Got ImageInfo class: " << imageInfoClass << endl;
  jmethodID mid = env->GetStaticMethodID(imageInfoClass,
    "main", "([Ljava/lang/String;)V");
  cout << "Got main method: " << mid << endl;
  jclass stringClass = env->FindClass("java/lang/String");
  cout << "Got String class: " << stringClass << endl;

  jobjectArray args = env->NewObjectArray(argc - 1, stringClass, 0);
  for (int i=1; i<argc; i++) {
    jstring arg = env->NewStringUTF(argv[i]);
    env->SetObjectArrayElement(args, i - 1, arg);
  }
  cout << "Got object array: " << args << endl;

  env->CallStaticVoidMethod(imageInfoClass, mid, args);

  jvm->DestroyJavaVM();
}