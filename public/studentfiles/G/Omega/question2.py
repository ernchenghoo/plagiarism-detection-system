import cv2
import numpy as np
from matplotlib import pyplot as pt

airplane = cv2.imread("airplane.bmp")

ex_airplane = airplane.copy()


airplane_R = np.zeros((512,512),dtype=np.uint32)
airplane_G = np.zeros((512,512),dtype=np.uint32)
airplane_B = np.zeros((512,512),dtype=np.uint32)


airplane_R = ex_airplane[0:512,0:512,0]
airplane_G = ex_airplane[0:512,0:512,1]
airplane_B = ex_airplane[0:512,0:512,2]


hist1 = cv2.calcHist(airplane,[0],None,[256],[0,256])
hist2 = cv2.calcHist(airplane,[1],None,[256],[0,256])
hist3 = cv2.calcHist(airplane,[2],None,[256],[0,256])


pt.subplot(1,3,1)
pt.title("Red channel")
pt.plot(hist1)

pt.subplot(1,3,2)
pt.title("Green channel")
pt.plot(hist2)

pt.subplot(1,3,3)
pt.title("Blue channel")
pt.plot(hist3)