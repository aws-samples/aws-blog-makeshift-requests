package com.dataanalytics.awsblogs;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.AddTagsRequest;
import com.amazonaws.services.elasticmapreduce.model.AmazonElasticMapReduceException;
import com.amazonaws.services.elasticmapreduce.model.ClusterSummary;
import com.amazonaws.services.elasticmapreduce.model.DescribeClusterRequest;
import com.amazonaws.services.elasticmapreduce.model.DescribeClusterResult;
import com.amazonaws.services.elasticmapreduce.model.ListClustersResult;
import com.amazonaws.services.elasticmapreduce.model.ListStepsRequest;
import com.amazonaws.services.elasticmapreduce.model.ListStepsResult;
import com.amazonaws.services.elasticmapreduce.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;

public class UpdateStatusLambda implements RequestHandler<StepRequestData, String> {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @Override
    public String handleRequest(StepRequestData event, Context context)
    {
        LambdaLogger logger = context.getLogger();
        // process event
        logger.log("EVENT: " + gson.toJson(event));

        String cognitoId = event.getCognitoId();
        String requestId = event.getRequestId();

        String stepId = "";
        String stepStatus = "waiting";
        String clusterId ="";

        try{
            AmazonElasticMapReduce emr = AmazonElasticMapReduceClientBuilder.defaultClient();
            ListClustersResult listClustersResult = emr.listClusters().withClusters(new ClusterSummary().withName(event.getRequestId()));
            clusterId = listClustersResult.getClusters().get(0).getId();

            DescribeClusterResult describeClusterResult = emr.describeCluster( new DescribeClusterRequest().withClusterId(clusterId));

            if( !describeClusterResult.getCluster().getStatus().getState().equalsIgnoreCase("terminated")
                    && describeClusterResult.getCluster().getTags().size() <= 1 ){
                List<Tag> tags = new ArrayList<>();
                tags.add(new Tag("Cost_Center",getParameter(cognitoId)));
                emr.addTags(new AddTagsRequest(clusterId,tags ));
            }

            ListStepsResult listStepsResult = emr.listSteps(new ListStepsRequest().withClusterId(clusterId));
            if(!listStepsResult.getSteps().isEmpty()){
                stepId = listStepsResult.getSteps().get(0).getId();
                stepStatus = listStepsResult.getSteps().get(0).getStatus().getState();
            }

        }catch (AmazonElasticMapReduceException e){
            System.out.println(e.toString());
        }

        try{
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();

            DynamoDBMapper mapper = new DynamoDBMapper(client);
            JobStatusConfig item = new JobStatusConfig();
            item.setJobID(requestId);
            item.setCognitoID(cognitoId);
            item.setClusterID(clusterId);
            item.setStepID(stepId);
            item.setStepStatusState(stepStatus);
            mapper.save(item);
        }
        catch (DynamoDBMappingException e){
            System.out.println(e.toString());
            return "failed to update table";
        }
        return stepStatus;
    }

    /**
     * Helper method to retrieve SSM Parameter's value
     * @param parameterName identifier of the SSM Parameter
     * @return decrypted parameter value
     */
    public static String getParameter(String parameterName) {
        AWSSimpleSystemsManagement ssm = AWSSimpleSystemsManagementClientBuilder.defaultClient();
        GetParameterRequest request = new GetParameterRequest();
        request.setName(parameterName);
        request.setWithDecryption(true);
        return ssm.getParameter(request).getParameter().getValue();
    }
}