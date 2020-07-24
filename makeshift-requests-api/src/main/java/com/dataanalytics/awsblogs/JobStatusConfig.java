package com.dataanalytics.awsblogs;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


@DynamoDBTable(tableName = "JobStatus")
public class JobStatusConfig {

    private String JobID;
    private String ClusterID;
    private String StepID;
    private String StepStatusState;

    @DynamoDBHashKey(attributeName = "JobID")
    public String getJobID() {
        return JobID;
    }
    public void setJobID(String jobID) {
        JobID = jobID;
    }

    @DynamoDBAttribute(attributeName = "ClusterID")
    public String getClusterID() {
        return ClusterID;
    }
    public void setClusterID(String clusterID) {
        ClusterID = clusterID;
    }

    @DynamoDBAttribute(attributeName = "StepID")
    public String getStepID() {
        return StepID;
    }
    public void setStepID(String stepID) {
        StepID = stepID;
    }

    @DynamoDBAttribute(attributeName = "StepStatusState")
    public String getStepStatusState() {
        return StepStatusState;
    }
    public void setStepStatusState(String stepStatusState) {
        StepStatusState = stepStatusState;
    }
}
