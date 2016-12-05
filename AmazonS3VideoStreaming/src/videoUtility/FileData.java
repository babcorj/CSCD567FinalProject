package videoUtility;

/**
 *
 * @author Ryan Babcock
 *
 * Contains universal data that is used in this project.
 * 
 */

public enum FileData {
	BUCKET				("icc-videostream-00"),
	INDEXFILE 			("playlist.txt"),
	ISLOGGING			(false),
	LOG_DIRECTORY 		("log/"),
	PLAYER_LOG			("VideoPlayerLog.txt"),
	S3UPLOADER_LOG		("S3UploaderLog.txt"),
	S3DOWNLOADER_LOG	("S3DownloaderLog.txt"),
	SCRIPTFILE			("inScript.p"),
	SENDER_LOG			("VideoSenderLog.txt"),
	SETUP_FILE 			("setup.txt"),
	VIDEO_FOLDER		("./videos/"),
	VIDEO_PREFIX 		("myvideo"),
	VIDEO_SUFFIX 		("");

	private String _value;
	private boolean _bool;

	FileData(String str){
		_value = str;
	}
	FileData(Boolean bool){
		_bool = bool;
	}
	public String print() { return _value; }
	public static String print(FileData data){
		return data.print();
	}
	public boolean isTrue() { return _bool; }
	public static boolean isTrue(FileData data){
		return data.isTrue();
	}
}
