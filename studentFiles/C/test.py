import numpy as np
import cv2
from matplotlib import pyplot as pt

img = cv2.imread("omega_book.bmp",0)
img2 = cv2.equalizeHist(img)
new_image = np.concatenate((img,img2),1)
cv2.imshow("result",new_image)
cv2.waitKey()


hist = cv2.calcHist([img],[0],None,[256],[0,256])
pt.figure()
pt.subplot(1,2,1)
pt.title("Original omega book")
pt.xlabel("Bins")
pt.ylabel("Number of Pixels")
pt.plot(hist)
pt.xlim([0,256])
pt.show()


hist = cv2.calcHist([img],[0],None,[256],[0,256])
pt.figure()
pt.subplot(1,2,2)
pt.title("Edited omega book")
pt.xlabel("Bins")
pt.ylabel("Number of Pixels")
pt.plot(hist)
pt.xlim([0,256])
pt.show()
