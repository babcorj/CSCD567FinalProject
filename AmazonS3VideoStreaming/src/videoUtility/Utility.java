package videoUtility;

import java.text.DecimalFormat;

public class Utility {

	public Utility(){}
	
	public static void pause(long milliseconds){
		try{
			Thread.sleep(milliseconds);
		} catch(InterruptedException e){}
	}
	
	public static double round(double d, int decimalPlace){
		assert(decimalPlace > 1);
		
		String decimalNum = ".";
		for(int i = 0; i < decimalPlace; i++){
			decimalNum += "#";
		}
		if(decimalNum.length() > 0){
			DecimalFormat df = new DecimalFormat("#" + decimalNum);
			d = Double.parseDouble(df.format(d));
		}
		else d = Math.round(d);

		return d;
	}
	
	public static String print(byte[] data){
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < data.length; i++){
			if(i % 15 == 0) sb.append("\n");
			sb.append(i + ": " + data[i] + " ");
		}
		return sb.toString();
	}
}
