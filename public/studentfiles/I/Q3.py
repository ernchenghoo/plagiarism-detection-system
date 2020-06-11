
import numpy as np
import cv2
from matplotlib import pyplot as pt

q4omega = cv2.imread("q4omega.tif",1)
q4omega_gray = cv2.cvtColor(q4omega, cv2.COLOR_BGR2GRAY)
histq4omega = cv2.calcHist([q4omega_gray],[0],None,[256],[0,256])
[nrow,ncol] = q4omega_gray.shape
mask = np.zeros((nrow,ncol),dtype=np.uint8)
mask1 = np.zeros((nrow,ncol),dtype=np.uint8)
mask2 = np.zeros((nrow,ncol),dtype=np.uint8)
pt.figure()
#white is 255
for x in range (0,nrow):
    for y in range(0,ncol):
        if q4omega_gray[x,y] >= 150:
            mask1[x,y] =255
        else:
            mask1[x,y]=0
for x in range (0,nrow):
    for y in range(0,ncol):
        if q4omega_gray[x,y] <= 150:
            mask2[x,y] =255
        else:
            mask2[x,y]=0

mask1[:,0:380] = 0
mask2[:,370:1140] = 0
mask = mask1 + mask2

ex_q4omega = q4omega.copy()
ex_q4omega[:,:,0] = cv2.bitwise_and(q4omega[:,:,0],mask)
ex_q4omega[:,:,1] = cv2.bitwise_and(q4omega[:,:,1],mask)
ex_q4omega[:,:,2] = cv2.bitwise_and(q4omega[:,:,2],mask)
pt.figure()
pt.subplot(2,2,1)
pt.imshow(cv2.cvtColor(q4omega, cv2.COLOR_BGR2RGB))
pt.title("Original Color Image")
pt.subplot(2,2,2)
pt.imshow(cv2.cvtColor(ex_q4omega, cv2.COLOR_BGR2RGB))
pt.title("Expected Color Image")