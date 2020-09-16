package com.dataanalytics.awsblogs;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsAsyncClientBuilder;
import com.amazonaws.services.stepfunctions.builder.StateMachine;
import com.amazonaws.services.stepfunctions.builder.StepFunctionBuilder;
import com.amazonaws.services.stepfunctions.model.StartExecutionRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;

public class InvokeStepFunctionsLambda implements RequestStreamHandler {
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
            //logger.log("event data: " + gson.toJson(event));
            JsonObject contextObject = gson.fromJson(gson.toJson(event.get("context")), JsonObject.class);
            String cognitoAppClientId = contextObject.get("cognito-identity-id").toString();
            String requestId = contextObject.get("request-id").toString();

            logger.log("cognito-identity-id: " + cognitoAppClientId);
            logger.log("request-id: " + requestId);

            String stepFunctionInputData = "{\"cognitoId\" : "+ cognitoAppClientId +","
                    +  "\"requestId\":" + requestId +","
                    +  "\"tagCreatedBy\":" + "{\"Key\" : \"create-by\", \"Value\" : \"makeshift-demo\"}" + ","
                    +  "\"tagCostCenter\":" + "{\"Key\": \"cost-center\", \"Value\" : \"" + getParameter(cognitoAppClientId) + "\"}"
                    +"}";

            //invoke step function
            AWSStepFunctionsAsyncClientBuilder
                    .standard()
                    .build().startExecution(
                            new StartExecutionRequest()
                                    .withStateMachineArn(System.getenv("StateMachineArn"))
                                    .withInput(stepFunctionInputData)
            );
            String jsonRequestString = "{request_id :"+requestId+"}";
            writer.write(gson.toJson(jsonRequestString));
            if (writer.checkError())
            {
                logger.log("WARNING: Writer encountered an error.");
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
    /**
     * Helper method to retrieve SSM Parameter's value
     * @param parameterName identifier of the SSM Parameter
     * @return decrypted parameter value
     */
    public static String getParameter(String parameterName) {
        AWSSimpleSystemsManagement ssm = AWSSimpleSystemsManagementClientBuilder.defaultClient();
        GetParameterRequest request = new GetParameterRequest();
        request.setName(parameterName.replace("\"", ""));
        request.setWithDecryption(true);
        return ssm.getParameter(request).getParameter().getValue();
    }
}
