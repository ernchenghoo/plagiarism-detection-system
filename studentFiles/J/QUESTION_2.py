import numpy as np
import cv2
from matplotlib import pyplot as pt

#question 2

airplane = cv2.imread("airplane.bmp", 1)
r = airplane[:,:,0]
g = airplane[:,:,1]
b = airplane[:,:,2]

pt.figure()
pt.subplot(1,3,1)
pt.imshow(r)
pt.title("RED")

pt.subplot(1,3,2)
pt.imshow(g)
pt.title("GREEN")

pt.subplot(1,3,3)
pt.imshow(b)
pt.title("BLUE")

pt.show()


