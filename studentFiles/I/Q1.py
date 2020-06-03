import numpy as np
import cv2
from matplotlib import pyplot as pt



airplane = cv2.imread("airplane.bmp", 1)




b = airplane[:,:,0]

g = airplane[:,:,1]

r = airplane[:,:,2]

pt.figure()
pt.subplot(2,2,1)
pt.imshow(r)
pt.title("Red")
pt.subplot(2,2,2)
pt.imshow(g)
pt.title("Green")
pt.subplot(2,2,3)
pt.imshow(b)
pt.title("Blue")