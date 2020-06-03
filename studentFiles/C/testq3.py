import numpy as np
import cv2
from matplotlib import pyplot as pt

img = cv2.imread('omega_circuit.bmp')
median = cv2.medianBlur(img,5)


pt.subplot(121),pt.imshow(img),pt.title('Original')
pt.xticks([]), pt.yticks([])
pt.subplot(122),pt.imshow(median),pt.title('Without noise')
pt.xticks([]), pt.yticks([])

pt.show()


cv2.waitKey()
