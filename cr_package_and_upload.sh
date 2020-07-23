#!/usr/bin/env bash
S3_BUCKET_1="skkodali-proserve"

export JAVA_HOME=$(/usr/libexec/java_home)
mvn clean package
S3_BUCKET_LIST="skkodali-proserve"
S3_BUCKET_1="skkodali-proserve"
pwd
for S3_BUCKET in $S3_BUCKET_LIST; do

  # copy lambda jar file.
  aws s3 cp makeshift-lambdas/target/makeshift-lambdas.jar s3://$S3_BUCKET/makeshift-requests/lambdas/ --acl public-read

  # Copy cloudformations
  pushd cloudformations;
  aws s3 cp . s3://$S3_BUCKET/makeshift-requests/cloudformations/ --recursive --acl public-read --content-type 'text/x-yaml' #--profile $PROFILE
  popd

done


## Update lambda code.
aws lambda update-function-code --function-name makeshift-aws-blog-invoke-step-functions-lambda --zip-file fileb:///Users/skkodali/work/Blogs-And-Artifacts/adhoc-requests-api/makeshift-lambdas/target/makeshift-lambdas.jar

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