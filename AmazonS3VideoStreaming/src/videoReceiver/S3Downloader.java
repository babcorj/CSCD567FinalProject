package videoReceiver;
import videoUtility.Utility;
import videoUtility.PerformanceLogger;
import videoUtility.S3UserStream;

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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;


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
public class S3Downloader extends S3UserStream {

	private AmazonS3 s3;
	private PerformanceLogger _logger;
	private StreamIndexParser parser;
	private String output;
	private String prefix;
	private String STREAMINDEX = "StreamIndex.txt";
	private VideoStream stream;
	
	public S3Downloader(String bucket, String prefix, String output, VideoStream stream) {
		super(bucket);
		this.output = output;
		this.stream = stream;
		this.prefix = prefix;
	}

	@Override
	public void run() {

		/*
		 * The ProfileCredentialsProvider will return your [default] credential
		 * profile by reading from the credentials file located at
		 * (/Users/ryanj/.aws/credentials).
		 */

		//---------------------------------------------------------------------
		//Verify Amazon Credentials

		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
		} catch (Exception e) {
			System.err.println("Cannot load the credentials from the credential profiles file. "
					+ "\nPlease make sure that your credentials file is at the correct "
					+ "location (/Users/username/.aws/credentials), and is in valid format.");
			System.exit(-1);
		}

		s3 = new AmazonS3Client(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		s3.setRegion(usWest2);

		System.out.println("===========================================");
		System.out.println("Getting Started with Amazon S3");
		System.out.println("===========================================\n");

		
		//---------------------------------------------------------------------
		//Retreive the playlist

		parser = null;
		while(parser == null) {
			try {	            
				System.out.println("\nAttempting to obtain playlist...");
				parser = new StreamIndexParser(prefix, getTemporaryFile(STREAMINDEX));
			} catch (IOException e) {
				System.err.println("Failed to retrieve playlist!");
				Utility.pause(500);
				continue;
			}
		}
		System.out.println("Playlist obtained!");

		//---------------------------------------------------------------------
		//Get video stream
		
		double timeRequested = 0;
		File videoFile = null;
		System.out.println("Obtaining videostream from S3...\n");

		while (!isDone) {
			try{
				timeRequested = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
				key = parser.parse(getTemporaryFile(STREAMINDEX));
				videoFile = getTemporaryFile(key);
			}
			catch(IOException ioe){
				System.err.println("Failed to parse playlist!");
				continue;
			}
			catch(IndexOutOfBoundsException e){
				Utility.pause(100);
				continue;
			}
			catch (AmazonServiceException ase) {
				System.out.println("Caught an AmazonServiceException, which means your request made it "
						+ "to Amazon S3, but was rejected with an error response for some reason.");
				System.out.println("Error Message:    " + ase.getMessage());
				System.out.println("HTTP Status Code: " + ase.getStatusCode());
				System.out.println("AWS Error Code:   " + ase.getErrorCode());
				System.out.println("Error Type:       " + ase.getErrorType());
				System.out.println("Request ID:       " + ase.getRequestId());
				System.out.println("Key:	           '" + key + "'");
			}
			catch (AmazonClientException ace) {
				System.out.println("Caught an AmazonClientException, which means the client encountered "
						+ "a serious internal problem while trying to communicate with S3, "
						+ "such as not being able to access the network.");
				System.out.println("Error Message: " + ace.getMessage());
			}
			System.out.println("Downloading file: " + key);
			stream.add(videoFile.getAbsolutePath());
			try{
	    		if(!key.equals(STREAMINDEX)){
	//        		_logger.log("Finished sending " + key + " to S3: ");
					_logger.logTime();
					_logger.log("\n");
	
					double curRunTime = (double)((System.currentTimeMillis() - _logger.getTime())/1000);
					double value = curRunTime - timeRequested;
	
	        		_logger.log("Total time to download " + key + ": ");
					_logger.log(value);
					_logger.log("\n");
	    		}
			} catch(IOException e){
				System.err.println();
			}
		}

		Utility.pause(500);
		deleteTempFiles(videoFile);
		System.out.println("S3 Downloader successfully closed");
	}

	public void setKey(String fout) {
		key = fout;
	}
	
	public void setPerformanceLog(PerformanceLogger perfLog){
		_logger = perfLog;
	}
	
	public void end() {
    	if(isDone) return;
    	System.out.println("Attempting to close S3 Downloader...");
    	while(!stream.isEmpty()){
    		stream.getFrame();
    	}
    	isDone = true;
	}
	
	private File getTemporaryFile(String key) throws IOException {
		
		byte[] buffer;
		File file = null;

		S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));

//		System.out.println(
//				"Content-Type: " + object.getObjectMetadata().getContentType());

		DataInputStream instream = new DataInputStream(object.getObjectContent());
		buffer = new byte[instream.available()];

		file = new File(String.format(output + "%s.tmp", key));
		FileOutputStream outstream = new FileOutputStream(file);

		int read = 0;
		while ((read = instream.read(buffer)) != -1) {
			outstream.write(buffer, 0, read);
		}
		instream.close();
		outstream.close();

		return file;
	}

	/*
	 * designed to delete the temporary video file and index file
	 */
    private void deleteTempFiles(File videoFile){
    	try{
    		File toDelete = new File(STREAMINDEX + ".tmp");
    		toDelete.delete();
    		videoFile.delete();
    	} catch(Exception e){
    		System.err.println("There was an error deleting the temporary files");
    	}
    }
}
