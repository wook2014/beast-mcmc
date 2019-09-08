#!/bin/bash
for file in TestXML/*\.xml
do
  if java -Djava.library.path=${BEAGLE_LIB} -jar ../build/dist/beast.jar -seed 666 -overwrite $file; then
    echo $file passed
  else
    echo $file failed; exit -1
  fi
done
