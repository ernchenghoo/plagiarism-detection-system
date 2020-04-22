import numpy as np
import cv2
from matplotlib import pyplot as pt
#question 4
q4omega = cv2.imread("q4omega.tif",1)
q4omega_gray = cv2.cvtColor(q4omega, cv2.COLOR_BGR2GRAY)
histq4omega = cv2.calcHist([q4omega_gray],[0],None,[256],[0,256])

[nrow,ncol] = q4omega_gray.shape
mask = np.zeros((nrow,ncol),dtype=np.uint8)

threshold = 150
for x in range (0,nrow):
    for y in range(0,ncol):
        if q4omega_gray[x,y] >= threshold:
            mask[x,y] =255
        else:
            mask[x,y]=0

mask2 = q4omega_gray.copy()

for x in range (0,nrow):
    for y in range(0,ncol):
        if q4omega_gray[x,y] <= threshold:
            mask2[x,y] =255
        else:
            mask2[x,y]=0

mask3 = q4omega_gray.copy()

for x in range (0,nrow):
    for y in range(0,ncol):
        if q4omega_gray[x,y] <= threshold:
            mask2[x,y] =255
        else:
            mask2[x,y]=0

mask[:,0:380]=0
mask2[:,380:1140]=0
mask3= mask + mask2
ex_q4omega = q4omega.copy()
ex_q4omega[:,:,0] = cv2.bitwise_and(q4omega[:,:,0],mask3)
ex_q4omega[:,:,1] = cv2.bitwise_and(q4omega[:,:,1],mask3)
ex_q4omega[:,:,2] = cv2.bitwise_and(q4omega[:,:,2],mask3)

pt.figure()

pt.subplot(2,4,1)
pt.imshow(q4omega)
pt.title("Original Color Image")

pt.subplot(2,4,2)
pt.imshow(q4omega_gray, cmap="gray")
pt.title("Original Gray Image")

pt.subplot(2,4,3)
pt.imshow(mask, cmap="gray")
pt.title("Mask")

pt.subplot(2,4,4)
pt.title("Histogram")
pt.plot(histq4omega)
pt.xlim([0,256])

pt.subplot(2,4,5)
pt.title("2nd mask")
pt.imshow(mask2, cmap="gray")

pt.subplot(2,4,6)
pt.title("3rd mask")
pt.imshow(mask3,cmap="gray")

pt.subplot(2,4,7)
pt.title("Extracted Color Image ")
pt.imshow(cv2.cvtColor(ex_q4omega, cv2.COLOR_BGR2RGB))
