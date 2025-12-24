#!/bin/bash
set -e

echo "Starting Visual-Diff report generation..."

# Color diff example
echo ">>> Generating color-diff report..."
sbt "run \
--old-file example/color-diff/testfiles/Test_Lato_150_Blue.jpg \
--new-file example/color-diff/testfiles/TestA_LatoAndRoboto_150.png \
--out example/color-diff/report"

#java -jar target/scala-3.7.4/visualdiff.jar \
#  --old-file example/color-diff/testfiles/Test_Lato_150_Blue.pdf \
#  --new-file example/color-diff/testfiles/TestA_LatoAndRoboto_150.pdf \
#  --out example/color-diff/report

# Font diff example
echo ">>> Generating font-diff report..."
sbt "run \
--old-file example/font-diff/testfiles/Lorem_Lato_11.pdf \
--new-file example/font-diff/testfiles/Lorem_Roboto_11.pdf \
--out example/font-diff/report"

#java -jar target/scala-3.7.4/visualdiff.jar \
#  --old-file example/font-diff/testfiles/Lorem_Lato_11.pdf \
#  --new-file example/font-diff/testfiles/Lorem_Roboto_11.pdf \
#  --out example/font-diff/report

# Batch diff example
sbt "run \
--batch-dir-old example/color-diff/testfiles/A \
--batch-dir-new example/color-diff/testfiles/B \
--out example/color-diff/batch/report"

#java -jar target/scala-3.7.4/visualdiff.jar \
#  --batch-dir-old example/color-diff/testfiles/A \
#  --batch-dir-new example/color-diff/testfiles/B \
#  --out example/color-diff/batch/report"
