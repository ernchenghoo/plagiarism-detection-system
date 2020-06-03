import numpy as np
import cv2
from matplotlib import pyplot as pt

img = cv2.imread("omega_circuit.bmp",0)

img_after= img.copy()

img_after[0:65,:]=cv2.medianBlur(img[0:65,:],5)

img_after[450:510,:]=cv2.medianBlur(img[450:510,:],5)

img_after[:,0:65]=cv2.medianBlur(img[:,0:65],5)

img_after[:,450:510]=cv2.medianBlur(img[:,450:510],5)


pt.figure()
pt.subplot(1,2,1)
pt.imshow(img, cmap="gray")
pt.title("Original Image")

pt.subplot(1,2,2)
pt.imshow(img_after, cmap="gray")
pt.title("Median fFilter with 5x5")
