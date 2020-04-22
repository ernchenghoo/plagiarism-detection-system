import numpy as np
import cv2
from matplotlib import pyplot as pt

img = cv2.imread("airplane.bmp",0)
img2 = cv2.equalizeHist(img)
new_image = np.concatenate((img,img2),1)
cv2.imshow("result",new_image)
cv2.waitKey()