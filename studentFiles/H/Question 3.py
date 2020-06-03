# -*- coding: utf-8 -*-
"""
Created on Fri Oct 28 12:55:59 2016

@author: 15074248
"""
from matplotlib import pyplot as pt
import numpy as np
import cv2
img = cv2.imread("omega_circuit.bmp", 0)

[nrow,ncol] = img.shape
output_img = cv2.medianBlur(img,(5))
cv2.imshow("Edited circuit", output_img)
cv2.waitKey()