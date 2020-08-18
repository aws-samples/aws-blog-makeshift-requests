package com.dataanalytics.awsblogs;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GetStatusLambda implements RequestStreamHandler {

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException
    {
        LambdaLogger logger = context.getLogger();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("US-ASCII")));
        PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, Charset.forName("US-ASCII"))));
        String ddbTableName = "makeshift-jobstatus-table";
        String jsonRequestString = "JobStatus";
        try
        {
            HashMap event = gson.fromJson(reader, HashMap.class);
            logger.log("event data: " + gson.toJson(event));
            JsonObject contextObject = gson.fromJson(gson.toJson(event.get("context")), JsonObject.class);
            String cognitoAppClientId = contextObject.get("cognito-identity-id").toString();
            String requestId = contextObject.get("request-id").toString();
            String jobId = null;
            try{
                JsonObject paramsObject = gson.fromJson(gson.toJson(event.get("params")), JsonObject.class);
                JsonObject querystringObject = (JsonObject) paramsObject.get("querystring");
                jobId = querystringObject.get("jobid").toString();
                System.out.println("jobId=" + jobId);
            }catch (NullPointerException e){
                System.out.println("No jobId provided");
            }

            logger.log("cognito-identity-id: " + cognitoAppClientId + "\n");
            logger.log("request-id: " + requestId + "\n");

            try{
                final AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.defaultClient();
                if(jobId != null){
                    String pKey = jobId.replaceAll("^\"+|\"+$", "");
                    HashMap<String,AttributeValue> key_to_get = new HashMap<String,AttributeValue>();
                    key_to_get.put("JobID", new AttributeValue(pKey));
                    logger.log("key_to_get :" + key_to_get.toString() + "\n" + "Dynamo Table Name: " + ddbTableName + "\n");
                    Map<String,AttributeValue> returned_item = ddb.getItem(new GetItemRequest(ddbTableName,key_to_get)).getItem();
                    if (returned_item != null) {
                        Set<String> keys = returned_item.keySet();
                        for (String key : keys) {
                            System.out.format("%s: %s\n",
                                    key, returned_item.get(key).toString());
                            jsonRequestString= jsonRequestString + " " +key+" "+returned_item.get(key).toString()+" ";
                        }
                    }
                } else {
                    System.out.format("No item found with the key %s!\n", cognitoAppClientId);
                    String val = cognitoAppClientId.replaceAll("^\"+|\"+$", "");
                    System.out.println("cognitoAppClientId: " + val);
                    Map<String, AttributeValue> expressionAttributeValues =
                            new HashMap<String, AttributeValue>();
                    expressionAttributeValues.put(":val", new AttributeValue().withS(val));

                    Map<String, Condition> items_to_scan = new HashMap<>();
                    ScanRequest scanRequest = new ScanRequest()
                            .withTableName(ddbTableName)
                            .withFilterExpression("CognitoID = :val")
                            .withExpressionAttributeValues(expressionAttributeValues);
                    System.out.println(scanRequest.toString());

                    ScanResult result = ddb.scan(scanRequest);
                    System.out.println(result.toString());
                    for (Map<String, AttributeValue> item : result.getItems()) {
                        System.out.println(item.toString());
                        jsonRequestString= jsonRequestString + " " +item.toString()+" ";
                    }
                }
            }
            catch (DynamoDBMappingException | NullPointerException | ResourceNotFoundException e){
                System.out.println(e.toString());
            }

            writer.write(gson.toJson(jsonRequestString));
            if (writer.checkError())
            {
                logger.log("WARNING: Writer encountered an error.");
            }
        }
        catch (IllegalStateException | JsonSyntaxException exception)
        {
            System.out.println(exception.toString());
            logger.log(exception.toString());
        }
        finally
        {
            reader.close();
            writer.close();
        }
    }
}
