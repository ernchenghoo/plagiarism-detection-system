from matplotlib import pyplot as pt
import numpy as np
import cv2

img = cv2.imread("q4omega.tif",1)
img1 = cv2.imread("q4omega.tif",0)
cv2.imshow("q4omega", img)
cv2.waitKey()

img = cv2.cvtColor(img,cv2.COLOR_BGR2RGB)
new_img = img.copy()
new_img[:,:,0] = img[:,:,0]
new_img[:,:,1] = img[:,:,1]
new_img[:,:,2] = img[:,:,2]


cv2.imshow("Edited q4omega",new_img)
cv2.waitKey()