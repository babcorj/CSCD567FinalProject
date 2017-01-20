package videoSender;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
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
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

import videoUtility.FileData;
import performance.PerformanceLogger;
import videoUtility.S3UserStream;
import videoUtility.SharedQueue;
import videoUtility.Utility;
import videoUtility.VideoSegment;

/**
 * 
 * @author Ryan Babcock
 * 
 * Used to upload video segments, playlist, and setup file to Amazon S3.
 * @see ICCRunner
 */

public class S3Uploader extends S3UserStream {

	private LinkedList<Double> 			_bitRateList;
	private PerformanceLogger 			_logger;
	private AmazonS3 					_s3;
	private SharedQueue<String> 		_signalQueue;
	private long 						_startTime;
	private SharedQueue<VideoSegment> 	_videoStream;
	private TransferManager 			_transferMGMT;
	
	//-------------------------------------------------------------------------
	//Constructor
	//-------------------------------------------------------------------------	
	public S3Uploader(SharedQueue<VideoSegment> theque){
		_videoStream = theque;
		_bitRateList = new LinkedList<>();
	}

	//-------------------------------------------------------------------------
	//Run method
	//-------------------------------------------------------------------------
	public void run() {
		//bit rate parameters
		long bytesSent = 0;
		short segmentsPlayed = 0;
		short segmentsToPlay = 5;
		long timeStart;
		
		/*
		 * The ProfileCredentialsProvider will return your [default]
		 * credential profile by reading from the credentials file located at
		 * (/Users/username/.aws/credentials).
		 *
		 * AWS IP occasionally changes. This will allow the application to receive
		 * and use new IP without querying DNS again with TTL being 60 
		 */
		java.security.Security.setProperty("networkaddress.cache.ttl", "60");

		AWSCredentials credentials = null;

		Runtime.getRuntime().addShutdownHook(new S3UploaderShutdownHook(this));

		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
				"Cannot load the credentials from the credential profiles file. " +
				"Please make sure that your credentials file is at the correct " +
				"location (/users/username/.aws/credentials), and is in valid format.", e);
		}

		_s3 = new AmazonS3Client(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		_s3.setRegion(usWest2);
		_transferMGMT = new TransferManager(_s3);

		System.out.println("Amazon S3: Preparation complete!");

		synchronized(_signalQueue){
			_signalQueue.enqueue("S3: Waiting for setup file...");
			try{
				_signalQueue.wait();				
			}catch(InterruptedException e){
				//empty
			}
		}

		String setupfile = _signalQueue.dequeue();
		uploadFile(setupfile);
		System.out.println("S3: Setup file successfully sent to S3");
		timeStart = System.currentTimeMillis();
		deleteAllSegments();
		
		//Continue to send video segments until end is called
		while(!_isDone){
			if(FileData.ISLOGGING){
				if(segmentsPlayed >= segmentsToPlay){
					recordBitRate(bytesSent,timeStart,10);
					timeStart = System.currentTimeMillis();//bitrate
					bytesSent = 0;
					segmentsPlayed = 0;
//				System.out.println("BitRate recorded");					
				}
			}
			ObjectMetadata info = new ObjectMetadata();
			VideoSegment segment;
			
			try { //start uploading video stream
				segment = _videoStream.dequeue();
				_key = segment.toString();
				
				System.out.println("S3: Uploading file '" + _key + "'...");
				
				info.setContentLength(segment.size());
				ByteArrayInputStream bin = new ByteArrayInputStream(segment.data());
				uploadSegment(bin, segment.size());
				
				_key = null;
				
				if(FileData.ISLOGGING){
					bytesSent += segment.size();//total bytes sent
					logUpload((System.currentTimeMillis() - _startTime)/1000.0);
					segmentsPlayed++;
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
				System.out.println("Current file to upload: " + _key);
			} catch (NoSuchElementException ie){
				System.err.println("No such element in video stream");
			} catch (Exception e){
				e.printStackTrace();
				System.err.println(e);
			}
		}//end while

		closeEverything();
		System.out.println("S3 Uploader successfully closed");
	}//end video stream

	//-------------------------------------------------------------------------
	//Set methods: All methods must be called before run.
	//-------------------------------------------------------------------------
	public void setLogger(PerformanceLogger logger){
		_logger = logger;
		_startTime = logger.getStartTime();
	}
	public void setSignal(SharedQueue<String> signal){
		_signalQueue = signal;
	}
	
	//-------------------------------------------------------------------------
	//Public methods
	//-------------------------------------------------------------------------
	/**
	 * Deletes the specified key within the S3 bucket.
	 * @param file		The file to be deleted from S3.
	 */
	public void delete(String file){
		while(!isDeleted(file)){
			try{
				_s3.deleteObject(new DeleteObjectRequest(_bucketName, file));
//				break;
			} catch(Exception e){
				e.printStackTrace();
				System.err.println("Deletion failed: " + file);
				Utility.pause(50);
				continue;
			}			
		}
		System.out.println("S3: Successfully deleted '"+file+"' from '" + _bucketName + "'");
	}
	
	/**
	 * Used to end the run method.
	 */
	public void end(){
		if(_isDone) return;
		System.out.println("Attempting to close S3 Uploader...");
		while(!_videoStream.isEmpty()){
			_videoStream.dequeue();
		}
		_isDone = true;
		this.interrupt();
	}

	public boolean isDeleted(String file){
		try{
			if(!_s3.doesObjectExist(_bucketName, file)){
				return true;
			}
		}catch(AmazonClientException ace){}
		return false;
	}
	
	//-------------------------------------------------------------------------
	//Private methods
	//-------------------------------------------------------------------------	
	private void closeEverything(){
		deleteAllSegments();
		if(!FileData.ISLOGGING) return;
		
		try{
			_logger.close();
			uploadLogFiles();
			_transferMGMT.shutdownNow(true);//true shutsdown s3 client too
		
		} catch(IOException e){
			System.err.println("Error writing logs");
			e.printStackTrace();
		}
	}
	
	private void deleteAllSegments(){
		String prefix = FileData.VIDEO_PREFIX;
		ObjectListing listing = _s3.listObjects(_bucketName, prefix);
		List<S3ObjectSummary> summaries = listing.getObjectSummaries();

		while (listing.isTruncated()) {
			   listing = _s3.listNextBatchOfObjects(listing);
			   summaries.addAll(listing.getObjectSummaries());
		}
		for(int i = 0; i < summaries.size(); i++){
			S3ObjectSummary s = summaries.get(i);
			String objKey = s.getKey();
			delete(objKey);
		}
	}
		
	/**
	 * Logs when the video segment was received.
	 * @param timeReceived		The time video was received, relative to start
	 * 							time specified in setup file.
	 */
	private void logUpload(double timeReceived){
		long currentTime = System.currentTimeMillis();
		double curRunTime = (currentTime - _startTime)/1000.0;
		double delay = curRunTime - timeReceived;

		try {
			_logger.logVideoTransfer(currentTime, delay);
		} catch (IOException e) {
			System.err.println("S3: Failed to log upload!");
			e.printStackTrace();
		}
	}
	
	private void recordBitRate(long bytes, long start, int size){
		double average = 0.0, totalBitRate = 0;
		double timePassed = (System.currentTimeMillis()-start)/1000.0;
		double bitRate = (bytes*8)/timePassed;
		byte[] bitRateStream;
		
		_bitRateList.addFirst(bitRate);
		if(_bitRateList.size() > size){
			_bitRateList.removeLast();
		}
		
		for(Double d : _bitRateList){
			totalBitRate += d;
		}
		average = totalBitRate/_bitRateList.size();

		bitRateStream = Double.toString(average).getBytes();
//		System.out.println("Bit rate: " + bitRate);
		ObjectMetadata info = new ObjectMetadata();
		ByteArrayInputStream input = new ByteArrayInputStream(bitRateStream);

		info.setContentLength(bitRateStream.length);
		PutObjectRequest request = new PutObjectRequest(_bucketName, FileData.BITRATE_FILE, input, info);
		_s3.putObject(request);
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	private void uploadLogFiles() throws IOException {
		String runnerLog = _signalQueue.dequeue();
		String runnerLogPath = _signalQueue.dequeue();
		String s3Log = _logger.getFileName();
		String s3LogPath = _logger.getFilePath();
		_s3.putObject(new PutObjectRequest(_bucketName, runnerLog, new File(runnerLogPath)));
		_s3.putObject(new PutObjectRequest(_bucketName, s3Log, new File(s3LogPath)));
	}
	
	/**
	 * 
	 * @param transferMGMT
	 * @param file
	 * 
	 * WARNING: If unable to upload, will spin forever!
	 */
	private void uploadFile(String file){
		try{
			PutObjectRequest request = new PutObjectRequest(_bucketName, file, new File(file));
			Upload upload = _transferMGMT.upload(request);
			while(!upload.isDone()){
				Utility.pause(10);
			}
		} catch(Exception e){
			System.err.println("S3: Failed to upload file: " + file);
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * 
	 * @param transferMGMT
	 * @param input
	 * @param key
	 * @param size
	 * 
	 * WARNING: If unable to upload, will spin forever!
	 */
	private void uploadSegment(InputStream input, long size){
		ObjectMetadata info = new ObjectMetadata();
		info.setContentLength(size);
		PutObjectRequest request = new PutObjectRequest(_bucketName, _key, input, info);
		Upload upload = _transferMGMT.upload(request);
		
		while(!upload.isDone()){
			Utility.pause(10);
		}
		System.out.println("S3: Uploaded: '" + _key + "'");
	}
}

//-----------------------------------------------------------------------------
//Shutdown Hook

/**
 *
 */
class S3UploaderShutdownHook extends Thread {
	private S3Uploader _uploader;

	public S3UploaderShutdownHook(S3Uploader uploader){
		_uploader = uploader;
	}

	public void run(){
		_uploader.end();
	}
}