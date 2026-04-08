#!/bin/sh
cd "$(dirname "${0}")"
jdk/bin/java -Xms128m -Xmx1024m -cp .:lib/* com.maknoon.DBDefrag
jdk/bin/java -cp lib/* org.apache.lucene.index.IndexUpgrader -delete-prior-commits arabicRootsTableIndex
jdk/bin/java -cp lib/* org.apache.lucene.index.IndexUpgrader -delete-prior-commits arabicRootsIndex
jdk/bin/java -cp lib/* org.apache.lucene.index.IndexUpgrader -delete-prior-commits arabicLuceneIndex
jdk/bin/java -cp lib/* org.apache.lucene.index.IndexUpgrader -delete-prior-commits arabicIndex
jdk/bin/java -cp lib/* org.apache.lucene.index.IndexUpgrader -delete-prior-commits englishIndex
