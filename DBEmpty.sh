#!/bin/sh
cd "$(dirname "${0}")"
jdk/bin/java -cp .:lib/* com.maknoon.CreateIndexerDatabase false
