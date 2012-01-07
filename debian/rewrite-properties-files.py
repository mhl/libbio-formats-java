#!/usr/bin/python

import sys, os, json, tempfile, shutil, re

top_level = os.path.realpath(os.path.join(sys.path[0], '..'))
debian_directory = os.path.join(top_level, 'debian')

required_packages = set([])

def rewrite_properties(properties_filename, mapping):

    with open(properties_filename) as fp:
        s = fp.read()

    for original, replacement in mapping.items():
        s = re.sub(re.escape(original), replacement['jar'], s)

    output = tempfile.NamedTemporaryFile(delete=False)
    output.write(s)
    print "Wrote output to:", output.name
    shutil.move(output.name, properties_filename)

with open(os.path.join(top_level, 'debian', 'jar-mapping.json')) as fp:
    mapping = json.load(fp)

with open(os.path.join(debian_directory, 'extra-classpath.json')) as fp:
    extra_classpath = json.load(fp)

for dirpath, dirnames, filenames in os.walk(top_level):
    for filename in filenames:
        if filename.endswith('.properties'):
            properties_filename = os.path.join(dirpath, filename)
            print 'Will rewrite:', properties_filename
            rewrite_properties(properties_filename, mapping)

required_packages = [ x['package'] for x in mapping.values() + extra_classpath if 'package' in x ]

print "sudo apt-get install", " ".join(required_packages)
print "export CLASSPATH="+":".join(x['jar'] for x in extra_classpath)
