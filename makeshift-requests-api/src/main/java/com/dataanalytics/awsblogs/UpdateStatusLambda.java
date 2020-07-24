package com.dataanalytics.awsblogs;


import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.SourceTableDetails;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.AddTagsRequest;
import com.amazonaws.services.elasticmapreduce.model.AddTagsResult;
import com.amazonaws.services.elasticmapreduce.model.DescribeClusterRequest;
import com.amazonaws.services.elasticmapreduce.model.DescribeClusterResult;
import com.amazonaws.services.elasticmapreduce.model.ListStepsRequest;
import com.amazonaws.services.elasticmapreduce.model.ListStepsResult;
import com.amazonaws.services.elasticmapreduce.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsAsyncClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateStatusLambda implements RequestStreamHandler {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException
    {
        LambdaLogger logger = context.getLogger();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("US-ASCII")));
        PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, Charset.forName("US-ASCII"))));
        try
        {
            HashMap event = gson.fromJson(reader, HashMap.class);
            logger.log("event data: \n" + gson.toJson(event));

            // convert the JSON data in json object
            JsonObject outputData = gson.fromJson(gson.toJson(event.get("APIGatewayData")), JsonObject.class);
            JsonObject emrData = gson.fromJson(gson.toJson(event.get("EMR")), JsonObject.class);

            System.out.println(outputData.get("cognitoId").toString());
            System.out.println(emrData.get("ClusterId").toString().replace("\"",""));

            String cognitoId = outputData.get("cognitoId").toString().replace("\"","");
            String clusterId = emrData.get("ClusterId").toString().replace("\"","");

            AmazonElasticMapReduce emr = AmazonElasticMapReduceClientBuilder.defaultClient();

            DescribeClusterResult describeClusterResult = emr.describeCluster( new DescribeClusterRequest().withClusterId(clusterId));

            if( !describeClusterResult.getCluster().getStatus().getState().equalsIgnoreCase("terminated")
                    && describeClusterResult.getCluster().getTags().isEmpty()){
                List<Tag> tags = new ArrayList<>();
                tags.add(new Tag("Cost_Center_lambda",cognitoId ));
                emr.addTags(new AddTagsRequest(clusterId,tags ));
            }

            String stepId = "";
            String stepStatus = "";

           ListStepsResult listStepsResult = emr.listSteps(new ListStepsRequest().withClusterId(clusterId));
           if(!listStepsResult.getSteps().isEmpty()){
               stepId = listStepsResult.getSteps().get(0).getId();
               stepStatus = listStepsResult.getSteps().get(0).getStatus().getState();
           }

           try{AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();

               DynamoDBMapper mapper = new DynamoDBMapper(client);
               JobStatusConfig item = new JobStatusConfig();
               item.setJobID(cognitoId);
               item.setClusterID(clusterId);
               item.setStepID(stepId);
               item.setStepStatusState(stepStatus);
               mapper.save(item);
           }
           catch (DynamoDBMappingException e){
               System.out.println(e.toString());
           }

        }
        catch (IllegalStateException | JsonSyntaxException exception)
        {
            logger.log(exception.toString());
        }
        finally
        {
            reader.close();
            writer.close();
        }
    }
}
