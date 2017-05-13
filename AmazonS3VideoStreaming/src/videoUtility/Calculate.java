package videoUtility;

import java.awt.Point;
import java.util.List;
import java.util.Random;

public class Calculate {

	/* calculates the average based on the absolute value of
	 * a sample. This method is concerned with finding the 
	 * average of a difference.
	 * 
	 * @return	the average of the absolute values within samples
	 */
	public static double average(List<Double> samples){
		if(samples.size() == 0)
			return 0;

		double average = 0.0;

		for(Double d : samples){
			average += Math.abs(d);
		}

		average = average/samples.size();
		return average;
	}
	
	public static double determineRandomValue(double minValue, double maxValue, Random rand){
		double value = rand.nextDouble() * (minValue + maxValue);
		if(value < minValue){
			return minValue;
		} else if(value > maxValue){
			return maxValue;
		}
		return value;
	}
	
	public static double max(List<Double> collection){
		if(collection.size() == 0)
			return 0;

		double max = 0.0;

		for(Double d : collection){
			if(d > max){
				max = d;
			}
		}
		return max;
	}

	//Assumes the hypotenuse is unknown
	public static double sinDegree(double height, double length){
		double incline = Math.sqrt(Math.pow(height, 2) + Math.pow(length, 2)); //hypotenuse
		double linearPitchSin = height/incline;
		double linearPitchRadians = Math.asin(linearPitchSin);
		double targetDegree = Math.toDegrees(linearPitchRadians);
		return targetDegree;
	}

	/** Returns a magnitude between the target point and the current direction
	 * 
	 * @param p1
	 * @param p2
	 * @param p1DirectionDegree
	 * @return A negative result means the target point is to the left, a
	 * 			positive result means the target point is to the right
	 */
	public static double compareToTarget(Point p1, Point p2, double p1DirectionDegree){
		int rotate = 1;
		int length;
		double targetDegree;
		double distance = Calculate.euclideanDistance(p1.x, p1.y, p2.x, p2.y);
		direction compass = Calculate.compass(p1, p2);

		if(compass == direction.NORTH || compass == direction.SOUTH){
			length = Math.abs(p1.x - p2.x);
			if((p1.x > p2.x)){
				rotate = -1;
			}
		}
		else{
			length = Math.abs(p1.y - p2.y);
			if((p1.y < p2.y) && (p1.x < p2.x)){
				rotate = -1;
			}
		}
		targetDegree = sinDegree(length, distance);

		if(rotate < 1){
			targetDegree = 360 - targetDegree;
		}
		double degreeDifference = Math.abs(p1DirectionDegree - targetDegree);
		
		if(degreeDifference > 180)
			degreeDifference = 360 - degreeDifference;
		
		return degreeDifference * rotate;
	}

	public static double euclideanDistance(double x1, double y1, double x2, double y2){
		double xvar = Math.pow((x1 - x2), 2);
		double yvar = Math.pow((y1 - y2), 2);
		return Math.sqrt(xvar + yvar);
	}

	public static double standardDeviation(double average, List<Double> record){
		assert(record != null);
		double stdDev = 0;

		for(double d : record){
			stdDev += Math.abs(average - d);
		}
		return stdDev/record.size();
	}

	public static double area(double[] heightRecord, double setWidth){
		double area = 0;

		for(double d : heightRecord){
			area += Math.abs(d * setWidth);
		}
		return area;
	}

	public static enum direction{
		NORTH, EAST, SOUTH, WEST;
	}

	public static direction compass(Point from, Point to){
		int distanceX = Math.abs(from.x - to.x);
		int distanceY = Math.abs(from.y - to.y);
		
		if(from.x < to.x && distanceX > distanceY){
			return direction.EAST;
		}else if(from.x > to.x && distanceX > distanceY){
			return direction.WEST;
		}else if(from.y < to.y && distanceY > distanceX){
			return direction.NORTH;
		}else if(from.y > to.y && distanceY > distanceX){
			return direction.SOUTH;
		}
		return null;
	}
	
	public static direction compass(double degree){
		if(degree <= 45 || degree > 315){
			return direction.EAST;
		}else if(degree <= 225 && degree > 135){
			return direction.WEST;
		}else if(degree <= 135 && degree > 45){
			return direction.NORTH;
		}else if(degree <= 315 && degree > 225){
			return direction.SOUTH;
		}
		return null;
	}
	
	public static direction eastWest(double degree){
		if(degree <= 90 || degree > 270){
			return direction.EAST;
		}else{
			return direction.WEST;
		}
	}
}
