from matplotlib import pyplot as pt
import numpy as np
import cv2

airplane = cv2.imread("airplane.bmp", 1)

b = airplane[:,:,0]
g = airplane[:,:,1]
r = airplane[:,:,2]

b_hist = cv2.calcHist([b],[0],None,[256],[0,256])
g_hist = cv2.calcHist([g],[0],None,[256],[0,256])
r_hist = cv2.calcHist([r],[0],None,[256],[0,256])

pt.figure()
pt.subplot(1,3,1)
pt.title("Blue Channel")
pt.xlabel("Bins")
pt.ylabel("Count")
pt.plot(b_hist, 'b')
pt.xlim([0,256])

pt.subplot(1,3,2)
pt.title("Green Channel")
pt.xlabel("Bins")
pt.ylabel("Count")
pt.plot(g_hist, 'g')
pt.xlim([0,256])

pt.subplot(1,3,3)
pt.title("Red Channel")
pt.xlabel("Bins")
pt.ylabel("Count")
pt.plot(r_hist, 'r')
pt.xlim([0,256])

cv2.imshow("Blue of Airplane.bmp",b)

cv2.imshow("Green of Airplane.bmp",g)

cv2.imshow("Red of Airplane.bmp",r)

pt.show()
