import cv2
import numpy as np
from matplotlib import pyplot as pt


circuit = cv2.imread("omega_circuit.bmp",0)

filtered_circuit = cv2.medianBlur(circuit,5)

pt.subplot(1,2,1)
pt.imshow(circuit,cmap="gray")
pt.subplot(1,2,2)
pt.imshow(filtered_circuit,cmap="gray")