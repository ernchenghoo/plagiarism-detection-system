# -*- coding: utf-8 -*-
"""
Spyder Editor

This is a temporary script file.
"""
from matplotlib import pyplot as pt
import numpy as np
import cv2


img = cv2.imread("omega_book.bmp",0)
img2 = cv2.equalizeHist(img)
new_image = np.concatenate((img,img2),1)
cv2.imshow("result",new_image)
cv2.waitKey()

hist = cv2.calcHist([img],[0],None,[256],[0,256])
pt.figure()
pt.subplot(1,2,1)
pt.title("Original omega_book")
pt.xlabel("Bins")
pt.ylabel("Number of Pixels")
pt.plot(hist)
pt.xlim([0,256])
pt.show()

hist2 = cv2.calcHist([img2],[0],None,[256],[0,256])
pt.subplot(1,2,2)
pt.title("Edited omega_book")
pt.xlabel("Bins")
pt.ylabel("Number of Pixels")
pt.plot(hist)
pt.xlim([0,256])
pt.show()

