from subprocess import call
import json
import re
import sys

cpath = "./:./libs/commons-cli-1.3.jar:"
cpath += "./libs/commons-lang-2.6.jar:"
cpath += "./libs/lucene-analyzers-common-5.0.0.jar:"
cpath += "./libs/lucene-core-5.0.0.jar:"
cpath += "./libs/lucene-queryparser-5.0.0.jar:"
cpath += "./libs/json-20131018.jar"

if len(sys.argv) <= 1:
	call(["javac", "-cp",cpath , "GeoNameResolver.java"])
elif len(sys.argv) == 3 :
	call(["java", "-cp",cpath , "GeoNameResolver", "-i", sys.argv[1], "-b", sys.argv[2]])
elif len(sys.argv) == 4:
	#count = 0
	#with open("raw_data") as input_file:
	#	for json_obj in input_file:
	#		try:
	#			print count
	#count += 1
	#data = json.loads(str(json_obj))
	#loc = data["availableAtOrFrom"]["address"]["name"]
	#loc = loc.replace("/", "")
	call(["java", "-cp",cpath , "GeoNameResolver", "-i", sys.argv[1], "-s", sys.argv[2]])
	#		except KeyError:
	#			pass
