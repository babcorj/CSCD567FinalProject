package videoSender;


/*
 * Copyright 2010-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.io.File;
import java.io.IOException;
//import java.util.NoSuchElementException;
import java.util.NoSuchElementException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;

import videoUtility.PerformanceLogger;
import videoUtility.S3UserStream;
import videoUtility.SharedQueue;

/**
 * This sample demonstrates how to make basic requests to Amazon S3 using the
 * AWS SDK for Java.
 * <p>
 * <b>Prerequisites:</b> You must have a valid Amazon Web Services developer
 * account, and be signed up to use Amazon S3. For more information on Amazon
 * S3, see http://aws.amazon.com/s3.
 * <p>
 * WANRNING:</b> To avoid accidental leakage of your credentials, DO NOT keep
 * the credentials file in your source directory.
 *
 * http://aws.amazon.com/security-credentials
 */
public class S3Uploader extends S3UserStream {

	private static String videoFolder;

	private String key;
	private String _indexFile;
	private SharedQueue<String> que;
	private AmazonS3 s3;
	private PerformanceLogger _logger;
	
	public S3Uploader(String bucket, SharedQueue<String> theque, String theVideoFolder){
		super(bucket);
		que = theque;
		videoFolder = theVideoFolder;
	}

    public void run() {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (/Users/ryanj/.aws/credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (/Users/ryanj/.aws/credentials), and is in valid format.",
                    e);
        }

        s3 = new AmazonS3Client(credentials);
        Region usWest2 = Region.getRegion(Regions.US_WEST_2);
        s3.setRegion(usWest2);

        System.out.println("===========================================");
        System.out.println("Getting Started with Amazon S3");
        System.out.println("===========================================\n");

        try { //start uploading video stream
            s3.listBuckets();
            /*
             * List the buckets in your account
             */
//            System.out.println("Listing buckets");
//            for (Bucket bucket : s3.listBuckets()) {
//                System.out.println(" - " + bucket.getName());
//            }
//            System.out.println();
            
            System.out.println("Uploading videostream to S3...\n");
            while(!isDone || !que.isEmpty()){
            	double timeReceived;
            	try{
            		key = que.dequeue();
            		timeReceived = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
            	} catch(NoSuchElementException e){
            		end();
            		continue;
            	}
            	try{
            		System.out.println("S3: Downloading file '" + key + "'");
            		File videoFile = loadVideoFile(key);
            		s3.putObject(new PutObjectRequest(bucketName, key, videoFile));

            		if(!key.equals(_indexFile)){
//	            		_logger.log("Finished sending " + key + " to S3: ");
						_logger.logTime();
						_logger.log("\n");
	
						double curRunTime = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
						double value = curRunTime - timeReceived;
	
	            		_logger.log("Total time to send " + key + ": ");
						_logger.log(value);
						_logger.log("\n");
            		}
					
            	} catch(IOException e) {
            		e.printStackTrace();
            	}
            	key = null;

            }
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with S3, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
            System.out.println("Current file to upload: " + key);
        } //end video stream

        System.out.println("S3 Uploader successfully closed");
    }

    /**
     * Loads the video file specified in fin
     *
     * @return The video file specified in fin.
     *
     * @throws IOException
     */
    private static File loadVideoFile(String fin) throws IOException {
        File file = new File(videoFolder + fin);
        if(!file.exists()){
        	throw new IOException("Cannot find file '" + fin + "'");
        }
        return file;
    }
    
    public void setKey(String fout){
    	key = fout;
    }
    
    //used to avoid logging index sending times
    public void setIndexFile(String indexfile){
    	_indexFile = indexfile;
    }
    
    public void setLogger(PerformanceLogger logger){
    	_logger = logger;
    }
    
    public void end(){
    	if(isDone) return;
    	System.out.println("Attempting to close S3 Uploader...");
    	while(!que.isEmpty()){
    		que.dequeue();
    	}
    	isDone = true;
    }

    public void delete(String file){
    	try{
    		s3.deleteObject(new DeleteObjectRequest(bucketName, file));
    	} catch(Exception e){
    		e.printStackTrace();
    	}
		System.out.println("Successfully deleted '"+file+"' from S3");
    }
}
