

import numpy as np
import cv2
from matplotlib import pyplot as pt



circuit = cv2.imread("omega_circuit.bmp",0)
circuit_after=circuit.copy()
circuit_after[0:512,0:64]=cv2.medianBlur(circuit[0:512,0:64],5)
circuit_after[0:512,384:512]=cv2.medianBlur(circuit[0:512,384:512],5)
circuit_after[0:64,0:512]=cv2.medianBlur(circuit[0:64,0:512],5)
circuit_after[384:512,0:512]=cv2.medianBlur(circuit[384:512,0:512],5)

#use pyplot to show all 

pt.figure()
pt.subplot(1,2,1)
pt.imshow(circuit, cmap="gray")
pt.title("Original Image")
pt.subplot(1,2,2)
pt.imshow(circuit_after, cmap="gray")
pt.title("Median 5x5 Image")