# HRDetectionAndroidApp
Android app that analyzes heart rate using the phone's camera from scratch

Here’s how I calculate heart rate:

1. For every image frame, we take out a region of interest from the center of the frame, separate its color channels and average the red channel.
2. This average of the red channel is inserted into a buffer that stores the average red channel values of 125 frames (approximately 5 seconds).
3. Every 125 frames (5 seconds), we calculate heart rate by:
	(a) Demean average red channel values in buffer.
	(b) Carry out median filtering with window size = 2 or window size = 4 (give similar results). 
	(c) Carry out zero crossing. Let number of zero crossings = “numCrossing”
	(d) HR = (numCrossing/2)*(60/5);
	(e) Display this HR in TextView
4. Additionally:
	(a) Current HR is added to the plot every 2 seconds. 
	(b) When finger is not in front of camera, average red channel value of frame should be less than 200. Whenever this happens, we switch TextView back to “Initializing”
	(c) “Initializing” is also visible in the beginning because it often takes a few seconds to start getting valid HR readings. 

Region of Interest “rectangle”:
“width”: width of whole image frame, “height”: height of whole image frame
X = (width/2 - width/4)
Y = (height/2 - height/4)
W = width/4 OR width/2
H = height/4 OR heigh/2 


VIDEO and ISSUES:

My resting heart according to an iPhone app should be around 72. As you can see in the video, my app also detects 72, however it’s not very stable. I could try to take a moving average of the heart rate and display that in order to stabilize it. Over a period of time, average heart rate detected by my app should be stable. 

NOTE: The following file could NOT be uploaded to github due to it's size. You'll need to re-install the opencv sdk: HeartRateMain/app/libs/OpenCV-android-sdk/sdk/native/3rdparty/libs/x86_64/libippicv.a