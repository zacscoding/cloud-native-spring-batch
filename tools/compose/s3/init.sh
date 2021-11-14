#!/usr/bin/env bash

echo "Wating for LocalStack(10s)"
sleep 10s

echo "Delete bucket: spring-batch"
aws s3 rb s3://spring-batch --force --endpoint-url="http://localstack:4572" --region ap-southeast-2

echo "Create bucket: spring-batch"
aws s3 mb s3://spring-batch --endpoint-url="http://localstack:4572" --region ap-southeast-2

echo "Put objects"
for f in /s3/input/*.csv
do
    filename="$(cut -d'/' -f4 <<<"${f}")"
    aws s3api put-object --bucket spring-batch --key ${filename} --body ${f} --endpoint-url="http://localstack:4572" --region ap-southeast-2
done
