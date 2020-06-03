from matplotlib import pyplot as pt
import numpy as np
import cv2

img = cv2.imread("omega_book.bmp",0)

img = img * 2
img = img - 33

img2 = cv2.equalizeHist(img)

img3 = cv2.calcHist([img],[0],None,[256],[0,256])
img4 = cv2.calcHist([img2],[0],None,[256],[0,256])

pt.figure()

pt.subplot(2,2,1)
pt.title("Adjusted")
pt.imshow(img,cmap="gray")

pt.subplot(2,2,2)
pt.imshow(img,cmap="gray")
pt.title("Equalized")

pt.subplot(2,2,3)
pt.plot(img3)
pt.title("H_adjusted")

pt.subplot(2,2,4)
pt.plot(img4)
pt.title("H_equalized")

pt.show()