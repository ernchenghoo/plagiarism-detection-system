"""
from matplotlib import pyplot as pt
import numpy as np
import cv2

#Question Number 1
build = cv2.imread("omega_book.bmp", 0)
build_eq = cv2.equalizeHist(build)
hist = cv2.calcHist([build],[0],None,[256],[0,256])
hist_eq = cv2.calcHist([build_eq],[0],None,[256],[0,256])


pt.figure()

pt.subplot(2,2,1)
pt.imshow(build, cmap="gray")
pt.title("Before")

pt.subplot(2,2,2)
pt.imshow(build, cmap="gray")
pt.title("After")

pt.subplot(2,2,3)
pt.plot(hist)
pt.title("Histogram")
pt.xlim([0,256])

pt.subplot(2,2,4)
pt.plot(hist_eq)
pt.title("Histogram")
pt.xlim([0,256])
pt.show()
"""