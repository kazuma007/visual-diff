#!/bin/bash
set -e

echo "Starting Visual-Diff report generation..."

# Color diff example
echo ">>> Generating color-diff report..."
sbt "run \
--old-pdf example/color-diff/testfiles/Test_Lato_150_Blue.pdf \
--new-pdf example/color-diff/testfiles/TestA_LatoAndRoboto_150.pdf \
--out example/color-diff/report"

#java -jar target/scala-3.7.4/visualdiff.jar \
#  --old-pdf example/color-diff/testfiles/Test_Lato_150_Blue.pdf \
#  --new-pdf example/color-diff/testfiles/TestA_LatoAndRoboto_150.pdf \
#  --out example/color-diff/report

# Font diff example
echo ">>> Generating font-diff report..."
sbt "run \
--old-pdf example/font-diff/testfiles/Lorem_Lato_11.pdf \
--new-pdf example/font-diff/testfiles/Lorem_Roboto_11.pdf \
--out example/font-diff/report"

#java -jar target/scala-3.7.4/visualdiff.jar \
#  --old-pdf example/font-diff/testfiles/Lorem_Lato_11.pdf \
#  --new-pdf example/font-diff/testfiles/Lorem_Roboto_11.pdf \
#  --out example/font-diff/report
