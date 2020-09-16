#!/usr/bin/env bash

export JAVA_HOME=$(/usr/libexec/java_home)
mvn clean package
S3_BUCKET_LIST=("aws-bigdata-blog")
pwd
for S3_BUCKET in $S3_BUCKET_LIST; do

  # copy lambda jar file.
  aws s3 cp makeshift-lambdas/target/makeshift-lambdas.jar s3://$S3_BUCKET/artifacts/awsblog-makeshift/jars/ --acl public-read

  aws s3 cp example-spark-job/target/example-spark-job-1.0-SNAPSHOT-jar-with-dependencies.jar s3://$S3_BUCKET/artifacts/awsblog-makeshift/jars/ --acl public-read

  # Copy cloudformations
  pushd cloudformations;
  aws s3 cp . s3://$S3_BUCKET/artifacts/awsblog-makeshift/cloudformations/ --recursive --acl public-read --content-type 'text/x-yaml' #--profile $PROFILE
  popd

  aws s3 cp monroe-county-crash-data2003-to-2015.csv s3://$S3_BUCKET/artifacts/awsblog-makeshift/sample-dataset/ --acl public-read

done


## Update lambda code.

STACK_NAME=cf-makeshift-lambda
: '
aws cloudformation update-stack \
  --stack-name $STACK_NAME \
  --template-url https://${S3_BUCKET_1}.s3.us-east-1.amazonaws.com/makeshift-requests/cloudformations/step7-api-gateway-lambdas.yaml \
  --parameters \
	ParameterKey=paramLambdaFunctionName,ParameterValue=\"makeshift-aws-blog-invoke-step-functions-lambda\" \
	--capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
    #--profile $AWS_CREDENTIALS_PROFILE \
    #--disable-rollback

echo "Created stack ${STACK_NAME}"


aws cloudformation create-stack \
  --stack-name makeshift-cognito-stack \
  --template-url https://${S3_BUCKET_1}.s3.us-east-1.amazonaws.com/makeshift-requests/cloudformations/step4-cognito.yaml \
  --parameters \
	ParameterKey=paramCognitoUserPoolName,ParameterValue=\"MyEnterprise\" \
	--capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
    #--profile $AWS_CREDENTIALS_PROFILE \
    #--disable-rollback

'